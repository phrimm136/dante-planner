#!/usr/bin/env python3
"""Measure review records against what landed on dev.

Usage: review-measures.py [--repo PATH] [--reviews DIR] [--since SHA] [--self-test]
  scripts/ops/review-measures.py
  scripts/ops/review-measures.py --self-test

Reads every *.md record under the reviews directory and the dev reflog, and prints finding
counts, verdict counts, landed commits whose patch-id is in no record, and files that two fix
commits touched within 14 days. The reflog walk starts at the first dev entry containing --since,
or by default the commit that added .githooks/reference-transaction.
"""

import argparse
import os
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

PATCH_ID_BULLET = re.compile(r"^\s*-\s+([0-9a-f]{40})(?:\s|$)")
SECTION_KEY = re.compile(r"^([A-Za-z][\w-]*):")
FINDING_ROW = re.compile(r"^\s*-\s+file:")
VERDICT_LINE = re.compile(r"^\s*verdict:\s*(\S*)")
VERDICT_KINDS = ("fix", "skip", "confirm")
FIX_SUBJECT = re.compile(r"^fix(\(|:)")
REFIX_WINDOW_SECONDS = 14 * 24 * 60 * 60
FIELD_SEP = "\x1f"
COMMIT_SEP = "\x00"
LANDING_HOOK = ".githooks/reference-transaction"


def parse_record(record_text):
    patch_ids = set()
    verdict_list = []
    current_section = None
    for text_line in record_text.splitlines():
        key_match = SECTION_KEY.match(text_line)
        if key_match:
            current_section = key_match.group(1)
            continue
        if current_section in ("patch-ids", "delta"):
            id_match = PATCH_ID_BULLET.match(text_line)
            if id_match:
                patch_ids.add(id_match.group(1))
        elif current_section == "findings":
            if FINDING_ROW.match(text_line):
                verdict_list.append("none")
                continue
            verdict_match = VERDICT_LINE.match(text_line)
            if verdict_match and verdict_list and verdict_match.group(1) in VERDICT_KINDS:
                verdict_list[-1] = verdict_match.group(1)
    return {"patch_ids": patch_ids, "verdicts": verdict_list}


def summarize_records(record_list):
    verdict_counts = {verdict_kind: 0 for verdict_kind in VERDICT_KINDS + ("none",)}
    for parsed_record in record_list:
        for verdict_kind in parsed_record["verdicts"]:
            verdict_counts[verdict_kind] += 1
    finding_total = sum(len(parsed_record["verdicts"]) for parsed_record in record_list)
    return {"units": len(record_list), "findings": finding_total, "verdicts": verdict_counts}


def reflog_entries(reflog_text):
    entry_shas = [text_line.split()[0] for text_line in reflog_text.splitlines() if text_line.strip()]
    entry_shas.reverse()
    return entry_shas


def landing_ranges(entry_shas, floor_index):
    return list(zip(entry_shas[floor_index:], entry_shas[floor_index + 1:]))


def parse_commit_log(log_text):
    commit_list = []
    for commit_chunk in log_text.split(COMMIT_SEP):
        if not commit_chunk.strip():
            continue
        header_line, _, file_block = commit_chunk.partition("\n")
        full_sha, short_sha, commit_time, commit_subject = header_line.split(FIELD_SEP, 3)
        commit_list.append({
            "sha": full_sha,
            "short": short_sha,
            "time": int(commit_time),
            "subject": commit_subject,
            "files": [file_line for file_line in file_block.splitlines() if file_line.strip()],
        })
    return commit_list


def parse_patch_ids(patch_output):
    id_map = {}
    for text_line in patch_output.splitlines():
        line_parts = text_line.split()
        if len(line_parts) == 2:
            id_map[line_parts[1]] = line_parts[0]
    return id_map


def find_unreviewed(commit_list, id_map, reviewed_ids, released_shas):
    return [commit_entry for commit_entry in commit_list
            if commit_entry["sha"] in id_map
            and commit_entry["sha"] not in released_shas
            and id_map[commit_entry["sha"]] not in reviewed_ids]


def find_refixes(commit_list):
    touches_by_path = {}
    for commit_entry in commit_list:
        if FIX_SUBJECT.match(commit_entry["subject"]):
            for file_path in commit_entry["files"]:
                touches_by_path.setdefault(file_path, []).append(commit_entry)
    refix_list = []
    for file_path in sorted(touches_by_path):
        path_touches = sorted(touches_by_path[file_path], key=lambda touch_entry: touch_entry["time"])
        for earlier_fix, later_fix in zip(path_touches, path_touches[1:]):
            if later_fix["time"] - earlier_fix["time"] <= REFIX_WINDOW_SECONDS:
                refix_list.append((file_path, earlier_fix["short"], later_fix["short"]))
    return refix_list


def render_report(record_summary, unreviewed_list, refix_list):
    unit_count = record_summary["units"]
    finding_count = record_summary["findings"]
    per_unit = finding_count / unit_count if unit_count else 0.0
    verdict_counts = record_summary["verdicts"]
    output_lines = [
        f"units: {unit_count}",
        f"findings: {finding_count}",
        f"findings/unit: {per_unit:.2f}",
        "verdicts: " + " ".join(f"{verdict_kind}: {verdict_counts[verdict_kind]}"
                                for verdict_kind in VERDICT_KINDS + ("none",)),
    ]
    output_lines += [f"unreviewed: {commit_entry['short']} {commit_entry['subject']}"
                     for commit_entry in unreviewed_list]
    output_lines += [f"refix-within-14d: {file_path} ({older_sha} -> {newer_sha})"
                     for file_path, older_sha, newer_sha in refix_list]
    return "\n".join(output_lines) + "\n"


def run_git(repo_path, git_args, stdin_text=None):
    return subprocess.run(["git", "-C", str(repo_path), *git_args], input=stdin_text,
                          capture_output=True, text=True, check=True).stdout


def released_commits(repo_path):
    ref_check = subprocess.run(["git", "-C", str(repo_path), "rev-parse", "--verify", "--quiet",
                                "refs/remotes/origin/main"], capture_output=True)
    if ref_check.returncode != 0:
        return set()
    return set(run_git(repo_path, ["rev-list", "refs/remotes/origin/main"]).split())


def gate_floor(repo_path, since_sha):
    if since_sha:
        return since_sha
    adding_shas = run_git(repo_path, ["log", "dev", "--diff-filter=A", "--format=%H", "--", LANDING_HOOK]).split()
    return adding_shas[-1] if adding_shas else None


def floor_index(repo_path, entry_shas, floor_sha):
    if floor_sha is None:
        return 0
    for entry_index, entry_sha in enumerate(entry_shas):
        if subprocess.run(["git", "-C", str(repo_path), "merge-base", "--is-ancestor", floor_sha, entry_sha],
                          capture_output=True).returncode == 0:
            return entry_index
    return len(entry_shas)


def read_records(reviews_dir):
    if not reviews_dir.is_dir():
        return []
    return [parse_record(record_path.read_text()) for record_path in sorted(reviews_dir.glob("*.md"))]


def landed_commits(repo_path, since_sha):
    entry_shas = reflog_entries(run_git(repo_path, ["reflog", "show", "dev", "--format=%H %gs"]))
    start_index = floor_index(repo_path, entry_shas, gate_floor(repo_path, since_sha))
    commit_list = []
    seen_shas = set()
    log_format = "--format=%x00" + "%x1f".join(["%H", "%h", "%ct", "%s"])
    for base_sha, tip_sha in landing_ranges(entry_shas, start_index):
        range_log = run_git(repo_path, ["log", "--reverse", "--no-merges", log_format, "--name-only",
                                        f"{base_sha}..{tip_sha}"])
        for commit_entry in parse_commit_log(range_log):
            if commit_entry["sha"] not in seen_shas:
                seen_shas.add(commit_entry["sha"])
                commit_list.append(commit_entry)
    return commit_list


def measure_repo(repo_path, reviews_dir, since_sha):
    record_list = read_records(reviews_dir)
    reviewed_ids = set().union(*(parsed_record["patch_ids"] for parsed_record in record_list))
    commit_list = landed_commits(repo_path, since_sha)
    sha_input = "".join(commit_entry["sha"] + "\n" for commit_entry in commit_list)
    diff_text = run_git(repo_path, ["diff-tree", "--stdin", "-p"], sha_input) if commit_list else ""
    id_map = parse_patch_ids(run_git(repo_path, ["patch-id", "--stable"], diff_text)) if diff_text else {}
    return render_report(summarize_records(record_list),
                         find_unreviewed(commit_list, id_map, reviewed_ids, released_commits(repo_path)),
                         find_refixes(commit_list))


FIXTURE_ENV = {
    "GIT_CONFIG_GLOBAL": os.devnull,
    "GIT_CONFIG_NOSYSTEM": "1",
    "GIT_AUTHOR_NAME": "fixture",
    "GIT_AUTHOR_EMAIL": "fixture@example.invalid",
    "GIT_COMMITTER_NAME": "fixture",
    "GIT_COMMITTER_EMAIL": "fixture@example.invalid",
}
DAY_SECONDS = 24 * 60 * 60
FIXTURE_EPOCH = 1_750_000_000

THREE_FINDINGS = """branch: feat/three
patch-ids:
  - {first_id} feat: one
findings:
- file: a.ts  line: 1  rule: 1
  claim: c
  proof: p
  fix: f
  verdict: fix
- file: b.ts  line: 2  rule: 2
  claim: c
  proof: p
  fix: f
  verdict: skip not reachable
- file: c.ts  line: 3  rule: 3
  claim: c
  proof: p
  fix: f
"""

ZERO_FINDINGS = """branch: feat/zero
patch-ids:
  - {first_id} feat: two
delta:
  - {second_id} fix: two
findings:
"""

NO_FINDINGS_BLOCK = """branch: feat/bare
patch-ids:
  - {first_id} feat: bare
"""


class FixtureRepo:
    def __init__(self, root_dir):
        self.repo_path = Path(root_dir) / "repo"
        self.reviews_dir = Path(root_dir) / "reviews"
        self.repo_path.mkdir()
        self.clock_seconds = FIXTURE_EPOCH
        self.git_cmd("init", "-q", "-b", "dev")
        self.commit_file("seed.txt", "chore: seed")

    def git_cmd(self, *git_args, stdin_text=None):
        commit_date = f"@{self.clock_seconds} +0000"
        fixture_env = {**os.environ, **FIXTURE_ENV,
                       "GIT_AUTHOR_DATE": commit_date, "GIT_COMMITTER_DATE": commit_date}
        return subprocess.run(["git", "-C", str(self.repo_path), *git_args], input=stdin_text,
                              capture_output=True, text=True, check=True, env=fixture_env).stdout

    def commit_file(self, file_name, commit_subject, day_offset=0):
        self.clock_seconds += day_offset * DAY_SECONDS + 1
        target_file = self.repo_path / file_name
        target_file.parent.mkdir(parents=True, exist_ok=True)
        prior_text = target_file.read_text() if target_file.exists() else ""
        target_file.write_text(prior_text + commit_subject + "\n")
        self.git_cmd("add", file_name)
        self.git_cmd("commit", "-q", "-m", commit_subject)
        return self.git_cmd("rev-parse", "HEAD").strip()

    def patch_id(self, commit_sha):
        diff_text = self.git_cmd("diff-tree", "-p", commit_sha)
        return self.git_cmd("patch-id", "--stable", stdin_text=diff_text).split()[0]

    def short_sha(self, commit_sha):
        return self.git_cmd("rev-parse", "--short", commit_sha).strip()

    def write_record(self, record_name, record_text):
        self.reviews_dir.mkdir(exist_ok=True)
        (self.reviews_dir / record_name).write_text(record_text)

    def run_script(self, reviews_dir=None, extra_args=()):
        return subprocess.run([sys.executable, str(Path(__file__).resolve()), "--repo", str(self.repo_path),
                               "--reviews", str(reviews_dir or self.reviews_dir), *extra_args],
                              capture_output=True, text=True, env={**os.environ, **FIXTURE_ENV})


class ReviewMeasuresTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.fixture = FixtureRepo(self.temp_dir.name)

    def tearDown(self):
        self.temp_dir.cleanup()

    def land_reviewed(self, file_name, commit_subject, day_offset=0):
        self.fixture.git_cmd("checkout", "-q", "-b", f"feat/{file_name}")
        commit_sha = self.fixture.commit_file(file_name, commit_subject, day_offset)
        self.fixture.git_cmd("checkout", "-q", "dev")
        self.fixture.git_cmd("merge", "-q", "--ff-only", f"feat/{file_name}")
        return commit_sha

    def test_findings_per_unit(self):
        first_sha = self.land_reviewed("one.txt", "feat: one")
        second_sha = self.land_reviewed("two.txt", "feat: two")
        first_id = self.fixture.patch_id(first_sha)
        second_id = self.fixture.patch_id(second_sha)
        self.fixture.write_record("feat__three.md", THREE_FINDINGS.format(first_id=first_id))
        self.fixture.write_record("feat__zero.md", ZERO_FINDINGS.format(first_id=first_id, second_id=second_id))
        script_run = self.fixture.run_script()
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout, "units: 2\nfindings: 3\nfindings/unit: 1.50\n"
                                            "verdicts: fix: 1 skip: 1 confirm: 0 none: 1\n")

    def test_unreviewed_commit_listed(self):
        reviewed_sha = self.land_reviewed("one.txt", "feat: one")
        self.fixture.write_record("feat__one.md", NO_FINDINGS_BLOCK.format(first_id=self.fixture.patch_id(reviewed_sha)))
        stray_sha = self.fixture.commit_file("stray.txt", "feat: stray")
        script_run = self.fixture.run_script()
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout, "units: 1\nfindings: 0\nfindings/unit: 0.00\n"
                                            "verdicts: fix: 0 skip: 0 confirm: 0 none: 0\n"
                                            f"unreviewed: {self.fixture.short_sha(stray_sha)} feat: stray\n")

    def test_refix_three_days_apart(self):
        first_sha = self.land_reviewed("shared.txt", "fix: first")
        self.fixture.git_cmd("branch", "-q", "-D", "feat/shared.txt")
        second_sha = self.land_reviewed("shared.txt", "fix(backend): second", day_offset=3)
        self.fixture.write_record("fixes.md", ZERO_FINDINGS.format(first_id=self.fixture.patch_id(first_sha),
                                                                   second_id=self.fixture.patch_id(second_sha)))
        script_run = self.fixture.run_script()
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout.splitlines()[4:],
                         [f"refix-within-14d: shared.txt ({self.fixture.short_sha(first_sha)} -> "
                          f"{self.fixture.short_sha(second_sha)})"])

    def test_fixup_and_fixture_are_not_fixes(self):
        first_sha = self.land_reviewed("shared.txt", "fixup! x")
        self.fixture.git_cmd("branch", "-q", "-D", "feat/shared.txt")
        second_sha = self.land_reviewed("shared.txt", "fixture: y", day_offset=2)
        self.fixture.write_record("fixes.md", ZERO_FINDINGS.format(first_id=self.fixture.patch_id(first_sha),
                                                                   second_id=self.fixture.patch_id(second_sha)))
        script_run = self.fixture.run_script()
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout.splitlines()[4:], [])

    def test_refix_twenty_days_apart(self):
        first_sha = self.land_reviewed("shared.txt", "fix: first")
        self.fixture.git_cmd("branch", "-q", "-D", "feat/shared.txt")
        second_sha = self.land_reviewed("shared.txt", "fix: second", day_offset=20)
        self.fixture.write_record("fixes.md", ZERO_FINDINGS.format(first_id=self.fixture.patch_id(first_sha),
                                                                   second_id=self.fixture.patch_id(second_sha)))
        script_run = self.fixture.run_script()
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout.splitlines()[4:], [])

    def test_empty_reviews_dir(self):
        self.fixture.reviews_dir.mkdir()
        script_run = self.fixture.run_script()
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout.splitlines()[0], "units: 0")

    def test_missing_reviews_dir(self):
        script_run = self.fixture.run_script(Path(self.temp_dir.name) / "absent")
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout.splitlines()[0], "units: 0")

    def test_record_without_findings_block(self):
        reviewed_sha = self.land_reviewed("one.txt", "feat: one")
        self.fixture.write_record("feat__one.md", NO_FINDINGS_BLOCK.format(first_id=self.fixture.patch_id(reviewed_sha)))
        script_run = self.fixture.run_script()
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout.splitlines()[:2], ["units: 1", "findings: 0"])

    def test_rollback_lists_nothing(self):
        first_sha = self.land_reviewed("one.txt", "feat: one")
        second_sha = self.land_reviewed("two.txt", "feat: two")
        self.fixture.write_record("pair.md", ZERO_FINDINGS.format(first_id=self.fixture.patch_id(first_sha),
                                                                  second_id=self.fixture.patch_id(second_sha)))
        self.fixture.git_cmd("reset", "-q", "--hard", "HEAD~2")
        reflog_subjects = self.fixture.git_cmd("reflog", "show", "dev", "--format=%gs")
        self.assertTrue(reflog_subjects.startswith("reset: moving to HEAD~2"), reflog_subjects)
        script_run = self.fixture.run_script()
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout.splitlines()[4:], [])

    def test_forward_reset_lists_descendant(self):
        self.fixture.git_cmd("checkout", "-q", "-b", "feat/ahead")
        ahead_sha = self.fixture.commit_file("ahead.txt", "feat: ahead")
        self.fixture.git_cmd("checkout", "-q", "dev")
        self.fixture.git_cmd("reset", "-q", "--hard", "feat/ahead")
        reflog_subjects = self.fixture.git_cmd("reflog", "show", "dev", "--format=%gs")
        self.assertTrue(reflog_subjects.startswith("reset: moving to feat/ahead"), reflog_subjects)
        script_run = self.fixture.run_script()
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout.splitlines()[4:],
                         [f"unreviewed: {self.fixture.short_sha(ahead_sha)} feat: ahead"])

    def test_empty_commit_not_listed(self):
        self.fixture.git_cmd("commit", "-q", "--allow-empty", "-m", "chore: empty")
        script_run = self.fixture.run_script()
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout.splitlines()[4:], [])

    def test_origin_main_ancestors_not_listed(self):
        released_sha = self.fixture.commit_file("released.txt", "feat: released")
        self.fixture.git_cmd("update-ref", "refs/remotes/origin/main", released_sha)
        pending_sha = self.fixture.commit_file("pending.txt", "feat: pending")
        script_run = self.fixture.run_script()
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout.splitlines()[4:],
                         [f"unreviewed: {self.fixture.short_sha(pending_sha)} feat: pending"])

    def test_commits_before_floor_not_listed(self):
        self.fixture.commit_file("early.txt", "feat: early")
        self.fixture.commit_file(LANDING_HOOK, "feat: landing hook")
        late_sha = self.fixture.commit_file("late.txt", "feat: late")
        script_run = self.fixture.run_script()
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout.splitlines()[4:],
                         [f"unreviewed: {self.fixture.short_sha(late_sha)} feat: late"])

    def test_since_moves_floor(self):
        self.fixture.commit_file(LANDING_HOOK, "feat: landing hook")
        middle_sha = self.fixture.commit_file("middle.txt", "feat: middle")
        late_sha = self.fixture.commit_file("late.txt", "feat: late")
        script_run = self.fixture.run_script(extra_args=("--since", middle_sha))
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout.splitlines()[4:],
                         [f"unreviewed: {self.fixture.short_sha(late_sha)} feat: late"])

    def test_no_floor_walks_everything(self):
        early_sha = self.fixture.commit_file("early.txt", "feat: early")
        late_sha = self.fixture.commit_file("late.txt", "feat: late")
        script_run = self.fixture.run_script()
        self.assertEqual(script_run.returncode, 0, script_run.stderr)
        self.assertEqual(script_run.stdout.splitlines()[4:],
                         [f"unreviewed: {self.fixture.short_sha(early_sha)} feat: early",
                          f"unreviewed: {self.fixture.short_sha(late_sha)} feat: late"])


def main():
    default_repo = Path(__file__).resolve().parents[2]
    arg_parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    arg_parser.add_argument("--repo", type=Path, default=default_repo)
    arg_parser.add_argument("--reviews", type=Path)
    arg_parser.add_argument("--since")
    arg_parser.add_argument("--self-test", action="store_true")
    cli_args = arg_parser.parse_args()
    if cli_args.self_test:
        test_suite = unittest.defaultTestLoader.loadTestsFromTestCase(ReviewMeasuresTest)
        test_result = unittest.TextTestRunner(verbosity=2).run(test_suite)
        return 0 if test_result.wasSuccessful() else 1
    reviews_dir = cli_args.reviews or cli_args.repo / ".claude" / "reports" / "reviews"
    try:
        sys.stdout.write(measure_repo(cli_args.repo, reviews_dir, cli_args.since))
    except subprocess.CalledProcessError as git_error:
        sys.stderr.write(f"review-measures: {' '.join(git_error.cmd)} failed\n{git_error.stderr}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
