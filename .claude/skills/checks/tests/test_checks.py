import datetime
import importlib.machinery
import importlib.util
import json
import subprocess
import sys
from pathlib import Path

import pytest

SKILL_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = SKILL_DIR.parents[2]
FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures"
CLI_FILE = SKILL_DIR / "bin" / "checks"
HOOK_FILE = REPO_ROOT / ".claude" / "hooks" / "check-output-redirect.sh"

cli_loader = importlib.machinery.SourceFileLoader("checks_cli", str(CLI_FILE))
module_spec = importlib.util.spec_from_loader("checks_cli", cli_loader)
checks_cli = importlib.util.module_from_spec(module_spec)
sys.modules["checks_cli"] = checks_cli
cli_loader.exec_module(checks_cli)

FAKE_PATHS = checks_cli.RepoPaths(Path("/repo/wt"), Path("/repo"))
CLI_TEXT = checks_cli.CLI_PATH


def plan_for(*command_words):
    return checks_cli.build_plan(FAKE_PATHS, command_words[0], command_words[1], list(command_words[2:]))


def test_global_options_are_taken_before_and_after_the_lane():
    command_line = checks_cli.parse_command_line(["--root", "/x", "be", "test", "--print", "--tests", "A"])
    assert command_line.root_arg == "/x"
    assert command_line.print_only
    assert command_line.command_words == ("be", "test", "--tests", "A")


def test_words_after_double_dash_reach_the_check():
    command_line = checks_cli.parse_command_line(["e2e", "run", "a.spec.ts", "--", "--print", "--headed"])
    assert not command_line.print_only
    assert command_line.command_words == ("e2e", "run", "a.spec.ts", "--", "--print", "--headed")


def test_help_flag_is_seen():
    assert checks_cli.parse_command_line(["--help"]).wants_help


def test_root_without_a_path_names_the_fix():
    with pytest.raises(checks_cli.CheckError, match="--root needs a path"):
        checks_cli.parse_command_line(["--root"])


def test_yarn_checks_run_their_script_in_the_package():
    typecheck_plan = plan_for("fe", "typecheck")
    assert typecheck_plan.command_argv == ("yarn", "typecheck")
    assert typecheck_plan.work_dir == Path("/repo/wt/frontend")
    assert plan_for("fe", "bailouts").command_argv == ("yarn", "check:compiler-bailouts")
    assert plan_for("e2e", "fmt-check").command_argv == ("yarn", "fmt:check")
    assert plan_for("e2e", "fmt-check").work_dir == Path("/repo/wt/e2e")


def test_build_carries_the_ci_heap():
    assert dict(plan_for("fe", "build").extra_env) == {"NODE_OPTIONS": "--max-old-space-size=6144"}


def test_frontend_test_passes_its_arguments_to_yarn_test_run():
    assert plan_for("fe", "test", "src/pages/abEvent", "-t", "name").command_argv == (
        "yarn", "test", "run", "src/pages/abEvent", "-t", "name")


def test_unknown_check_names_the_lane_line():
    with pytest.raises(checks_cli.CheckError) as raised_error:
        plan_for("fe", "tests")
    assert "fe has no check 'tests'" in str(raised_error.value)
    assert "fe checks: typecheck, lint, lint-validation" in str(raised_error.value)


def test_unknown_lane_names_the_lanes():
    with pytest.raises(checks_cli.CheckError, match="lanes: be, e2e, fe"):
        plan_for("frontend", "typecheck")


def test_a_check_without_arguments_refuses_them():
    with pytest.raises(checks_cli.CheckError, match="takes no arguments"):
        plan_for("fe", "lint", "src")


def test_e2e_run_maps_its_flags_onto_playwright():
    run_plan = plan_for("e2e", "run", "tests/discard-durability.spec.ts", "--base-url", "http://[::1]:4183",
                        "--repeat", "2", "--jobs=2")
    assert run_plan.command_argv == (
        "/repo/wt/e2e/node_modules/.bin/playwright", "test", "--config", "playwright.config.ts",
        "--project=chromium", "tests/discard-durability.spec.ts", "--repeat-each=2", "--workers=2")
    assert dict(run_plan.extra_env) == {"E2E_BASE_URL": "http://[::1]:4183"}
    assert run_plan.work_dir == Path("/repo/wt/e2e")


def test_e2e_run_passes_words_after_double_dash():
    run_plan = plan_for("e2e", "run", "--project", "contract", "--", "--grep", "login")
    assert run_plan.command_argv[4:] == ("--project=contract", "--grep", "login")


def test_e2e_run_refuses_a_bad_count_and_unknown_flags():
    with pytest.raises(checks_cli.CheckError, match="--jobs takes a positive whole number"):
        plan_for("e2e", "run", "--jobs", "0")
    with pytest.raises(checks_cli.CheckError, match="pass playwright's own flags after --"):
        plan_for("e2e", "run", "--headed")


def test_backend_targeted_test_adds_the_archunit_pattern():
    run_plan = plan_for("be", "test", "--tests", "*PlannerCommandService*")
    assert run_plan.command_argv == (
        "/repo/wt/backend/gradlew", "-p", "/repo/wt/backend", "--console=plain", "test",
        "--tests", "*PlannerCommandService*", "--tests", "org.danteplanner.backend.architecture.*")


def test_backend_full_test_runs_without_a_filter():
    assert plan_for("be", "test").command_argv[-1] == "test"
    assert plan_for("be", "test", "--exclude-tags", "containerized").command_argv[-1] == \
        "-PexcludeTags=containerized"


def test_backend_task_checks():
    assert plan_for("be", "check").command_argv[4:] == ("check",)
    assert plan_for("be", "checkstyle").command_argv[4:] == ("checkstyleMain", "checkstyleTest", "--rerun-tasks")
    assert plan_for("be", "pit").command_argv[4:] == ("pitestRatchet",)


def test_list_names_every_check_once():
    list_lines = checks_cli.list_lines()
    list_text = "\n".join(list_lines)
    for one_spec in checks_cli.CHECK_TABLE:
        command_prefix = f"checks {one_spec.lane_name} {one_spec.check_name}"
        assert sum(1 for text_line in list_lines
                   if text_line == command_prefix or text_line.startswith(command_prefix + " ")) == 1
    assert "checks doctor" in list_text


PR_GATE_STEPS = [
    ("FE Install", "yarn install --frozen-lockfile"),
    ("FE Typecheck", "yarn typecheck"),
    ("FE Test", "yarn test:coverage"),
    ("FE Lint", "yarn lint"),
    ("FE Validation Lint", "yarn lint:validation"),
    ("FE False Positive Scan", "yarn check:fp"),
    ("FE Rule Tests", "yarn check:rules"),
    ("FE Compiler Bailouts", "yarn check:compiler-bailouts"),
    ("FE Format Check", "yarn fmt:check"),
]


def test_the_real_workflow_yields_the_test_frontend_steps_in_order():
    workflow_text = (REPO_ROOT / checks_cli.WORKFLOW_PATH).read_text()
    parsed_steps = checks_cli.workflow_steps(workflow_text, "test-frontend")
    assert [(one_step.step_name, one_step.run_text) for one_step in parsed_steps] == PR_GATE_STEPS
    assert {one_step.work_dir for one_step in parsed_steps} == {"./frontend"}


def test_the_line_parser_agrees_with_a_yaml_parser():
    yaml_module = pytest.importorskip("yaml")
    workflow_data = yaml_module.safe_load((REPO_ROOT / checks_cli.WORKFLOW_PATH).read_text())
    for job_name, job_data in workflow_data["jobs"].items():
        if "steps" not in job_data:
            continue
        yaml_steps = [(one_step.get("name") or one_step["run"].splitlines()[0], one_step["run"].rstrip("\n"),
                       one_step.get("working-directory", "."))
                      for one_step in job_data["steps"] if "run" in one_step]
        workflow_text = (REPO_ROOT / checks_cli.WORKFLOW_PATH).read_text()
        try:
            parsed_steps = checks_cli.workflow_steps(workflow_text, job_name)
        except checks_cli.CheckError as parse_error:
            assert "GitHub expression" in str(parse_error)
            continue
        assert [(one_step.step_name, one_step.run_text, one_step.work_dir)
                for one_step in parsed_steps] == yaml_steps, job_name


SYNTHETIC_WORKFLOW = """name: x
jobs:
  other:
    steps:
      - run: echo other
  test-frontend:
    runs-on: ubuntu-latest
    steps:
      # a comment between steps
      - uses: actions/checkout@abc  # v1
      - name: 'Quoted name'
        working-directory: ./frontend
        run: yarn lint  # trailing comment
      - run: |
          yarn one
          yarn two

      - name: With env
        env:
          NODE_OPTIONS: '--max-old-space-size=6144'
          PLAIN: value
        run: yarn build
  after:
    steps:
      - run: echo after
"""


def test_the_line_parser_reads_blocks_env_quotes_and_comments():
    parsed_steps = checks_cli.workflow_steps(SYNTHETIC_WORKFLOW, "test-frontend")
    assert [(one_step.step_name, one_step.run_text, one_step.work_dir, one_step.step_env)
            for one_step in parsed_steps] == [
        ("Quoted name", "yarn lint", "./frontend", ()),
        ("yarn one", "yarn one\nyarn two", ".", ()),
        ("With env", "yarn build", ".", (("NODE_OPTIONS", "--max-old-space-size=6144"), ("PLAIN", "value"))),
    ]


def test_a_github_expression_stops_the_gate():
    expression_workflow = SYNTHETIC_WORKFLOW.replace("run: yarn build", "run: yarn build ${{ matrix.x }}")
    with pytest.raises(checks_cli.CheckError, match="GitHub expression"):
        checks_cli.workflow_steps(expression_workflow, "test-frontend")


def test_a_missing_job_names_the_job():
    with pytest.raises(checks_cli.CheckError, match="no job 'test-frontend'"):
        checks_cli.workflow_steps("jobs:\n  other:\n    steps: []\n", "test-frontend")


def test_the_gate_skips_installs_and_runs_each_step_with_bash(tmp_path):
    workflow_file = tmp_path / checks_cli.WORKFLOW_PATH
    workflow_file.parent.mkdir(parents=True)
    workflow_file.write_text((REPO_ROOT / checks_cli.WORKFLOW_PATH).read_text())
    gate_rows = checks_cli.gate_plans(checks_cli.RepoPaths(tmp_path, tmp_path))
    assert gate_rows[0][1] is None
    assert [workflow_step.step_name for workflow_step, _ in gate_rows] == [step_name for step_name, _ in PR_GATE_STEPS]
    typecheck_plan = gate_rows[1][1]
    assert typecheck_plan.command_argv[-2:] == ("-c", "yarn typecheck")
    assert typecheck_plan.work_dir == tmp_path / "frontend"
    assert typecheck_plan.module_dir == tmp_path / "frontend"


def test_log_names_carry_lane_check_and_second():
    run_moment = datetime.datetime(2026, 11, 8, 23, 5, 9)
    assert checks_cli.log_path_for(Path("/logs"), ("fe", "test"), run_moment) == \
        Path("/logs/checks-fe-test-20261108-230509.log")
    assert checks_cli.log_path_for(Path("/logs"), ("fe", "gate"), run_moment, ("02", "FE Typecheck")) == \
        Path("/logs/checks-fe-gate-20261108-230509-02-fe-typecheck.log")


def test_a_second_log_in_the_same_second_gets_a_suffix(tmp_path):
    run_moment = datetime.datetime(2026, 11, 8, 23, 5, 9)
    (tmp_path / "checks-be-pit-20261108-230509.log").write_text("")
    assert checks_cli.log_path_for(tmp_path, ("be", "pit"), run_moment).name == "checks-be-pit-20261108-230509-2.log"


def test_the_default_root_is_the_checkout_holding_the_cli():
    assert checks_cli.own_checkout_dir() == REPO_ROOT


def test_the_cli_targets_its_own_checkout_from_any_directory(tmp_path):
    print_run = subprocess.run([sys.executable, "-I", str(CLI_FILE), "--print", "fe", "typecheck"],
                               cwd=tmp_path, capture_output=True, text=True)
    assert print_run.returncode == 0, print_run.stderr
    assert f"cwd  {REPO_ROOT / 'frontend'}" in print_run.stdout.splitlines()


def test_root_overrides_the_cli_location(tmp_path):
    subprocess.run(["git", "init", "-q", str(tmp_path)], check=True)
    print_run = subprocess.run([sys.executable, "-I", str(CLI_FILE), "--root", str(tmp_path), "--print",
                                "fe", "typecheck"], capture_output=True, text=True)
    assert f"cwd  {tmp_path.resolve() / 'frontend'}" in print_run.stdout.splitlines()


def test_logs_go_to_the_worktree_when_it_has_reports(tmp_path):
    worktree_root = tmp_path / "wt"
    (worktree_root / ".claude" / "reports").mkdir(parents=True)
    repo_paths = checks_cli.RepoPaths(worktree_root, tmp_path / "main")
    assert repo_paths.log_dir == worktree_root / ".claude" / "reports" / "logs"
    assert repo_paths.lock_path == tmp_path / "main" / ".claude" / ".test.lock"


def test_logs_go_to_the_main_checkout_when_the_worktree_has_none(tmp_path):
    repo_paths = checks_cli.RepoPaths(tmp_path / "wt", tmp_path / "main")
    assert repo_paths.log_dir == tmp_path / "main" / ".claude" / "reports" / "logs"


def fixture_text(fixture_name):
    return (FIXTURE_DIR / fixture_name).read_text()


def test_summary_of_a_vitest_run():
    assert checks_cli.summary_line(fixture_text("vitest-pass.log")) == "Test Files  11 passed (11)"


def test_summary_of_a_failing_tsc_run():
    assert checks_cli.summary_line(fixture_text("tsc-fail.log")) == (
        "1 tsc error; first: src/checksPlantedError.ts(1,14): error TS2322: "
        "Type 'string' is not assignable to type 'number'.")


def test_summary_of_a_clean_tsc_run():
    assert checks_cli.summary_line("yarn run v1.22.22\n$ tsc -b\nDone in 9.84s.\n") == "0 tsc errors"


def test_summary_falls_back_to_the_tool_s_last_line():
    assert checks_cli.summary_line(fixture_text("oxfmt-pass.log")) == \
        "Finished in 225ms on 1234 files using 12 threads."


def test_summary_of_a_playwright_run():
    assert checks_cli.summary_line(fixture_text("playwright-pass.log")) == "4 passed (4.4s)"


def test_summary_of_a_gradle_run():
    assert checks_cli.summary_line(fixture_text("gradle-pass.log")).startswith("BUILD SUCCESSFUL")


def test_summary_counts_oxlint_diagnostics():
    assert checks_cli.summary_line(fixture_text("oxlint-warnings.log")) == "100 warnings, 0 errors"


def test_summary_of_a_vite_build():
    assert checks_cli.summary_line(fixture_text("vite-build-tail.log")) == "\u2713 built in 36.86s"


def test_summary_of_a_silent_tool_names_the_command():
    assert checks_cli.summary_line("yarn run v1.22.22\n$ ast-grep scan\nDone in 0.29s.\n") == \
        "(no output from `ast-grep scan`)"


def test_lock_holders_read_proc_locks():
    holder_rows = checks_cli.lock_holders(fixture_text("proc-locks.txt"), "00:26:613729",
                                          command_of=lambda holder_pid: f"cmd {holder_pid}")
    assert holder_rows == [checks_cli.LockHolder(2169408, "WRITE", "cmd 2169408")]


HOOK_TABLE = [
    ("yarn --cwd frontend typecheck", f"{CLI_TEXT} fe typecheck"),
    ("yarn --cwd frontend run typecheck", f"{CLI_TEXT} fe typecheck"),
    ("yarn --cwd frontend run test", f"{CLI_TEXT} fe test"),
    ("yarn --cwd frontend test run src/a", f"{CLI_TEXT} fe test src/a"),
    ("yarn --cwd /home/u/wt/frontend test:coverage", f"{CLI_TEXT} fe test"),
    ("./node_modules/.bin/vitest run", f"{CLI_TEXT} fe test"),
    ("frontend/node_modules/.bin/vitest run src/x.test.ts", f"{CLI_TEXT} fe test src/x.test.ts"),
    ("backend/gradlew -p backend test", f"{CLI_TEXT} be test"),
    ("./gradlew -p backend test --tests '*Foo*' > .claude/reports/logs/be.log 2>&1 && echo PASS",
     f"{CLI_TEXT} be test --tests '*Foo*'"),
    ("/r/backend/gradlew -p /r/backend checkstyleMain checkstyleTest", f"{CLI_TEXT} be checkstyle"),
    ("./gradlew -p backend pitest pitestRatchet", f"{CLI_TEXT} be pit"),
    ("./gradlew -p backend build", f"{CLI_TEXT} be check"),
    ("./gradlew -p backend :compileJava", f"{CLI_TEXT} be check"),
    ("playwright test", f"{CLI_TEXT} e2e run"),
    ("env -C e2e node_modules/.bin/playwright test tests/a.spec.ts --config playwright.config.ts",
     f"{CLI_TEXT} e2e run tests/a.spec.ts"),
    ("yarn --cwd e2e test:app", f"{CLI_TEXT} e2e run"),
    ("yarn --cwd e2e test:contract", f"{CLI_TEXT} e2e run --project contract"),
    ("yarn --cwd e2e typecheck", f"{CLI_TEXT} e2e typecheck"),
    ("yarn --cwd e2e fmt:check", f"{CLI_TEXT} e2e fmt-check"),
    ("yarn --cwd frontend tsc -b", f"{CLI_TEXT} fe typecheck"),
    ("yarn --cwd frontend lint", f"{CLI_TEXT} fe lint"),
    ("yarn --cwd frontend lint:validation", f"{CLI_TEXT} fe lint-validation"),
    ("frontend/node_modules/.bin/oxlint -c .oxlintrc.validation.json src", f"{CLI_TEXT} fe lint-validation"),
    ("yarn --cwd frontend fmt", f"{CLI_TEXT} fe fmt"),
    ("yarn --cwd frontend check:fp", f"{CLI_TEXT} fe fp"),
    ("yarn --cwd frontend check:bundle-budget", f"{CLI_TEXT} fe budget"),
    ("yarn --cwd frontend build", f"{CLI_TEXT} fe build"),
    ("tsc --noEmit", f"{CLI_TEXT} fe typecheck"),
    ("flock /r/.claude/.test.lock -c 'yarn --cwd frontend test run src/a > x.log 2>&1'", f"{CLI_TEXT} fe test src/a"),
    ("flock -n /r/.claude/.test.lock yarn --cwd frontend typecheck", f"{CLI_TEXT} fe typecheck"),
    ("bash -c 'yarn --cwd frontend lint'", f"{CLI_TEXT} fe lint"),
    ("echo \"$(yarn --cwd frontend fmt:check)\"", f"{CLI_TEXT} fe fmt-check"),
    ("echo `yarn --cwd frontend typecheck`", f"{CLI_TEXT} fe typecheck"),
    ("x=`frontend/node_modules/.bin/vitest run`; echo $x", f"{CLI_TEXT} fe test"),
    ("echo \\`yarn --cwd frontend typecheck\\`", None),
    ("NODE_OPTIONS=--max-old-space-size=4096 timeout 600 yarn --cwd frontend test", f"{CLI_TEXT} fe test"),
    ("ls && yarn --cwd frontend typecheck | tail -3", f"{CLI_TEXT} fe typecheck"),
    ("checks fe typecheck", None),
    (".claude/skills/checks/bin/checks fe test src/pages/abEvent", None),
    ("python3 -I /x/.claude/skills/checks/bin/checks be test --tests '*X*'", None),
    ("yarn --cwd frontend install", None),
    ("yarn --cwd frontend install --frozen-lockfile", None),
    ("yarn add zod", None),
    ("yarn", None),
    ("yarn --cwd frontend dev", None),
    ("yarn --cwd frontend preview --port 4183 --host ::1", None),
    ("yarn --cwd frontend check --integrity", None),
    ("yarn --cwd frontend generate:sitemap", None),
    ("yarn --cwd e2e stack:up", None),
    ("yarn --cwd e2e playwright install chromium", None),
    ("yarn --cwd /elsewhere/static test", None),
    ("./gradlew -p backend bootRun", None),
    ("./gradlew -p backend dependencies", None),
    ("git commit -m \"fix yarn test and vitest\"", None),
    ("rg -n 'vitest|tsc' frontend", None),
    ("cat .claude/reports/logs/fe-test-1.log | tail -5", None),
    ("git commit -F - <<'EOF'\nrun yarn --cwd frontend test\nEOF", None),
    ("echo 'unbalanced", None),
]


@pytest.mark.parametrize(("command_text", "expected_replacement"), HOOK_TABLE)
def test_hook_classification(command_text, expected_replacement):
    found_refusals = checks_cli.classify_command(command_text)
    if expected_replacement is None:
        assert found_refusals == []
    else:
        assert [one_refusal.replacement_text for one_refusal in found_refusals] == [expected_replacement]


def run_hook(hook_input):
    return subprocess.run(["bash", str(HOOK_FILE)], input=json.dumps(hook_input), capture_output=True, text=True)


def test_the_hook_script_refuses_with_the_replacement():
    hook_run = run_hook({"tool_name": "Bash", "tool_input": {"command": "yarn --cwd frontend run typecheck"}})
    assert hook_run.returncode == 2
    assert f"run      {CLI_TEXT} fe typecheck" in hook_run.stderr


def test_the_hook_script_passes_other_commands_and_tools():
    assert run_hook({"tool_name": "Bash", "tool_input": {"command": f"{CLI_TEXT} fe typecheck"}}).returncode == 0
    assert run_hook({"tool_name": "Read", "tool_input": {"file_path": "x"}}).returncode == 0
    assert subprocess.run(["bash", str(HOOK_FILE)], input="not json", capture_output=True,
                          text=True).returncode == 0


def write_package(package_root, installed_oxlint):
    package_root.mkdir(parents=True)
    (package_root / "package.json").write_text(json.dumps(
        {"devDependencies": {"oxlint": "1.90.0", "oxfmt": "0.75.0", "vitest": "^4.1.2"}}))
    (package_root / "yarn.lock").write_text(fixture_text("frontend-yarn-lock-excerpt.txt"))
    for tool_name, tool_version in (("oxlint", installed_oxlint), ("oxfmt", "0.75.0"), ("vitest", "4.1.2")):
        tool_dir = package_root / "node_modules" / tool_name
        tool_dir.mkdir(parents=True)
        (tool_dir / "package.json").write_text(json.dumps({"name": tool_name, "version": tool_version}))


def test_tool_versions_compare_the_install_with_the_lockfile(tmp_path):
    write_package(tmp_path / "frontend", "1.89.0")
    version_rows = {one_row.tool_name: one_row for one_row in checks_cli.tool_versions(tmp_path / "frontend",
                                                                                         "frontend")}
    assert version_rows["oxlint"].is_stale
    assert version_rows["oxlint"].locked_version == "1.90.0"
    assert not version_rows["vitest"].is_stale
    assert version_rows["vitest"].locked_version == "4.1.2"


def fake_run(command_argv, **_):
    if command_argv[:2] == ["java", "-version"]:
        return subprocess.CompletedProcess(command_argv, 0, "", 'openjdk version "21.0.1"\n')
    return subprocess.CompletedProcess(command_argv, 0, "29.0.0\n", "")


def test_doctor_names_the_stale_package_both_versions_and_the_install(tmp_path):
    write_package(tmp_path / "frontend", "1.89.0")
    out_lines, problem_count = checks_cli.doctor_lines(checks_cli.RepoPaths(tmp_path, tmp_path), run_command=fake_run)
    assert problem_count == 1
    assert (f"frontend STALE oxlint: package.json 1.90.0, yarn.lock 1.90.0, installed 1.89.0; "
            f"run yarn --cwd {tmp_path / 'frontend'} install --frozen-lockfile") in out_lines
