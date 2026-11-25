import dataclasses
import functools
import importlib
import importlib.machinery
import importlib.util
import json
import os
import posixpath
import re
import shlex
import sys
from pathlib import Path

from opscli.paths import (
    APP_SCRIPT, BOOTLOG_SCRIPT, CHECKS_CLI_PATH, CLI_PATH, DB_VERIFY_SCRIPT, DEFAULT_ENV, DEFAULT_SITE, ENV_PROFILES,
    FLEET_VERIFY_SCRIPT, LOGS_SCRIPT, METRICS_SCRIPT, NET_NAMES, NET_WORD_PATTERN, NODES_SCRIPT,
    OPS_CLI_NAME, OpsError,
    PYTHON_PATTERN, SITE_NAMES, SQL_SCRIPT, TERRAFORM_ROOT, TERRAFORM_RUNNER, TUNNEL_SCRIPT,
    USER_ONLY_SUBCOMMANDS, VAR_FILE_FLAGS, own_checkout_dir)


@dataclasses.dataclass(frozen=True)
class RefusedCommand:
    refused_text: str
    replacement_text: str


@dataclasses.dataclass(frozen=True)
class UserOnlyCommand:
    refused_text: str
    user_text: str


@functools.cache
def shell_helpers():
    checks_file = own_checkout_dir() / CHECKS_CLI_PATH
    if not checks_file.is_file():
        raise OpsError(f"{checks_file} is missing")
    module_loader = importlib.machinery.SourceFileLoader("checks_cli", str(checks_file))
    module_spec = importlib.util.spec_from_loader("checks_cli", module_loader)
    checks_module = importlib.util.module_from_spec(module_spec)
    module_loader.exec_module(checks_module)
    return checks_module


SHELL_NAMES = {"bash", "sh", "zsh", "dash"}
ACCESS_SCRIPT_NAMES = {Path(script_path).name: script_path for script_path in
                       (METRICS_SCRIPT, LOGS_SCRIPT, SQL_SCRIPT, TUNNEL_SCRIPT, DB_VERIFY_SCRIPT, NODES_SCRIPT,
                        BOOTLOG_SCRIPT, APP_SCRIPT)}
WRAPPED_NAMES = (*ACCESS_SCRIPT_NAMES, Path(FLEET_VERIFY_SCRIPT).name, Path(TERRAFORM_RUNNER).name)
SITE_COMMANDS = {Path(NODES_SCRIPT).name: "nodes", Path(APP_SCRIPT).name: "app"}


def ops_text(*command_words):
    return " ".join([CLI_PATH, *[shlex.quote(one_word) for one_word in command_words if one_word]])


def assigned_env(assignment_words):
    profile_names = [one_word.split("=", 1)[1] for one_word in assignment_words
                     if one_word.startswith("AWS_PROFILE=")]
    if not profile_names:
        return ""
    return next((env_key for env_key, env_profile in ENV_PROFILES.items() if env_profile == profile_names[-1]),
                None)


def env_words(env_name, default_env):
    return ("--env", env_name) if env_name and env_name != default_env else ()


def nth_or_empty(arg_words, word_index):
    return arg_words[word_index] if word_index < len(arg_words) else ""


def wrapped_kind(head_word):
    head_path = posixpath.normpath(head_word.replace("\\", "/"))
    head_name = os.path.basename(head_path)
    if head_name in ACCESS_SCRIPT_NAMES and (head_path == f"access/{head_name}"
                                             or head_path.endswith(f"/access/{head_name}")):
        return head_name
    if head_name in (Path(FLEET_VERIFY_SCRIPT).name, Path(TERRAFORM_RUNNER).name):
        return head_name
    return ""


def flag_pairs(option_names, option_values):
    return [pair_word for option_name, option_value in zip(option_names, option_values) if option_value
            for pair_word in (option_name, option_value)]


def access_replacement(kind_name, arg_words):
    if kind_name == "metrics-query.sh":
        query_words = [one_word for one_word in arg_words if one_word != "--names"]
        return ["metrics", nth_or_empty(query_words, 0) or "<promql>",
                *flag_pairs(("--since", "--step"), query_words[1:3]),
                *(["--names"] if "--names" in arg_words else [])]
    if kind_name == "logs-query.sh":
        if arg_words[:1] == ["--day"]:
            return ["logs", "--day", nth_or_empty(arg_words, 1) or "YYYY-MM-DD",
                    nth_or_empty(arg_words, 2) or "<logql>", *flag_pairs(("--limit",), arg_words[3:4])]
        return ["logs", nth_or_empty(arg_words, 0) or "<logql>", *flag_pairs(("--since", "--limit"), arg_words[1:3])]
    if kind_name == "rds-query.sh":
        if arg_words[:1] == ["-f"]:
            return ["sql", "-f", nth_or_empty(arg_words, 1) or "FILE", *flag_pairs(("--site",), arg_words[2:3])]
        return ["sql", nth_or_empty(arg_words, 0) or "<SELECT ...>", *flag_pairs(("--site",), arg_words[1:2])]
    if kind_name == "rds-tunnel.sh":
        return ["tunnel", nth_or_empty(arg_words, 0) or "start|stop|status", *flag_pairs(("--site",), arg_words[1:2])]
    if kind_name == Path(BOOTLOG_SCRIPT).name:
        return ["bootlog", nth_or_empty(arg_words, 0) or "<instance-id>", *flag_pairs(("--site",), arg_words[1:2])]
    if kind_name in SITE_COMMANDS:
        return [SITE_COMMANDS[kind_name], *flag_pairs(("--site",), arg_words[:1])]
    return ["verify", "db"]


def without_var_files(arg_words):
    kept_args = []
    skip_next = False
    for one_arg in arg_words:
        if skip_next:
            skip_next = False
        elif one_arg in VAR_FILE_FLAGS:
            skip_next = True
        elif one_arg.split("=", 1)[0] not in VAR_FILE_FLAGS:
            kept_args.append(one_arg)
    return kept_args


def runner_replacement(arg_words, env_name):
    subcommand_name = next((one_arg for one_arg in arg_words if not one_arg.startswith("-")), "")
    if subcommand_name != "plan":
        return None
    chdir_value = next((one_arg.split("=", 1)[1] for one_arg in arg_words if one_arg.startswith("-chdir=")), "")
    stack_match = re.search(rf"(?:^|/){TERRAFORM_ROOT}/([^/]+)/?$", posixpath.normpath(chdir_value or "."))
    stack_name = stack_match.group(1) if stack_match else "<stack>"
    checkout_dir = own_checkout_dir()
    terraform_module = importlib.import_module("opscli.terraform")
    default_env = DEFAULT_ENV
    is_moved = False
    if stack_name in terraform_module.plan_stacks(checkout_dir):
        one_stack = terraform_module.stack_info(checkout_dir, stack_name)
        if one_stack.is_unmapped:
            return None
        default_env = one_stack.default_env()
        is_moved = one_stack.is_moved
        if one_stack.per_account and not env_name:
            env_name = "<env>"
    rest_args = [one_arg for one_arg in arg_words if not one_arg.startswith(("-chdir=", "-out", "--out"))]
    rest_args.remove("plan")
    if is_moved:
        rest_args = without_var_files(rest_args)
    return [*env_words(env_name, default_env), "tf", "plan", stack_name, *(["--", *rest_args] if rest_args else [])]


def replacement_words(head_word, arg_words, env_name):
    kind_name = wrapped_kind(head_word)
    if not kind_name or env_name is None:
        return None
    if kind_name in ACCESS_SCRIPT_NAMES:
        return [*env_words(env_name, DEFAULT_ENV), *access_replacement(kind_name, arg_words)]
    if kind_name == Path(FLEET_VERIFY_SCRIPT).name:
        if "--kubeconfig" in arg_words:
            return None
        site_name = next((one_word for one_word in arg_words if one_word in SITE_NAMES), "")
        return [*env_words(env_name, DEFAULT_ENV), "verify", "fleet",
                *(("--site", site_name) if site_name and site_name != DEFAULT_SITE else ())]
    return runner_replacement(arg_words, env_name)


SOURCE_WORDS = {"source", "."}


def ops_command_words(arg_words):
    command_words = []
    word_index = 0
    while word_index < len(arg_words) and arg_words[word_index] != "--":
        one_word = arg_words[word_index]
        if one_word == "--env":
            word_index += 1
        elif not one_word.startswith("--env="):
            command_words.append(one_word)
        word_index += 1
    return command_words


def user_only_text(core_words, command_words):
    head_path = posixpath.normpath(core_words[0].replace("\\", "/"))
    if os.path.basename(head_path) == OPS_CLI_NAME and ops_command_words(core_words[1:])[:2] == ["tf", "apply"]:
        return f"! {shlex.join(command_words)}"
    if wrapped_kind(core_words[0]) != Path(TERRAFORM_RUNNER).name:
        return ""
    subcommand_name = next((one_arg for one_arg in core_words[1:] if not one_arg.startswith("-")), "")
    if subcommand_name not in USER_ONLY_SUBCOMMANDS:
        return ""
    return f"! {CLI_PATH} tf apply <plan file>, for a plan file {CLI_PATH} tf plan <stack> cleared"


def shell_script_words(core_words):
    head_name = os.path.basename(core_words[0])
    if head_name not in SHELL_NAMES and not PYTHON_PATTERN.match(head_name):
        return None
    rest_words = list(core_words[1:])
    while rest_words and rest_words[0].startswith("-"):
        rest_words = rest_words[1:]
    return rest_words or None


def classify_words(command_words, depth_left):
    checks_module = shell_helpers()
    found_refusals = []
    for one_word in command_words:
        for body_text in checks_module.substitution_bodies(one_word):
            found_refusals += classify_command(body_text, depth_left - 1)
    core_words, _ = checks_module.unwrap_command(command_words)
    if not core_words:
        return found_refusals
    script_texts, word_lists = checks_module.nested_scripts(core_words)
    for script_text in script_texts:
        found_refusals += classify_command(script_text, depth_left - 1)
    for inner_words in word_lists:
        found_refusals += classify_words(inner_words, depth_left - 1)
    if script_texts or word_lists:
        return found_refusals
    assignment_words = command_words[:len(command_words) - len(core_words)]
    script_words = shell_script_words(core_words)
    if script_words:
        core_words = script_words
    elif core_words[0] in SOURCE_WORDS and len(core_words) > 1:
        core_words = core_words[1:]
    user_text = user_only_text(core_words, command_words)
    if user_text:
        found_refusals.append(UserOnlyCommand(shlex.join(command_words), user_text))
        return found_refusals
    ops_words = replacement_words(core_words[0], core_words[1:], assigned_env(assignment_words))
    if ops_words is not None:
        found_refusals.append(RefusedCommand(shlex.join(command_words), ops_text(*ops_words)))
    return found_refusals


def shell_heredoc_bodies(checks_module, command_text):
    found_bodies = []
    for heredoc_match in checks_module.HEREDOC_PATTERN.finditer(command_text):
        line_start = command_text.rfind("\n", 0, heredoc_match.start()) + 1
        feeding_line = command_text[line_start:heredoc_match.start()] + " " + heredoc_match.group(3)
        try:
            feeding_tokens = checks_module.shell_tokens(feeding_line)
        except ValueError:
            continue
        feeding_heads = [checks_module.unwrap_command(command_words)[0][:1]
                         for command_words in checks_module.simple_commands(feeding_tokens)]
        if any(head_words and os.path.basename(head_words[0]) in SHELL_NAMES for head_words in feeding_heads):
            found_bodies.append("\n".join(heredoc_match.group(0).splitlines()[1:-1]))
    return found_bodies


def classify_command(command_text, depth_left=6):
    if depth_left <= 0:
        return []
    checks_module = shell_helpers()
    found_refusals = []
    for body_text in shell_heredoc_bodies(checks_module, command_text):
        found_refusals += classify_command(body_text, depth_left - 1)
    try:
        token_list = checks_module.shell_tokens(checks_module.strip_heredocs(command_text))
    except ValueError:
        return found_refusals
    for command_words in checks_module.simple_commands(token_list):
        found_refusals += classify_words(command_words, depth_left)
    return found_refusals


def refusal_message(found_refusals):
    message_lines = []
    user_refusals = [one_refusal for one_refusal in found_refusals if isinstance(one_refusal, UserOnlyCommand)]
    wrapped_refusals = [one_refusal for one_refusal in found_refusals if isinstance(one_refusal, RefusedCommand)]
    if user_refusals:
        message_lines += ["ops: an apply or destroy is refused from the Bash tool; only the user runs it, typed",
                          "outside the tool with a leading !. There is no command for an agent to run instead:"]
        for one_refusal in user_refusals:
            message_lines.append(f"  refused    {one_refusal.refused_text}")
            message_lines.append(f"  user runs  {one_refusal.user_text}")
    if wrapped_refusals:
        message_lines += [
            "ops: a direct call to a production script the ops CLI wraps is refused (ADR 512); run the",
            "ops command, which selects the AWS profile (--env, default prod), checks the SSO session",
            "and writes the dated log:"]
        for one_refusal in wrapped_refusals:
            message_lines.append(f"  refused  {one_refusal.refused_text}")
            message_lines.append(f"  run      {one_refusal.replacement_text}")
    message_lines.append(f"Every command: {CLI_PATH} list")
    return "\n".join(message_lines)


def hook_main(stdin_text):
    try:
        hook_input = json.loads(stdin_text)
    except json.JSONDecodeError:
        return 0
    if hook_input.get("tool_name", "Bash") != "Bash":
        return 0
    command_text = (hook_input.get("tool_input") or {}).get("command") or ""
    refusal_text = hook_refusal(command_text)
    if not refusal_text:
        return 0
    print(refusal_text, file=sys.stderr)
    return 2


@dataclasses.dataclass(frozen=True)
class TextNetMatch:
    named_path: str
    named_word: str


def text_net_match(command_text):
    named_path = next((one_name for one_name in NET_NAMES if one_name in command_text), "")
    word_match = NET_WORD_PATTERN.search(command_text)
    if not named_path or not word_match:
        return None
    return TextNetMatch(named_path, word_match.group(1))


def net_message(net_match):
    return "\n".join([
        f"ops: this command names {net_match.named_path} together with the word '{net_match.named_word}', and the "
        f"hook cannot show it harmless,",
        "so it is refused from the Bash tool; only the user applies, typed outside the tool with a leading !:",
        f"  user runs  ! {CLI_PATH} tf apply <plan file>, for a plan file {CLI_PATH} tf plan <stack> cleared",
        "A command that only mentions them passes with the word in a file: rg -f <pattern file>, "
        "git commit -F <message file>.",
        f"Every command: {CLI_PATH} list"])


def hook_refusal(command_text):
    net_match = text_net_match(command_text)
    try:
        found_refusals = classify_command(command_text)
    except Exception as parser_error:
        if net_match:
            return net_message(net_match)
        if not any(script_name in command_text for script_name in WRAPPED_NAMES):
            return ""
        return (f"ops hook: the shell parser borrowed from {CHECKS_CLI_PATH} failed ({parser_error!r}), so a "
                f"command naming a script ops wraps is refused; restore the parser with: git checkout -- "
                f"{CHECKS_CLI_PATH}")
    if any(isinstance(one_refusal, UserOnlyCommand) for one_refusal in found_refusals):
        return refusal_message(found_refusals)
    if net_match:
        return net_message(net_match)
    return refusal_message(found_refusals) if found_refusals else ""
