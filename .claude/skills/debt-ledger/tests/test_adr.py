import os
import subprocess
from pathlib import Path

SKILL_DIR = Path(__file__).resolve().parents[1]
FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures"
CLI_FILE = SKILL_DIR / "bin" / "debt"
KINDS_DIR = FIXTURE_DIR / "adr-kinds"
PROBLEMS_DIR = FIXTURE_DIR / "adr-problems"
KIND_LIST = "[assert], [absent], [gh], [prod-read], [manual], [prose]"
CLAIM = "states more than its command checks; mark it [manual] or make the command the whole check"


def run_debt(*cli_args):
    stub_path = f"{FIXTURE_DIR / 'bin'}{os.pathsep}{os.environ['PATH']}"
    return subprocess.run([str(CLI_FILE), *cli_args], capture_output=True, text=True,
                          env={**os.environ, "PATH": stub_path})


def test_check_runs_asserting_lines_and_lists_the_others_by_kind():
    check_result = run_debt("adr", "check", "--dir", str(KINDS_DIR))
    output_lines = check_result.stdout.splitlines()
    assert output_lines[:2] == ["PASS 900:5 test -d docs", "FAIL 900:6 false"]
    assert output_lines[2].startswith("BROKEN 900:7: exit 2 — ")
    assert output_lines[2].endswith(" — test 1 -gt x")
    assert output_lines[3:] == [
        "PASS 900:8 rg -q fixture-token-nothing docs",
        "FAIL 900:10 rg -q fixture-token-match docs",
        "PASS 900:16 test -d docs",
        "not run gh: 900:12",
        "not run prod-read: 900:13",
        "not run manual: 900:14",
        "not run prose: 900:15",
        "2 lines failed, 1 line broken",
    ]
    assert check_result.returncode == 1


def test_check_exits_zero_when_every_asserting_line_passes(tmp_path):
    (tmp_path / "902-passing.md").write_text(
        "# 902 passing\n\n## Decisions\n"
        "- @fixture — Passes. Re-check [assert]: `test -d docs`.\n"
        "- @fixture — Absent. Re-check [absent]: `rg -q fixture-token-nothing docs`.\n"
        "- @fixture — Manual. Re-check [manual]: `false` after a build.\n"
        "\n## Takeaway\n- takeaway: none.\n", encoding="utf-8")
    check_result = run_debt("adr", "check", "--dir", str(tmp_path))
    assert check_result.stdout.splitlines() == [
        "PASS 902:4 test -d docs",
        "PASS 902:5 rg -q fixture-token-nothing docs",
        "not run manual: 902:6",
    ]
    assert check_result.returncode == 0


def test_doctor_counts_every_kind_and_skips_superseded_and_quoted_lines():
    doctor_result = run_debt("adr", "doctor", "--dir", str(KINDS_DIR))
    assert doctor_result.stdout.splitlines() == [
        "lines 10, assert 4, absent 2, gh 1, prod-read 1, manual 1, prose 1, unmarked 0, malformed 0",
    ]
    assert doctor_result.returncode == 0


def test_doctor_names_unmarked_and_malformed_lines():
    doctor_result = run_debt("adr", "doctor", "--dir", str(PROBLEMS_DIR))
    assert doctor_result.stdout.splitlines() == [
        "lines 8, assert 4, absent 2, gh 0, prod-read 0, manual 0, prose 0, unmarked 1, malformed 6",
        "unmarked: 901:5",
        f'malformed: 901:6 — kind "[asert]" is not one of {KIND_LIST}',
        'malformed: 901:7 — starts a clause with "ls", which is not an allowed re-check tool; '
        "see debt help, or mark it [manual]",
        "malformed: 901:8 — an [absent] line runs one rg clause; mark it [assert] or [manual]",
        "malformed: 901:9 — names no backticked command after the marker; mark it [manual] or [prose]",
        f"malformed: 901:11 — {CLAIM}",
        f"malformed: 901:12 — {CLAIM}",
    ]
    assert doctor_result.returncode == 1


def test_check_reports_unmarked_and_malformed_lines_as_broken_and_runs_the_rest():
    check_result = run_debt("adr", "check", "--dir", str(PROBLEMS_DIR))
    assert check_result.stdout.splitlines() == [
        f"BROKEN 901:5: unmarked; add one of {KIND_LIST} after Re-check",
        f'BROKEN 901:6: kind "[asert]" is not one of {KIND_LIST}',
        'BROKEN 901:7: starts a clause with "ls", which is not an allowed re-check tool; '
        "see debt help, or mark it [manual]",
        "BROKEN 901:8: an [absent] line runs one rg clause; mark it [assert] or [manual]",
        "BROKEN 901:9: names no backticked command after the marker; mark it [manual] or [prose]",
        f"BROKEN 901:11: {CLAIM}",
        f"BROKEN 901:12: {CLAIM}",
        "PASS 901:13 true",
        "0 lines failed, 7 lines broken",
    ]
    assert check_result.returncode == 1


def test_a_command_on_the_next_line_of_the_bullet_is_found(tmp_path):
    (tmp_path / "903-wrapped.md").write_text(
        "# 903 wrapped\n\n## Decisions\n"
        "- @fixture — Wraps. Re-check [assert]:\n"
        "  `test -d docs` succeeds.\n"
        "\n## Takeaway\n- takeaway: none.\n", encoding="utf-8")
    check_result = run_debt("adr", "check", "--dir", str(tmp_path))
    assert check_result.stdout.splitlines() == ["PASS 903:4 test -d docs"]


def test_adr_refuses_a_missing_directory_and_an_unknown_subcommand(tmp_path):
    missing_result = run_debt("adr", "check", "--dir", str(tmp_path / "none"))
    assert missing_result.returncode == 2
    assert "pass --dir <path>" in missing_result.stderr
    unknown_result = run_debt("adr", "run")
    assert unknown_result.returncode == 2
    assert "run debt adr doctor or debt adr check" in unknown_result.stderr
