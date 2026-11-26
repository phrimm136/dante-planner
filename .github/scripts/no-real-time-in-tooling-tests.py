#!/usr/bin/env python3
import argparse
import io
import re
import sys
import tokenize
from pathlib import Path

TEST_GLOBS = ("scripts/ops/tests", "scripts/ops/provision/tests", ".claude/skills/*/tests", ".claude/hooks/tests",
              ".githooks/tests", ".github/scripts/tests")
SKIPPED_PARTS = {"fixtures", "__pycache__"}
FAKE_PREFIX = "FAKE_"
SHEBANG = "#!"
STRING_OPENING = re.compile(r"[rRbBuUfF]*(?:\"\"\"|'''|\"|')")
FSTRING_START = getattr(tokenize, "FSTRING_START", -1)
FSTRING_MIDDLE = getattr(tokenize, "FSTRING_MIDDLE", -1)
FSTRING_END = getattr(tokenize, "FSTRING_END", -1)

PYTHON_CLOCKS = {"time": {"sleep", "monotonic", "monotonic_ns", "time", "time_ns", "perf_counter", "perf_counter_ns"},
                 "datetime": {"now", "utcnow", "today"}, "asyncio": {"sleep"}}
DURATION_WORDS = {"elapsed", "duration", "started", "took", "began"}
SHELL_SLEEP = re.compile(r"(?<![\w./$-])sleep[ \t]+(?:[\d.]+|\"?\$)")
SHELL_DATE = re.compile(r"(?<![\w./$-])date((?:[ \t]+(?:'[^'\n]*'|\"[^\"\n]*\"|[^\s;|&)`'\"])+)*)")
DATE_ARG = re.compile(r"'[^'\n]*'|\"[^\"\n]*\"|[^\s'\"]+")
SHELL_SECONDS = re.compile(r"\$\{?SECONDS\b|\bSECONDS=")
SHELL_PATH = re.compile(r"(?<![\w$])PATH=(\"[^\"\n]*\"|'[^'\n]*'|\S*)")
INHERITED_PATH = re.compile(r"os\.environ(?:\[|\.get\()[\"']PATH[\"']|\$\{{0,2}PATH\b|TOOLING_SLEEP_GUARD_DIR")
PATH_KEYS = ('"PATH"', "'PATH'")
OPENING_BRACKETS = ("(", "[", "{")
CLOSING_BRACKETS = (")", "]", "}")
TIME_ADVICE = ("; script time as an input through a fake (clock file, fake sleep, date or timeout) and assert on the "
               "ending and the calls")
RULE_TEXT = {
    "clock-call": "reads or waits on the host clock" + TIME_ADVICE,
    "duration-assert": "asserts on a measured duration" + TIME_ADVICE,
    "shell-sleep": "a shell sleep spends real time" + TIME_ADVICE,
    "shell-clock": "a shell date or SECONDS reads the host clock" + TIME_ADVICE,
    "path-without-guard": "builds a PATH from neither the inherited PATH nor TOOLING_SLEEP_GUARD_DIR, which drops "
                          "the runner's real-sleep guard; put the fake directory before the inherited PATH, or name "
                          "TOOLING_SLEEP_GUARD_DIR right after it",
}


def date_reads_clock(args_text, before_text):
    date_args = [one_arg.strip("'\"") for one_arg in DATE_ARG.findall(args_text)]
    if not date_args:
        return before_text.endswith(("$(", "`"))
    if not date_args[0].startswith(("-", "+")):
        return False
    for arg_index, one_arg in enumerate(date_args):
        if one_arg == "-r" or one_arg.startswith("--reference"):
            return False
        if one_arg in ("-d", "--date") and arg_index + 1 < len(date_args):
            if date_args[arg_index + 1].startswith("@"):
                return False
        if one_arg.startswith("--date=@"):
            return False
    return True


def shell_hits(shell_text, line_start, escaped_breaks=False):
    found_hits = []
    for line_offset, source_line in enumerate(shell_text.split("\n")):
        line_number = line_start + line_offset
        for one_line in source_line.split("\\n") if escaped_breaks else [source_line]:
            if one_line.lstrip().startswith("#"):
                continue
            if SHELL_SLEEP.search(one_line):
                found_hits.append((line_number, "shell-sleep"))
            if SHELL_SECONDS.search(one_line) or any(
                    date_reads_clock(date_match.group(1), one_line[:date_match.start()])
                    for date_match in SHELL_DATE.finditer(one_line)):
                found_hits.append((line_number, "shell-clock"))
            if any(not INHERITED_PATH.search(path_match.group(1)) for path_match in SHELL_PATH.finditer(one_line)):
                found_hits.append((line_number, "path-without-guard"))
    return found_hits


def path_value_start(line_tokens, token_index):
    following_texts = [later_token.string for later_token in line_tokens[token_index + 1:token_index + 3]]
    one_token = line_tokens[token_index]
    if one_token.type == tokenize.STRING and one_token.string in PATH_KEYS:
        if following_texts[:1] == [":"]:
            return token_index + 2
        if following_texts == ["]", "="]:
            return token_index + 3
    if (one_token.type == tokenize.NAME and one_token.string == "PATH" and following_texts[:1] == ["="]
            and token_index and line_tokens[token_index - 1].string in ("(", ",")):
        return token_index + 2
    return None


def path_value_text(value_tokens):
    bracket_depth = 0
    value_texts = []
    for one_token in value_tokens:
        if one_token.type in (tokenize.NEWLINE, tokenize.ENDMARKER):
            break
        if not bracket_depth and (one_token.string == "," or one_token.string in CLOSING_BRACKETS):
            break
        if one_token.string in OPENING_BRACKETS:
            bracket_depth += 1
        elif one_token.string in CLOSING_BRACKETS:
            bracket_depth -= 1
        value_texts.append(one_token.string)
    return "".join(value_texts)


def logical_lines(token_list):
    current_line = []
    for one_token in token_list:
        current_line.append(one_token)
        if one_token.type in (tokenize.NEWLINE, tokenize.ENDMARKER):
            yield current_line
            current_line = []
    if current_line:
        yield current_line


def string_literals(line_tokens):
    fstring_depth = 0
    fstring_pieces = []
    for one_token in line_tokens:
        if one_token.type == tokenize.STRING:
            yield STRING_OPENING.sub("", one_token.string, count=1), [one_token]
        elif one_token.type == FSTRING_START:
            fstring_pieces = fstring_pieces if fstring_depth else []
            fstring_depth += 1
        elif one_token.type == FSTRING_MIDDLE:
            fstring_pieces.append(one_token)
        elif one_token.type == FSTRING_END:
            fstring_depth -= 1
            if not fstring_depth and fstring_pieces:
                yield fstring_pieces[0].string, fstring_pieces


def assigns_a_fake(line_tokens):
    code_tokens = [one_token for one_token in line_tokens if one_token.type not in (tokenize.INDENT, tokenize.DEDENT)]
    return (len(code_tokens) >= 2 and code_tokens[0].start[1] == 0 and code_tokens[0].type == tokenize.NAME
            and code_tokens[0].string.startswith(FAKE_PREFIX) and code_tokens[1].string == "=")


def python_hits(source_text):
    found_hits = []
    token_list = [one_token for one_token in tokenize.generate_tokens(io.StringIO(source_text).readline)
                  if one_token.type not in (tokenize.COMMENT, tokenize.NL)]
    for line_tokens in logical_lines(token_list):
        line_names = [one_token.string for one_token in line_tokens if one_token.type == tokenize.NAME]
        fake_line = assigns_a_fake(line_tokens)
        for literal_text, literal_tokens in string_literals(line_tokens):
            if fake_line or literal_text.startswith(SHEBANG):
                found_hits.extend(shell_hits("".join(one_token.string for one_token in literal_tokens),
                                             literal_tokens[0].start[0], escaped_breaks=True))
        for token_index, one_token in enumerate(line_tokens):
            token_window = [later_token.string for later_token in line_tokens[token_index:token_index + 4]]
            if (len(token_window) == 4 and token_window[1] == "." and token_window[3] == "("
                    and token_window[2] in PYTHON_CLOCKS.get(token_window[0], ())):
                found_hits.append((one_token.start[0], "clock-call"))
            value_start = path_value_start(line_tokens, token_index)
            if value_start is not None and not INHERITED_PATH.search(path_value_text(line_tokens[value_start:])):
                found_hits.append((one_token.start[0], "path-without-guard"))
        if len(line_names) >= 3 and line_names[0] == "from" and line_names[1] in PYTHON_CLOCKS and line_names[2] == "import":
            if set(line_names[3:]) & PYTHON_CLOCKS[line_names[1]]:
                found_hits.append((line_tokens[0].start[0], "clock-call"))
        line_asserts = "assert" in line_names or any(one_name.startswith("assert") for one_name in line_names)
        if line_asserts and any(set(one_name.lower().split("_")) & DURATION_WORDS for one_name in line_names):
            found_hits.append((line_tokens[0].start[0], "duration-assert"))
    return found_hits


def test_files(repo_root):
    for one_glob in TEST_GLOBS:
        for test_dir in sorted(repo_root.glob(one_glob)):
            for file_path in sorted(test_dir.rglob("*")):
                relative_path = file_path.relative_to(repo_root)
                if (file_path.is_file() and file_path.suffix in (".py", ".sh")
                        and not SKIPPED_PARTS & set(relative_path.parts)):
                    yield relative_path


def file_hits(repo_root, relative_path):
    source_text = (repo_root / relative_path).read_text()
    if relative_path.suffix == ".py":
        return python_hits(source_text)
    return shell_hits(source_text, 1)


def check_tree(repo_root):
    return [f"{relative_path.as_posix()}:{line_number}: {rule_name}: {RULE_TEXT[rule_name]}"
            for relative_path in test_files(repo_root)
            for line_number, rule_name in file_hits(repo_root, relative_path)]


def main():
    arg_parser = argparse.ArgumentParser(description="Fail on real clock reads, real waits, duration assertions "
                                                     "and PATHs that drop the sleep guard in the tooling tests.")
    arg_parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    found_problems = check_tree(arg_parser.parse_args().root)
    for one_problem in found_problems:
        print(one_problem)
    if found_problems:
        print(f"no-real-time-in-tooling-tests: {len(found_problems)} problem(s)")
        return 1
    print("no-real-time-in-tooling-tests: no real time in the tooling tests")
    return 0


if __name__ == "__main__":
    sys.exit(main())
