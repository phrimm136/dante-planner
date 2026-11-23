import contextlib
import datetime
import importlib.machinery
import importlib.util
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import unittest.mock
from pathlib import Path

SKILL_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = SKILL_DIR.parents[2]
FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures"
CLI_FILE = SKILL_DIR / "bin" / "ops"
HOOK_FILE = REPO_ROOT / ".claude" / "hooks" / "ops-redirect.sh"

cli_loader = importlib.machinery.SourceFileLoader("ops_cli", str(CLI_FILE))
module_spec = importlib.util.spec_from_loader("ops_cli", cli_loader)
ops_cli = importlib.util.module_from_spec(module_spec)
sys.modules["ops_cli"] = ops_cli
cli_loader.exec_module(ops_cli)

RUN_MOMENT = datetime.datetime(2026, 11, 9, 12, 30, 5)
STAMP_TEXT = "20261109-123005"
CLI_TEXT = ops_cli.CLI_PATH
EXPIRED_STDERR = ("\nError when retrieving token from sso: Token has expired and refresh failed\n")
S3_BACKEND_PATTERN = re.compile(r'^\s*backend\s+"s3"\s*\{', re.MULTILINE)


def stacks_without_s3_backend(repo_root):
    tracked_paths = subprocess.run(["git", "-C", str(repo_root), "ls-files", "--", "terraform"],
                                   capture_output=True, text=True, check=True).stdout.split()
    return [stack_name for stack_name in ops_cli.plan_stacks(repo_root)
            if not any(S3_BACKEND_PATTERN.search((repo_root / tracked_path).read_text(encoding="utf-8"))
                       for tracked_path in tracked_paths
                       if tracked_path.startswith(f"terraform/{stack_name}/") and tracked_path.count("/") == 2
                       and tracked_path.endswith(".tf"))]
PROFILE_ACCOUNTS = {"prod": "111122223333", "staging": "444455556666", "mgmt": "777788889999"}
UNMAPPED_ID = "999988887777"
FAKE_CONFIG_TEXT = "".join(f"[profile {profile_name}]\nsso_session = s\nsso_account_id = {account_id}\n\n"
                           for profile_name, account_id in PROFILE_ACCOUNTS.items())
FAKE_CONFIG_TEXT += "[profile prod-provisioner]\nsso_account_id = 111122223333\n"
PLAN_BYTES = b"tfplan zip bytes\x00\x01"


def identity_json(account_id):
    return json.dumps({"Account": account_id, "Arn": f"arn:aws:sts::{account_id}:assumed-role/AdminRole/caller-x"})
TABLE_SCRIPTS = {one_spec.script_path for one_spec in ops_cli.DISPATCH_TABLE if one_spec.script_path}


def plugin_argv(local_port, remote_host, region_name):
    start_request = {"Target": "i-0abc", "DocumentName": "AWS-StartPortForwardingSessionToRemoteHost",
                     "Parameters": {"host": [remote_host], "portNumber": ["3306"], "localPortNumber": [local_port]}}
    return ("session-manager-plugin", "AWS_SSM_START_SESSION_RESPONSE", region_name, "StartSession", "",
            json.dumps(start_request), f"https://ssm.{region_name}.amazonaws.com")


class FakeEffects:
    def __init__(self, session_rows=None, docker_up=True, stream_exit=0, show_text="", process_list=(),
                 tool_paths=None, stream_text="script output\n", identity_accounts=None):
        self.stream_text = stream_text
        self.identity_accounts = identity_accounts or PROFILE_ACCOUNTS
        self.session_rows = session_rows or {}
        self.docker_up = docker_up
        self.stream_exit = stream_exit
        self.show_text = show_text
        self.process_list = list(process_list)
        self.tool_paths = {"session-manager-plugin": "/usr/bin/session-manager-plugin", "jq": "/usr/bin/jq"} \
            if tool_paths is None else tool_paths
        self.capture_calls = []
        self.stream_calls = []

    def run_captured(self, command_argv, child_env=None, work_dir=None, timeout_seconds=None):
        command_argv = list(command_argv)
        self.capture_calls.append((command_argv, child_env))
        if command_argv[:3] == ["aws", "sts", "get-caller-identity"]:
            profile_name = command_argv[command_argv.index("--profile") + 1]
            exit_code, stderr_text = self.session_rows.get(profile_name, (0, ""))
            identity_text = identity_json(self.identity_accounts.get(profile_name, UNMAPPED_ID))
            return subprocess.CompletedProcess(command_argv, exit_code, identity_text if exit_code == 0 else "",
                                               stderr_text)
        if command_argv[:2] == ["docker", "version"]:
            return subprocess.CompletedProcess(command_argv, 0 if self.docker_up else 1,
                                               "27.3.1\n" if self.docker_up else "",
                                               "" if self.docker_up else "Cannot connect to the Docker daemon at "
                                               "unix:///var/run/docker.sock. Is the docker daemon running?")
        if command_argv[:3] == ["docker", "image", "inspect"]:
            return subprocess.CompletedProcess(command_argv, 0, "sha256:abc\n", "")
        if "show" in command_argv:
            return subprocess.CompletedProcess(command_argv, 0, self.show_text, "")
        raise AssertionError(f"unexpected capture {command_argv}")

    def run_streamed(self, command_argv, child_env, work_dir, log_file, echo_output):
        self.stream_calls.append((tuple(command_argv), child_env, work_dir, echo_output))
        for one_arg in command_argv:
            if one_arg.startswith("-out="):
                Path(one_arg.split("=", 1)[1]).write_bytes(PLAN_BYTES)
        log_file.write(self.stream_text)
        if echo_output:
            sys.stdout.write(self.stream_text)
        return self.stream_exit

    def process_rows(self):
        return self.process_list

    def tool_path(self, tool_name):
        return self.tool_paths.get(tool_name, "")


class RepoCase(unittest.TestCase):
    def setUp(self):
        self.temp_handle = tempfile.TemporaryDirectory()
        self.repo_root = Path(self.temp_handle.name).resolve()
        (self.repo_root / ".claude" / "reports").mkdir(parents=True)
        (self.repo_root / "scripts" / "ops" / "lib").mkdir(parents=True)
        shutil.copy(REPO_ROOT / ops_cli.CONSTANTS_PATH, self.repo_root / ops_cli.CONSTANTS_PATH)
        for stack_name, backend_text in (("rds", 'terraform {\n  backend "s3" {}\n}\n'),
                                         ("oregon", '  backend "s3" {\n  }\n'),
                                         ("state-backend", 'terraform {\n  backend "s3" {}\n}\n'),
                                         ("registry", '# init -backend-config=../backend.management.hcl\n'
                                                      'terraform {\n  backend "s3" {}\n}\n'),
                                         ("log-archive", '# init -backend-config=../backend.log-archive.hcl\n'
                                                         'terraform {\n  backend "s3" {}\n}\n')):
            stack_dir = self.repo_root / "terraform" / stack_name
            stack_dir.mkdir(parents=True)
            (stack_dir / "versions.tf").write_text(backend_text)
            if stack_name in ("rds", "oregon"):
                (stack_dir / ".terraform").mkdir()
        (self.repo_root / "terraform" / "modules").mkdir()
        self.repo_paths = ops_cli.RepoPaths(self.repo_root, self.repo_root)
        self.log_dir = self.repo_root / ".claude" / "reports" / "logs"
        config_file = self.repo_root / "aws-config"
        config_file.write_text(FAKE_CONFIG_TEXT)
        self.env_patch = unittest.mock.patch.dict(os.environ, {"AWS_CONFIG_FILE": str(config_file)})
        self.env_patch.start()

    def tearDown(self):
        self.env_patch.stop()
        self.temp_handle.cleanup()

    def assert_no_ids(self, *checked_texts):
        for checked_text in checked_texts:
            for account_id in (*PROFILE_ACCOUNTS.values(), UNMAPPED_ID):
                self.assertNotIn(account_id, checked_text)

    def run_main(self, argv_words, fake_effects):
        out_buffer, err_buffer = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out_buffer), contextlib.redirect_stderr(err_buffer):
            exit_code = ops_cli.main(argv_words, fake_effects, str(self.repo_root), RUN_MOMENT, self.repo_paths)
        return exit_code, out_buffer.getvalue(), err_buffer.getvalue()

    def script_argv(self, script_path, *script_args):
        return (str(self.repo_root / script_path), *script_args)


class ParseTest(unittest.TestCase):
    def test_env_is_taken_anywhere_before_double_dash(self):
        self.assertEqual(ops_cli.parse_request(["sql", "SELECT 1", "--env", "staging"]).env_name, "staging")
        self.assertEqual(ops_cli.parse_request(["--env=staging", "logs", "q"]).env_name, "staging")
        self.assertEqual(ops_cli.parse_request(["logs", "q"]).env_name, "")
        self.assertEqual(ops_cli.parse_request(["--env", "mgmt", "whoami"]).env_name, "mgmt")

    def test_words_after_double_dash_stay_with_the_command(self):
        parsed_request = ops_cli.parse_request(["tf", "plan", "rds", "--", "--env", "x", "-var-file=a.tfvars"])
        self.assertEqual(parsed_request.env_name, "")
        self.assertEqual(parsed_request.command_words, ("tf", "plan", "rds", "--", "--env", "x", "-var-file=a.tfvars"))

    def test_unknown_env_names_the_valid_ones(self):
        with self.assertRaisesRegex(ops_cli.OpsError, "use one of prod, staging, mgmt"):
            ops_cli.parse_request(["--env", "log-archive", "whoami"])

    def test_help_flag_is_seen(self):
        self.assertTrue(ops_cli.parse_request(["help"]).wants_help)
        with self.assertRaisesRegex(ops_cli.OpsError, "run ops help"):
            ops_cli.parse_request(["--help"])

    def test_metrics_maps_flags_to_positionals(self):
        self.assertEqual(ops_cli.build_invocation(["metrics", "up"], "/").script_args, ("up",))
        self.assertEqual(ops_cli.build_invocation(["metrics", "up", "--step", "1m"], "/").script_args,
                         ("up", "", "1m"))
        self.assertEqual(ops_cli.build_invocation(["metrics", "up", "--since=6h", "--step", "1m"], "/").script_args,
                         ("up", "6h", "1m"))

    def test_logs_maps_since_limit_and_day(self):
        self.assertEqual(ops_cli.build_invocation(["logs", "{app=\"backend\"}", "--limit", "5"], "/").script_args,
                         ("{app=\"backend\"}", "", "5"))
        self.assertEqual(ops_cli.build_invocation(["logs", "--day", "2026-07-23", "q", "--limit", "9"], "/")
                         .script_args, ("--day", "2026-07-23", "q", "9"))

    def test_logs_refuses_day_with_since(self):
        with self.assertRaisesRegex(ops_cli.OpsError, "pass one"):
            ops_cli.build_invocation(["logs", "--day", "2026-07-23", "q", "--since", "1h"], "/")

    def test_bad_window_and_limit_values_are_refused(self):
        with self.assertRaisesRegex(ops_cli.OpsError, "--since takes a window"):
            ops_cli.build_invocation(["metrics", "up", "--since", "yesterday"], "/")
        with self.assertRaisesRegex(ops_cli.OpsError, "--limit takes a positive whole number"):
            ops_cli.build_invocation(["logs", "q", "--limit", "0"], "/")
        with self.assertRaisesRegex(ops_cli.OpsError, "--day takes a date"):
            ops_cli.build_invocation(["logs", "--day", "23/07", "q"], "/")

    def test_sql_takes_a_statement_or_a_file_and_a_site(self):
        self.assertEqual(ops_cli.build_invocation(["sql", "SELECT 1", "--site", "seoul"], "/").script_args,
                         ("SELECT 1", "seoul"))
        with tempfile.TemporaryDirectory() as caller_dir:
            Path(caller_dir, "q.sql").write_text("SELECT 1;\n")
            file_invocation = ops_cli.build_invocation(["sql", "-f", "q.sql"], caller_dir)
            self.assertEqual(file_invocation.script_args, ("-f", str(Path(caller_dir, "q.sql").resolve())))
            with self.assertRaisesRegex(ops_cli.OpsError, "no SQL file at"):
                ops_cli.build_invocation(["sql", "-f", "missing.sql"], caller_dir)
        with self.assertRaisesRegex(ops_cli.OpsError, "--site takes oregon or seoul"):
            ops_cli.build_invocation(["sql", "SELECT 1", "--site", "tokyo"], "/")

    def test_tunnel_needs_a_session_only_to_start(self):
        self.assertTrue(ops_cli.build_invocation(["tunnel", "start"], "/").needs_session)
        self.assertFalse(ops_cli.build_invocation(["tunnel", "status", "--site", "seoul"], "/").needs_session)
        self.assertEqual(ops_cli.build_invocation(["tunnel", "stop", "--site", "seoul"], "/").script_args,
                         ("stop", "seoul"))
        with self.assertRaisesRegex(ops_cli.OpsError, "mode is one of start, stop, status"):
            ops_cli.build_invocation(["tunnel", "restart"], "/")

    def test_unknown_command_points_at_list(self):
        with self.assertRaisesRegex(ops_cli.OpsError, "run ops list"):
            ops_cli.build_invocation(["restart-redis"], "/")


class DispatchTest(unittest.TestCase):
    def test_the_dispatch_table_is_the_read_and_plan_set_and_one_apply(self):
        command_classes = [
            ("metrics", "scripts/ops/access/metrics-query.sh", "read"),
            ("logs", "scripts/ops/access/logs-query.sh", "read"),
            ("sql", "scripts/ops/access/rds-query.sh", "read"),
            ("tunnel", "scripts/ops/access/rds-tunnel.sh", "read"),
            ("verify db", "scripts/ops/access/db-observability-check.sh", "read"),
            ("verify fleet", "scripts/ops/fleet-verify.sh", "read"),
            ("tf plan", "scripts/ops/terraform-run.sh", "plan"),
            ("tf apply", "scripts/ops/terraform-run.sh", "apply"),
            ("whoami", "", "read"),
            ("doctor", "", "read"),
            ("list", "", "read"),
        ]
        self.assertEqual([(one_spec.command_name, one_spec.script_path) for one_spec in ops_cli.DISPATCH_TABLE],
                         [(command_name, script_path) for command_name, script_path, _ in command_classes],
                         "a new ops command must be classified read, plan or apply in command_classes here; "
                         "an apply also needs the hook to refuse it from the Bash tool")
        self.assertEqual([command_name for command_name, _, class_name in command_classes if class_name == "apply"],
                         ["tf apply"])

    def test_list_says_the_user_runs_apply_and_the_hook_refuses_it(self):
        list_text = "\n".join(ops_cli.list_lines())
        apply_row = next(one_line for one_line in ops_cli.list_lines() if one_line.startswith("ops tf apply"))
        self.assertTrue(apply_row.startswith("ops tf apply <plan file>"))
        self.assertIn("you run it with !, and the hook refuses every form of it that it can see", apply_row)
        self.assertIn("ops tf plan <stack> [--expect <address>]...", list_text)

    def test_help_says_the_hook_is_not_a_sandbox_and_names_the_known_cost(self):
        self.assertIn("is not a sandbox", ops_cli.USAGE_TEXT)
        self.assertIn("put the pattern in a file (rg -f <file>)", ops_cli.USAGE_TEXT)
        self.assertNotIn("never applies", ops_cli.USAGE_TEXT)

    def test_every_invocation_runs_a_script_from_the_table(self):
        for command_words in (["metrics", "up"], ["logs", "q"], ["sql", "SELECT 1"], ["tunnel", "status"],
                              ["verify", "db"], ["verify", "fleet"], ["verify", "fleet", "--site", "seoul"]):
            self.assertIn(ops_cli.build_invocation(command_words, "/").command_spec.script_path, TABLE_SCRIPTS)

    def test_fleet_verify_passes_the_site_and_never_fetches_the_kubeconfig(self):
        self.assertEqual(ops_cli.build_invocation(["verify", "fleet"], "/").script_args, ("oregon",))
        seoul_invocation = ops_cli.build_invocation(["verify", "fleet", "--site", "seoul"], "/")
        self.assertEqual((seoul_invocation.script_args, seoul_invocation.log_parts),
                         (("seoul",), ("verify", "fleet", "seoul")))
        with self.assertRaisesRegex(ops_cli.OpsError, "unknown option --kubeconfig"):
            ops_cli.build_invocation(["verify", "fleet", "--kubeconfig"], "/")
        with self.assertRaisesRegex(ops_cli.OpsError, "--site takes oregon or seoul"):
            ops_cli.build_invocation(["verify", "fleet", "--site", "tokyo"], "/")
        with self.assertRaisesRegex(ops_cli.OpsError, "takes only --site, not 'seoul'"):
            ops_cli.build_invocation(["verify", "fleet", "seoul"], "/")
        with self.assertRaisesRegex(ops_cli.OpsError, "ops verify db: takes no arguments"):
            ops_cli.build_invocation(["verify", "db", "--site", "seoul"], "/")

    def test_verify_oregon_is_gone(self):
        with self.assertRaisesRegex(ops_cli.OpsError, "ops verify takes one of db, fleet"):
            ops_cli.build_invocation(["verify", "oregon"], "/")

    def test_list_names_every_table_command(self):
        list_text = "\n".join(ops_cli.list_lines())
        for one_spec in ops_cli.DISPATCH_TABLE:
            self.assertIn(f"ops {one_spec.command_name}", list_text)


class RunTest(RepoCase):
    def test_a_metrics_read_runs_the_script_with_the_prod_profile_and_logs(self):
        fake_effects = FakeEffects(stream_exit=3)
        exit_code, _, err_text = self.run_main(["metrics", "up", "--since", "1h"], fake_effects)
        self.assertEqual(exit_code, 3)
        command_argv, child_env, work_dir, echo_output = fake_effects.stream_calls[0]
        self.assertEqual(command_argv, self.script_argv(ops_cli.METRICS_SCRIPT, "up", "1h"))
        self.assertEqual(child_env["AWS_PROFILE"], "prod")
        self.assertEqual(work_dir, self.repo_root)
        self.assertTrue(echo_output)
        log_path = self.log_dir / f"ops-metrics-{STAMP_TEXT}.log"
        log_text = log_path.read_text()
        self.assertIn("# ops: env AWS_PROFILE=prod", log_text)
        self.assertIn("script output", log_text)
        self.assertIn("# ops: exit 3", log_text)
        self.assertIn(f"log {log_path}", err_text)

    def test_staging_selection_reaches_the_script(self):
        fake_effects = FakeEffects()
        self.run_main(["sql", "SELECT 1", "--env", "staging"], fake_effects)
        command_argv, child_env, _, _ = fake_effects.stream_calls[0]
        self.assertEqual(command_argv, self.script_argv(ops_cli.SQL_SCRIPT, "SELECT 1"))
        self.assertEqual(child_env["AWS_PROFILE"], "staging")
        self.assertIn(["aws", "sts", "get-caller-identity", "--output", "json", "--profile", "staging"],
                      [capture_argv for capture_argv, _ in fake_effects.capture_calls])

    def test_sql_without_docker_says_to_start_it(self):
        fake_effects = FakeEffects(docker_up=False)
        with self.assertRaisesRegex(ops_cli.OpsError, "start Docker"):
            self.run_main(["sql", "SELECT 1"], fake_effects)
        self.assertEqual(fake_effects.stream_calls, [])

    def test_an_expired_session_stops_every_aws_command_before_the_script(self):
        for argv_words in (["metrics", "up"], ["logs", "q"], ["sql", "SELECT 1"], ["tunnel", "start"],
                           ["verify", "db"], ["verify", "fleet"], ["tf", "plan", "rds"], ["whoami"]):
            fake_effects = FakeEffects(session_rows={"prod": (255, EXPIRED_STDERR)})
            with self.assertRaisesRegex(ops_cli.OpsError, "aws sso login --sso-session danteplanner"):
                self.run_main(argv_words, fake_effects)
            self.assertEqual(fake_effects.stream_calls, [], argv_words)

    def test_verify_fleet_needs_the_site_stack_initialized(self):
        (self.repo_root / "terraform" / "oregon" / ".terraform").rmdir()
        fake_effects = FakeEffects()
        with self.assertRaisesRegex(ops_cli.OpsError, "terraform/oregon is not initialized"):
            self.run_main(["verify", "fleet"], fake_effects)
        self.assertEqual(fake_effects.stream_calls, [])
        with unittest.mock.patch.dict(os.environ, {"CP_INSTANCE_ID": "i-0abc"}):
            self.run_main(["verify", "fleet"], fake_effects)
        self.assertEqual(fake_effects.stream_calls[0][0], self.script_argv(ops_cli.FLEET_VERIFY_SCRIPT, "oregon"))

    def test_verify_fleet_seoul_needs_the_seoul_stack_initialized(self):
        seoul_dir = self.repo_root / "terraform" / "seoul"
        seoul_dir.mkdir()
        (seoul_dir / "versions.tf").write_text('terraform {\n  backend "s3" {}\n}\n')
        fake_effects = FakeEffects()
        with self.assertRaisesRegex(ops_cli.OpsError, "terraform/seoul is not initialized"):
            self.run_main(["verify", "fleet", "--site", "seoul"], fake_effects)
        (seoul_dir / ".terraform").mkdir()
        self.run_main(["verify", "fleet", "--site", "seoul"], fake_effects)
        self.assertEqual(fake_effects.stream_calls[0][0], self.script_argv(ops_cli.FLEET_VERIFY_SCRIPT, "seoul"))
        self.assertTrue((self.log_dir / f"ops-verify-fleet-seoul-{STAMP_TEXT}.log").is_file())

    def test_tunnel_status_runs_without_a_session(self):
        fake_effects = FakeEffects(session_rows={"prod": (255, EXPIRED_STDERR)}, stream_exit=1)
        exit_code, _, _ = self.run_main(["tunnel", "status"], fake_effects)
        self.assertEqual(exit_code, 1)
        self.assertEqual(fake_effects.capture_calls, [])
        self.assertTrue((self.log_dir / f"ops-tunnel-status-{STAMP_TEXT}.log").is_file())

    def test_other_session_failures_name_a_retry(self):
        fake_effects = FakeEffects(session_rows={"prod": (255, "Could not connect to the endpoint URL")})
        with self.assertRaisesRegex(ops_cli.OpsError, "retry with aws sts get-caller-identity --profile prod"):
            self.run_main(["logs", "q"], fake_effects)

    def test_whoami_prints_the_identity(self):
        exit_code, out_text, _ = self.run_main(["whoami", "--env", "staging"], FakeEffects())
        self.assertEqual(exit_code, 0)
        self.assertIn("profile staging: account <account:staging> as caller-x", out_text)
        self.assertTrue((self.log_dir / f"ops-whoami-{STAMP_TEXT}.log").is_file())

    def test_whoami_hides_the_account(self):
        exit_code, out_text, err_text = self.run_main(["whoami"], FakeEffects())
        log_text = (self.log_dir / f"ops-whoami-{STAMP_TEXT}.log").read_text()
        self.assertEqual(exit_code, 0)
        self.assertIn("<account:prod>", out_text)
        self.assertIn("<account:prod>", log_text)
        self.assert_no_ids(out_text, err_text, log_text)

    def test_an_unmapped_account_prints_no_number(self):
        _, out_text, _ = self.run_main(["whoami"], FakeEffects(identity_accounts={"prod": UNMAPPED_ID}))
        self.assertIn("profile prod: account <account> as caller-x", out_text)
        self.assert_no_ids(out_text, (self.log_dir / f"ops-whoami-{STAMP_TEXT}.log").read_text())

    def test_streamed_script_output_is_redacted_on_stdout_and_in_the_log(self):
        stream_text = (f"owner {PROFILE_ACCOUNTS['prod']} role arn:aws:iam::{UNMAPPED_ID}:role/q "
                       f"bucket arn:aws:s3:::x-{PROFILE_ACCOUNTS['staging']}\n")
        _, out_text, err_text = self.run_main(["metrics", "up"], FakeEffects(stream_text=stream_text))
        log_text = (self.log_dir / f"ops-metrics-{STAMP_TEXT}.log").read_text()
        for checked_text in (out_text, log_text):
            self.assertIn("owner <account:prod> role arn:aws:iam::<account>:role/q bucket "
                          "arn:aws:s3:::x-<account:staging>", checked_text)
        self.assert_no_ids(out_text, err_text, log_text)

    def test_the_plan_text_in_the_log_is_redacted(self):
        stream_text = f"credentials resolve to {PROFILE_ACCOUNTS['prod']}\n"
        fake_effects = FakeEffects(stream_text=stream_text, stream_exit=1)
        _, out_text, err_text = self.run_main(["tf", "plan", "rds"], fake_effects)
        log_text = (self.log_dir / f"ops-tf-rds-{STAMP_TEXT}.log").read_text()
        self.assertIn("credentials resolve to <account:prod>", log_text)
        self.assertIn("credentials resolve to <account:prod>", err_text)
        self.assert_no_ids(out_text, err_text, log_text)

    def test_a_shared_id_maps_to_the_ops_profile(self):
        self.assertEqual(ops_cli.config_accounts(ops_cli.aws_config_path())[PROFILE_ACCOUNTS["prod"]], "prod")

    def test_no_config_file_redacts_only_arns(self):
        with unittest.mock.patch.dict(os.environ, {"AWS_CONFIG_FILE": str(self.repo_root / "absent")}):
            self.assertEqual(ops_cli.redact_text(f"{UNMAPPED_ID} arn:aws:iam::{UNMAPPED_ID}:role/q"),
                             f"{UNMAPPED_ID} arn:aws:iam::<account>:role/q")

    def test_log_names_never_collide(self):
        self.log_dir.mkdir(parents=True)
        (self.log_dir / f"ops-logs-{STAMP_TEXT}.log").write_text("")
        self.run_main(["logs", "q"], FakeEffects())
        self.assertTrue((self.log_dir / f"ops-logs-{STAMP_TEXT}-2.log").is_file())


class PlanTest(RepoCase):
    def plan_run(self, fixture_name, extra_words=()):
        fake_effects = FakeEffects(show_text=(FIXTURE_DIR / fixture_name).read_text())
        exit_code, out_text, err_text = self.run_main(["tf", "plan", "rds", *extra_words], fake_effects)
        return fake_effects, exit_code, out_text.splitlines(), err_text

    def test_plan_writes_its_file_beside_the_log_and_hands_off_apply(self):
        fake_effects, exit_code, out_lines, _ = self.plan_run(
            "terraform-show-mixed.json", ("--expect", "terraform_data.gone", "--expect=terraform_data.swap"))
        plan_path = self.log_dir / f"ops-tf-rds-{STAMP_TEXT}.plan"
        self.assertEqual(exit_code, 0)
        command_argv, child_env, _, echo_output = fake_effects.stream_calls[0]
        self.assertEqual(command_argv, self.script_argv(ops_cli.TERRAFORM_RUNNER, "-chdir=terraform/rds", "plan",
                                                        f"-out={plan_path}"))
        self.assertEqual(child_env["AWS_PROFILE"], "prod")
        self.assertFalse(echo_output)
        show_argv = [capture_argv for capture_argv, _ in fake_effects.capture_calls if "show" in capture_argv][0]
        self.assertEqual(show_argv, list(self.script_argv(ops_cli.TERRAFORM_RUNNER, "-chdir=terraform/rds", "show",
                                                          "-json", str(plan_path))))
        self.assertEqual(out_lines, [
            "- terraform_data.gone",
            "-/+ terraform_data.swap",
            "~ terraform_data.changed",
            "+ terraform_data.new_a",
            "+ terraform_data.new_b",
            "Plan: 3 to add, 1 to change, 2 to destroy.",
            f"! {self.repo_root / CLI_TEXT} tf apply {plan_path}",
        ])
        log_text = (self.log_dir / f"ops-tf-rds-{STAMP_TEXT}.log").read_text()
        self.assertIn("script output", log_text)
        self.assertIn("Plan: 3 to add, 1 to change, 2 to destroy.", log_text)

    def test_a_plan_with_neither_resource_nor_output_changes_prints_no_changes(self):
        _, exit_code, out_lines, _ = self.plan_run("terraform-show-no-changes.json")
        self.assertEqual(exit_code, 0)
        self.assertEqual(out_lines, ["No changes."])

    def test_an_output_only_plan_is_applicable(self):
        _, exit_code, out_lines, _ = self.plan_run("terraform-show-outputs-only.json")
        plan_path = self.log_dir / f"ops-tf-rds-{STAMP_TEXT}.plan"
        self.assertEqual(exit_code, 0)
        self.assertEqual(out_lines, [
            "Outputs only: 1 changed.",
            f"! {self.repo_root / CLI_TEXT} tf apply {plan_path}",
        ])
        self.assertEqual(json.loads(Path(f"{plan_path}.verdict.json").read_text())["verdict"], "clear")

    def test_double_dash_out_is_refused(self):
        for extra_word in ("--out", "--out=x.plan", "-out"):
            with self.assertRaisesRegex(ops_cli.OpsError, "drop -out"):
                self.run_main(["tf", "plan", "rds", "--", extra_word], FakeEffects())

    def test_detailed_exitcode_is_refused(self):
        for extra_word in ("-detailed-exitcode", "-detailed-exitcode=true", "--detailed-exitcode"):
            with self.assertRaisesRegex(ops_cli.OpsError, "owns the exit code.*drop -detailed-exitcode"):
                self.run_main(["tf", "plan", "rds", "--", extra_word], FakeEffects())

    def test_an_uninitialized_stack_names_the_init_command_before_terraform(self):
        fake_effects = FakeEffects()
        (self.repo_root / "terraform" / "rds" / ".terraform").rmdir()
        for env_words, backend_name in (((), "backend.prod.hcl"), (("--env", "staging"), "backend.staging.hcl")):
            with self.assertRaises(ops_cli.OpsError) as raised_error:
                self.run_main(["tf", "plan", "rds", *env_words], fake_effects)
            profile_name = "staging" if env_words else "prod"
            self.assertIn(f"AWS_PROFILE={profile_name} {self.repo_root / ops_cli.TERRAFORM_RUNNER} "
                          f"-chdir={self.repo_root / 'terraform' / 'rds'} init "
                          f"-backend-config={self.repo_root / 'terraform' / backend_name}",
                          str(raised_error.exception))
            self.assertIn(f"{backend_name} is not in this checkout", str(raised_error.exception))
        self.assertEqual((fake_effects.stream_calls, fake_effects.capture_calls), ([], []))

    def test_a_management_stack_defaults_to_mgmt_and_names_its_backend_file(self):
        (self.repo_root / "terraform" / "backend.management.hcl").write_text("bucket = \"x\"\n")
        with self.assertRaises(ops_cli.OpsError) as raised_error:
            self.run_main(["tf", "plan", "registry"], FakeEffects())
        self.assertIn(f"AWS_PROFILE=mgmt {self.repo_root / ops_cli.TERRAFORM_RUNNER} "
                      f"-chdir={self.repo_root / 'terraform' / 'registry'} init "
                      f"-backend-config={self.repo_root / 'terraform' / 'backend.management.hcl'}",
                      str(raised_error.exception))
        self.assertNotIn("is not in this checkout", str(raised_error.exception))

    def test_a_management_stack_plans_with_the_mgmt_profile(self):
        (self.repo_root / "terraform" / "registry" / ".terraform").mkdir()
        fake_effects = FakeEffects(show_text=(FIXTURE_DIR / "terraform-show-no-changes.json").read_text())
        self.run_main(["tf", "plan", "registry"], fake_effects)
        self.assertEqual(fake_effects.stream_calls[0][1]["AWS_PROFILE"], "mgmt")
        self.assertIn(["aws", "sts", "get-caller-identity", "--output", "json", "--profile", "mgmt"],
                      [capture_argv for capture_argv, _ in fake_effects.capture_calls])

    def test_a_management_stack_refuses_another_env(self):
        with self.assertRaisesRegex(ops_cli.OpsError, "plan it with --env mgmt or without --env"):
            self.run_main(["tf", "plan", "registry", "--env", "staging"], FakeEffects())

    def test_a_stack_whose_account_has_no_profile_is_planned_directly(self):
        fake_effects = FakeEffects()
        with self.assertRaises(ops_cli.OpsError) as raised_error:
            self.run_main(["tf", "plan", "log-archive"], fake_effects)
        self.assertIn("backend.log-archive.hcl, which has no ops profile", str(raised_error.exception))
        self.assertIn(f"AWS_PROFILE=<profile for that account> {self.repo_root / ops_cli.TERRAFORM_RUNNER} "
                      f"-chdir={self.repo_root / 'terraform' / 'log-archive'} plan", str(raised_error.exception))
        self.assertEqual((fake_effects.stream_calls, fake_effects.capture_calls), ([], []))

    def test_the_real_tree_backend_files(self):
        self.assertEqual(ops_cli.backend_file_name(REPO_ROOT, "registry"), "backend.management.hcl")
        self.assertEqual(ops_cli.backend_file_name(REPO_ROOT, "organization"), "backend.management.hcl")
        self.assertEqual(ops_cli.backend_file_name(REPO_ROOT, "log-archive"), "backend.log-archive.hcl")
        self.assertEqual(ops_cli.backend_file_name(REPO_ROOT, "oregon"), "")
        self.assertEqual(ops_cli.backend_file_name(REPO_ROOT, "cloudflare"), "")
        self.assertEqual(ops_cli.stack_info(REPO_ROOT, "registry").default_env(), "mgmt")
        self.assertTrue(ops_cli.stack_info(REPO_ROOT, "log-archive").is_unmapped)
        for stack_name in ("iam-bootstrap", "state-backend"):
            self.assertEqual(ops_cli.stack_info(REPO_ROOT, stack_name),
                             ops_cli.StackInfo(stack_name, "", True, False, True))
        for stack_name in ("oregon", "rds", "registry", "log-archive"):
            self.assertFalse(ops_cli.stack_info(REPO_ROOT, stack_name).per_account)

    def test_a_per_account_stack_needs_an_env_and_lists_the_envs_it_can_take(self):
        (self.repo_root / "terraform" / "state-backend" / ops_cli.ACCOUNT_VARS_EXAMPLE).write_text("")
        (self.repo_root / "terraform" / "state-backend" / ".terraform").mkdir()
        for backend_name in ("backend.staging.hcl", "backend.management.hcl"):
            (self.repo_root / "terraform" / backend_name).write_text("bucket = \"x\"\n")
        fake_effects = FakeEffects()
        with self.assertRaises(ops_cli.OpsError) as raised_error:
            self.run_main(["tf", "plan", "state-backend"], fake_effects)
        self.assertIn("terraform/state-backend is applied once per account, so it takes no default profile; "
                      "name the account: --env staging (backend.staging.hcl), --env mgmt (backend.management.hcl)",
                      str(raised_error.exception))
        self.assertEqual((fake_effects.stream_calls, fake_effects.capture_calls), ([], []))

    def test_a_per_account_stack_plans_with_the_env_named(self):
        (self.repo_root / "terraform" / "state-backend" / ops_cli.ACCOUNT_VARS_EXAMPLE).write_text("")
        (self.repo_root / "terraform" / "state-backend" / ".terraform").mkdir()
        fake_effects = FakeEffects(show_text=(FIXTURE_DIR / "terraform-show-no-changes.json").read_text())
        exit_code, _, _ = self.run_main(["--env", "staging", "tf", "plan", "state-backend"], fake_effects)
        self.assertEqual(exit_code, 0)
        self.assertEqual(fake_effects.stream_calls[0][1]["AWS_PROFILE"], "staging")

    def test_a_per_account_stack_names_the_mgmt_backend_file_in_its_init_command(self):
        (self.repo_root / "terraform" / "state-backend" / ops_cli.ACCOUNT_VARS_EXAMPLE).write_text("")
        with self.assertRaises(ops_cli.OpsError) as raised_error:
            self.run_main(["--env", "mgmt", "tf", "plan", "state-backend"], FakeEffects())
        self.assertIn(f"-backend-config={self.repo_root / 'terraform' / 'backend.management.hcl'}",
                      str(raised_error.exception))

    def test_imports_and_moves_are_changes(self):
        for fixture_name, expect_words, expected_lines in (
                ("terraform-show-import-move-mixed.json", ("--expect", "terraform_data.gone"), [
                    "- terraform_data.gone", "+ terraform_data.fresh", "<= terraform_data.imported",
                    "-> terraform_data.old_name to terraform_data.new_name",
                    "Plan: 1 to import, 1 to add, 0 to change, 1 to destroy."]),
                ("terraform-show-import-only.json", (), [
                    "<= terraform_data.imported", "Plan: 1 to import, 0 to add, 0 to change, 0 to destroy."]),
                ("terraform-show-move-only.json", (), [
                    "-> terraform_data.old_name to terraform_data.new_name",
                    "Plan: 0 to add, 0 to change, 0 to destroy."])):
            with self.subTest(fixture_name=fixture_name):
                for stale_file in self.log_dir.glob("*"):
                    stale_file.unlink()
                _, exit_code, out_lines, _ = self.plan_run(fixture_name, expect_words)
                self.assertEqual(exit_code, 0)
                self.assertEqual(out_lines[:-1], expected_lines)
                self.assertTrue(out_lines[-1].startswith(f"! {self.repo_root / CLI_TEXT} tf apply "))

    def test_read_actions_are_not_listed(self):
        show_data = json.loads((FIXTURE_DIR / "terraform-show-mixed.json").read_text())
        read_rows = [resource_change["address"] for resource_change in show_data["resource_changes"]
                     if resource_change["change"]["actions"] == ["read"]]
        self.assertEqual(read_rows, ["data.terraform_remote_state.peek"])
        self.assertNotIn("data.terraform_remote_state.peek",
                         "\n".join(ops_cli.plan_summary(show_data).change_lines))

    def test_create_then_delete_is_a_replace(self):
        plan_info = ops_cli.plan_summary({"resource_changes": [
            {"address": "a.b", "change": {"actions": ["create", "delete"]}}]})
        self.assertEqual(plan_info.change_lines, ("-/+ a.b",))
        self.assertEqual(plan_info.counts_line, "Plan: 1 to add, 0 to change, 1 to destroy.")
        self.assertEqual(plan_info.guarded_markers, {"a.b": "-/+ "})

    def test_extra_args_pass_through_and_out_is_refused(self):
        fake_effects, _, _, _ = self.plan_run("terraform-show-no-changes.json", ("--", "-var-file=prod.tfvars"))
        self.assertEqual(fake_effects.stream_calls[0][0][-1], "-var-file=prod.tfvars")
        with self.assertRaisesRegex(ops_cli.OpsError, "drop -out"):
            self.run_main(["tf", "plan", "rds", "--", "-out=x.plan"], FakeEffects())

    def test_a_failed_plan_prints_the_error_tail_and_the_log(self):
        fake_effects = FakeEffects(stream_exit=1)
        exit_code, out_text, err_text = self.run_main(["tf", "plan", "rds"], fake_effects)
        self.assertEqual(exit_code, 1)
        self.assertEqual(out_text, "")
        self.assertIn("script output", err_text)
        self.assertIn(str(self.log_dir / f"ops-tf-rds-{STAMP_TEXT}.log"), err_text)

    def test_unknown_stack_lists_the_stacks_with_a_backend(self):
        with self.assertRaisesRegex(ops_cli.OpsError,
                                    r"unknown stack 'nope'; .*: log-archive, oregon, rds, registry, state-backend$"):
            self.run_main(["tf", "plan", "nope"], FakeEffects())

    def test_apply_takes_one_plan_file_and_destroy_is_refused(self):
        for argv_words, message_pattern in (
                (["tf", "apply"], "takes one plan file and nothing else"),
                (["tf", "apply", "x.plan", "-auto-approve"], "takes one plan file and nothing else"),
                (["tf", "apply", "x.plan", "--", "-var-file=prod.tfvars"], "takes one plan file and nothing else"),
                (["tf", "apply", "-auto-approve"], "takes one plan file and nothing else"),
                (["tf", "destroy"], "ops tf has no destroy.*--expect <address> -- -destroy")):
            fake_effects = FakeEffects()
            with self.assertRaisesRegex(ops_cli.OpsError, message_pattern):
                self.run_main(argv_words, fake_effects)
            self.assertEqual((fake_effects.stream_calls, fake_effects.capture_calls), ([], []))

    def test_the_real_tree_stacks_are_the_ones_with_a_backend(self):
        self.assertEqual(ops_cli.plan_stacks(REPO_ROOT), ["cloudflare", "iam-bootstrap", "log-archive", "oregon",
                                                          "organization", "rds", "registry", "secrets", "seoul",
                                                          "state-backend"])
        self.assertEqual(stacks_without_s3_backend(REPO_ROOT), [])


ACCOUNT_BOUND_SHAPES = {
    "an account id": re.compile(r"(?<![0-9.])[0-9]{12}(?![0-9])"),
    "an ARN": re.compile(r"arn:aws"),
    "a resource id": re.compile(r"\b(?:vpc|subnet|sg|rtb|igw|pcx|eni|ami|i|vol)-[0-9a-f]{8,}\b"),
    "a hostname": re.compile(r"\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|net|org|io|dev|internal)\b"),
    "a long hex value": re.compile(r"\b[0-9a-f]{32,}\b"),
}


def account_bound_shapes(settings_text):
    return sorted(shape_name for shape_name, shape in ACCOUNT_BOUND_SHAPES.items() if shape.search(settings_text))


class MovedStackTest(RepoCase):
    def setUp(self):
        super().setUp()
        self.rds_dir = self.repo_root / "terraform" / "rds"
        (self.rds_dir / "variables.tf").write_text('variable "aws_account_id" {\n  type = string\n}\n')
        (self.rds_dir / "envs").mkdir()
        (self.rds_dir / "envs" / "prod.tfvars").write_text('engine_version = "8.4.11"\n')

    def moved_plan(self, argv_words):
        fake_effects = FakeEffects(show_text=(FIXTURE_DIR / "terraform-show-no-changes.json").read_text())
        exit_code, out_text, err_text = self.run_main(argv_words, fake_effects)
        stack_name = argv_words[argv_words.index("plan") + 1]
        plan_path = self.log_dir / f"ops-tf-{stack_name}-{STAMP_TEXT}.plan"
        log_text = (self.log_dir / f"ops-tf-{stack_name}-{STAMP_TEXT}.log").read_text()
        return fake_effects, exit_code, plan_path, out_text + err_text, log_text

    def assert_refused(self, argv_words, message_pattern):
        fake_effects = FakeEffects()
        with self.assertRaisesRegex(ops_cli.OpsError, message_pattern):
            self.run_main(argv_words, fake_effects)
        self.assertEqual((fake_effects.stream_calls, fake_effects.capture_calls), ([], []))

    def test_a_moved_stack_is_planned_with_its_settings_and_the_account(self):
        fake_effects, exit_code, plan_path, out_text, log_text = self.moved_plan(["tf", "plan", "rds"])
        self.assertEqual(exit_code, 0)
        self.assertEqual(fake_effects.stream_calls[0][0], self.script_argv(
            ops_cli.TERRAFORM_RUNNER, "-chdir=terraform/rds", "plan", f"-out={plan_path}",
            "-var-file=envs/prod.tfvars", "-var", "aws_account_id=111122223333"))
        self.assertIn("-var-file=envs/prod.tfvars -var aws_account_id=<account:prod>", log_text.splitlines()[0])
        self.assert_no_ids(out_text, log_text)
        show_argv = [capture_argv for capture_argv, _ in fake_effects.capture_calls if "show" in capture_argv][0]
        self.assertEqual(show_argv, list(self.script_argv(ops_cli.TERRAFORM_RUNNER, "-chdir=terraform/rds", "show",
                                                          "-json", str(plan_path))))

    def test_staging_takes_its_own_settings_file_and_account(self):
        (self.rds_dir / "envs" / "staging.tfvars").write_text('engine_version = "8.4.11"\n')
        fake_effects, _, plan_path, out_text, log_text = self.moved_plan(["--env", "staging", "tf", "plan", "rds"])
        self.assertEqual(fake_effects.stream_calls[0][0][-4:], (
            f"-out={plan_path}", "-var-file=envs/staging.tfvars", "-var", "aws_account_id=444455556666"))
        self.assertEqual(fake_effects.stream_calls[0][1]["AWS_PROFILE"], "staging")
        self.assertIn("-var-file=envs/staging.tfvars -var aws_account_id=<account:staging>", log_text)
        self.assert_no_ids(out_text, log_text)

    def test_other_terraform_arguments_follow_the_inputs(self):
        fake_effects, _, _, _, _ = self.moved_plan(
            ["tf", "plan", "rds", "--", "-refresh=false", "-target=aws_db_instance.primary"])
        self.assertEqual(fake_effects.stream_calls[0][0][-5:], (
            "-var-file=envs/prod.tfvars", "-var", "aws_account_id=111122223333", "-refresh=false",
            "-target=aws_db_instance.primary"))

    def test_a_moved_stack_refuses_every_var_file(self):
        for extra_words in (["-var-file=prod.tfvars"], ["-var-file", "prod.tfvars"], ["--var-file=prod.tfvars"],
                            ["--var-file", "prod.tfvars"], ["-refresh=false", "-var-file=prod.tfvars"]):
            with self.subTest(extra_words=extra_words):
                self.assert_refused(["tf", "plan", "rds", "--", *extra_words],
                                    "never from -var-file; edit terraform/rds/envs/prod.tfvars instead")

    def test_a_moved_stack_refuses_a_var_the_settings_file_assigns(self):
        for extra_words in (["-var", "engine_version=8.4.12"], ["-var=engine_version=8.4.12"],
                            ["--var", "engine_version=8.4.12"], ["--var=engine_version=8.4.12"],
                            ["-var", "apply_immediately=true", "-var", "engine_version=8.4.12"]):
            with self.subTest(extra_words=extra_words):
                self.assert_refused(["tf", "plan", "rds", "--", *extra_words],
                                    "-var engine_version is refused: terraform/rds/envs/prod.tfvars sets it.*"
                                    "edit that file instead")

    def test_a_moved_stack_refuses_a_var_for_the_account(self):
        for extra_words in (["-var", "aws_account_id=000000000000"], ["-var=aws_account_id=000000000000"]):
            with self.subTest(extra_words=extra_words):
                self.assert_refused(["tf", "plan", "rds", "--", *extra_words],
                                    "-var aws_account_id is refused: ops passes the account of the selected profile")

    def test_a_per_run_var_follows_the_automatic_arguments(self):
        for extra_words in (["-var", "apply_immediately=true"], ["-var=apply_immediately=true"],
                            ["--var", "apply_immediately=true"], ["-var", "no_equals_sign"]):
            with self.subTest(extra_words=extra_words):
                for stale_file in self.log_dir.glob("*"):
                    stale_file.unlink()
                fake_effects, _, _, _, _ = self.moved_plan(["tf", "plan", "rds", "--", *extra_words])
                self.assertEqual(fake_effects.stream_calls[0][0][-3 - len(extra_words):], (
                    "-var-file=envs/prod.tfvars", "-var", "aws_account_id=111122223333", *extra_words))

    def test_assigned_names_are_the_top_level_ones(self):
        settings_text = "\n".join([
            "# engine_version = \"commented\"",
            "create_vpc = true # trailing comment with { brace",
            "tags = {",
            "  Project = \"danteplanner\"",
            "  nested = [\"a\", \"}\"]",
            "}",
            "/* block_name = 1",
            "   still_block = 2 */",
            "policy = <<EOT",
            "inner_name = 3",
            "EOT",
            "subnet_ids = [",
            "  \"subnet-a\",",
            "]",
            "multi_az=false",
            "  sql_mode   = \"A,B\"",
            "// slash_comment = 4",
        ])
        self.assertEqual(ops_cli.assigned_names(settings_text),
                         {"create_vpc", "tags", "policy", "subnet_ids", "multi_az", "sql_mode"})

    def test_a_missing_settings_file_is_named(self):
        self.assert_refused(["--env", "staging", "tf", "plan", "rds"],
                            "terraform/rds/envs/staging.tfvars is missing.*--env staging.*create it")

    def test_an_empty_envs_directory_fails_every_plan(self):
        (self.rds_dir / "envs" / "prod.tfvars").unlink()
        self.assert_refused(["tf", "plan", "rds"], "terraform/rds/envs/prod.tfvars is missing")

    def test_a_profile_without_an_account_id_is_named_with_the_config_file(self):
        (self.rds_dir / "envs" / "staging.tfvars").write_text('engine_version = "8.4.11"\n')
        config_file = self.repo_root / "aws-config-no-staging-id"
        config_file.write_text(FAKE_CONFIG_TEXT.replace(f"sso_account_id = {PROFILE_ACCOUNTS['staging']}\n", ""))
        with unittest.mock.patch.dict(os.environ, {"AWS_CONFIG_FILE": str(config_file)}):
            self.assert_refused(["--env", "staging", "tf", "plan", "rds"],
                                f"profile 'staging' has no sso_account_id in {re.escape(str(config_file))}")

    def test_a_fixed_account_stack_reads_the_settings_of_its_own_env(self):
        registry_dir = self.repo_root / "terraform" / "registry"
        (registry_dir / ".terraform").mkdir()
        (registry_dir / "variables.tf").write_text('variable "aws_account_id" {}\n')
        (registry_dir / "envs").mkdir()
        (registry_dir / "envs" / "mgmt.tfvars").write_text("")
        fake_effects, _, _, _, _ = self.moved_plan(["tf", "plan", "registry"])
        self.assertEqual(fake_effects.stream_calls[0][0][-3:],
                         ("-var-file=envs/mgmt.tfvars", "-var", "aws_account_id=777788889999"))

    def test_a_moved_stack_without_the_account_variable_takes_only_its_settings(self):
        (self.repo_root / "terraform" / "oregon" / "envs").mkdir()
        (self.repo_root / "terraform" / "oregon" / "envs" / "prod.tfvars").write_text("")
        fake_effects, _, plan_path, _, _ = self.moved_plan(["tf", "plan", "oregon"])
        self.assertEqual(fake_effects.stream_calls[0][0][-2:], (f"-out={plan_path}", "-var-file=envs/prod.tfvars"))

    def test_a_stack_that_is_not_moved_is_planned_as_before(self):
        fake_effects, _, plan_path, _, _ = self.moved_plan(["tf", "plan", "oregon", "--", "-var-file=prod.tfvars"])
        self.assertEqual(fake_effects.stream_calls[0][0], self.script_argv(
            ops_cli.TERRAFORM_RUNNER, "-chdir=terraform/oregon", "plan", f"-out={plan_path}", "-var-file=prod.tfvars"))

    def test_apply_takes_no_variable(self):
        _, _, plan_path, _, _ = self.moved_plan(["tf", "plan", "rds"])
        fake_effects = FakeEffects()
        self.run_main(["tf", "apply", str(plan_path)], fake_effects)
        self.assertEqual(fake_effects.stream_calls[0][0], self.script_argv(
            ops_cli.TERRAFORM_RUNNER, "-chdir=terraform/rds", "apply", str(plan_path)))


class RealSettingsTest(unittest.TestCase):
    def test_each_account_bound_shape_is_recognized(self):
        samples = {
            "an account id": 'aws_account_id = "123456789012"',
            "an ARN": 'role = "arn:aws:iam::role/x"',
            "a resource id": 'vpc_id = "vpc-0abc1234def567890"',
            "a hostname": 'host = "db.example.com"',
            "a long hex value": 'zone = "0123456789abcdef0123456789abcdef"',
        }
        for shape_name, sample_text in samples.items():
            with self.subTest(shape_name=shape_name):
                self.assertIn(shape_name, account_bound_shapes(sample_text))
        self.assertEqual(account_bound_shapes('engine_version = "8.4.11"\nmulti_az = false\ncidr = "10.30.0.0/16"\n'), [])

    def test_no_tracked_settings_file_holds_an_account_bound_value(self):
        settings_paths = sorted((REPO_ROOT / "terraform").glob("*/envs/*.tfvars"))
        self.assertTrue(settings_paths)
        for settings_path in settings_paths:
            with self.subTest(settings_path=str(settings_path.relative_to(REPO_ROOT))):
                self.assertEqual(account_bound_shapes(settings_path.read_text()), [])

    def test_the_real_rds_stack_is_moved_and_takes_the_account(self):
        rds_stack = ops_cli.stack_info(REPO_ROOT, "rds")
        self.assertEqual((rds_stack.is_moved, rds_stack.takes_account), (True, True))
        self.assertFalse(ops_cli.stack_info(REPO_ROOT, "oregon").is_moved)

    def test_settings_files_are_tracked_and_other_tfvars_stay_ignored(self):
        for relative_path, ignore_exit in (("terraform/rds/envs/prod.tfvars", 1), ("terraform/oregon/envs/prod.tfvars", 1),
                                           ("terraform/rds/prod.tfvars", 0), ("terraform/rds/envs/x/prod.tfvars", 0),
                                           ("terraform/rds/environment.tfvars.example", 1)):
            with self.subTest(relative_path=relative_path):
                ignore_run = subprocess.run(["git", "-C", str(REPO_ROOT), "check-ignore", "--no-index", "-q",
                                             relative_path], capture_output=True, text=True,
                                            env=ops_cli.git_environment())
                self.assertEqual(ignore_run.returncode, ignore_exit)


CHECK_MESSAGE = ("The cp instance was built from an older boot script than the code holds. It takes the current one "
                 "when node_generation.cp is raised, which replaces it.")


class SeoulCase(RepoCase):
    def setUp(self):
        super().setUp()
        seoul_dir = self.repo_root / "terraform" / "seoul"
        seoul_dir.mkdir()
        (seoul_dir / "versions.tf").write_text('terraform {\n  backend "s3" {}\n}\n')
        (seoul_dir / ".terraform").mkdir()

    def guarded_plan(self, stack_name, fixture_name, *plan_words, show_text=None):
        fake_effects = FakeEffects(show_text=show_text or (FIXTURE_DIR / fixture_name).read_text())
        exit_code, out_text, err_text = self.run_main(["tf", "plan", stack_name, *plan_words], fake_effects)
        plan_path = self.log_dir / f"ops-tf-{stack_name}-{STAMP_TEXT}.plan"
        verdict_data = json.loads(Path(f"{plan_path}.verdict.json").read_text())
        return fake_effects, exit_code, out_text.splitlines(), verdict_data, plan_path


FORGOTTEN_ADDRESSES = ("aws_s3_bucket.tf_state[0]", "aws_s3_bucket_lifecycle_configuration.tf_state[0]",
                       "aws_s3_bucket_public_access_block.tf_state[0]",
                       "aws_s3_bucket_server_side_encryption_configuration.tf_state[0]",
                       "aws_s3_bucket_versioning.tf_state[0]")
FORGET_LINES = [f"forget {one_address}" for one_address in FORGOTTEN_ADDRESSES]
FORGET_EXPECTATIONS = tuple(word for one_address in FORGOTTEN_ADDRESSES for word in ("--expect", one_address))


class ForgetGuardTest(SeoulCase):
    def test_an_unexpected_forget_blocks_the_plan(self):
        _, exit_code, out_lines, verdict_data, _ = self.guarded_plan("seoul", "terraform-show-forget-update.json")
        self.assertEqual(out_lines, [
            "~ aws_iam_policy.provisioning", *FORGET_LINES,
            "Plan: 0 to add, 1 to change, 0 to destroy, 5 to forget.",
            *[f"UNEXPECTED {one_line}" for one_line in FORGET_LINES], ops_cli.BLOCKED_LINE])
        self.assertEqual((verdict_data["verdict"], verdict_data["guarded"]), ("blocked", list(FORGOTTEN_ADDRESSES)))
        self.assertEqual(exit_code, 3)

    def test_every_forget_expected_clears_the_plan(self):
        _, exit_code, out_lines, verdict_data, plan_path = self.guarded_plan(
            "seoul", "terraform-show-forget-update.json", *FORGET_EXPECTATIONS)
        self.assertEqual(out_lines, [
            "~ aws_iam_policy.provisioning", *FORGET_LINES,
            "Plan: 0 to add, 1 to change, 0 to destroy, 5 to forget.",
            f"! {self.repo_root / CLI_TEXT} tf apply {plan_path}"])
        self.assertEqual((verdict_data["verdict"], verdict_data["expected"]), ("clear", list(FORGOTTEN_ADDRESSES)))
        self.assertEqual(exit_code, 0)

    def test_an_expected_forget_the_plan_lacks_is_absent(self):
        _, exit_code, out_lines, verdict_data, _ = self.guarded_plan(
            "seoul", "terraform-show-forget-update.json", *FORGET_EXPECTATIONS, "--expect", "aws_s3_bucket.other[0]")
        self.assertEqual(out_lines[-2:], ["ABSENT aws_s3_bucket.other[0]", ops_cli.BLOCKED_LINE])
        self.assertFalse(any(one_line.startswith("UNEXPECTED") for one_line in out_lines))
        self.assertEqual(verdict_data["verdict"], "blocked")
        self.assertEqual(exit_code, 3)

    def test_a_forget_only_plan_prints_its_apply_command_once_expected(self):
        show_data = json.loads((FIXTURE_DIR / "terraform-show-forget-update.json").read_text())
        show_data["resource_changes"] = [resource_change for resource_change in show_data["resource_changes"]
                                         if resource_change["change"]["actions"] == ["forget"]]
        counts_line = "Plan: 0 to add, 0 to change, 0 to destroy, 5 to forget."
        _, exit_code, out_lines, _, plan_path = self.guarded_plan(
            "seoul", "", *FORGET_EXPECTATIONS, show_text=json.dumps(show_data))
        self.assertEqual(out_lines, [*FORGET_LINES, counts_line, f"! {self.repo_root / CLI_TEXT} tf apply {plan_path}"])
        self.assertEqual(exit_code, 0)
        for stale_file in self.log_dir.glob("*"):
            stale_file.unlink()
        _, exit_code, out_lines, _, _ = self.guarded_plan("seoul", "", show_text=json.dumps(show_data))
        self.assertEqual(out_lines, [*FORGET_LINES, counts_line,
                                     *[f"UNEXPECTED {one_line}" for one_line in FORGET_LINES], ops_cli.BLOCKED_LINE])
        self.assertEqual(exit_code, 3)


class GuardTest(SeoulCase):
    def test_a_plan_with_no_guarded_change_is_clear(self):
        _, exit_code, out_lines, verdict_data, plan_path = self.guarded_plan("oregon", "terraform-show-guard-clear.json")
        self.assertEqual(out_lines, [
            "~ module.fleet.aws_launch_template.app",
            "+ aws_iam_role.new_a",
            "+ aws_iam_role_policy.new_b",
            "Plan: 2 to add, 1 to change, 0 to destroy.",
            f"! {self.repo_root}/.claude/skills/ops/bin/ops tf apply {plan_path}",
        ])
        self.assertEqual(verdict_data, {"stack": "oregon", "env": "prod", "verdict": "clear", "expected": [],
                                        "guarded": [], "plan_sha256": ops_cli.hashlib.sha256(PLAN_BYTES).hexdigest()})
        self.assertEqual(exit_code, 0)

    def test_an_unexpected_replacement_blocks_the_plan(self):
        _, exit_code, out_lines, verdict_data, _ = self.guarded_plan("seoul", "terraform-show-guard-replace.json")
        self.assertEqual(out_lines, [
            "-/+ module.fleet.aws_instance.cp",
            "~ aws_iam_policy.app_node",
            "Plan: 1 to add, 1 to change, 1 to destroy.",
            "UNEXPECTED -/+ module.fleet.aws_instance.cp",
            "blocked: name each change you mean with --expect <address>, before the -- ; no apply command is printed",
        ])
        self.assertFalse(any(one_line.startswith("!") for one_line in out_lines))
        self.assertEqual((verdict_data["verdict"], verdict_data["guarded"]),
                         ("blocked", ["module.fleet.aws_instance.cp"]))
        self.assertTrue((self.log_dir / f"ops-tf-seoul-{STAMP_TEXT}.plan").is_file())
        self.assertEqual(exit_code, 3)

    def test_an_expected_replacement_is_clear(self):
        _, exit_code, out_lines, verdict_data, plan_path = self.guarded_plan(
            "seoul", "terraform-show-guard-replace.json", "--expect", "module.fleet.aws_instance.cp")
        self.assertEqual(out_lines[-1], f"! {self.repo_root / CLI_TEXT} tf apply {plan_path}")
        self.assertEqual((verdict_data["verdict"], verdict_data["expected"]),
                         ("clear", ["module.fleet.aws_instance.cp"]))
        self.assertEqual(exit_code, 0)

    def test_an_expectation_the_plan_does_not_carry_blocks_it(self):
        _, exit_code, out_lines, verdict_data, _ = self.guarded_plan(
            "seoul", "terraform-show-create-only.json", "--expect", "module.fleet.aws_instance.cp")
        self.assertIn("ABSENT module.fleet.aws_instance.cp", out_lines)
        self.assertFalse(any(one_line.startswith("!") for one_line in out_lines))
        self.assertEqual(verdict_data["verdict"], "blocked")
        self.assertEqual(exit_code, 3)

    def test_an_expectation_on_a_plan_without_changes_blocks_it(self):
        _, exit_code, out_lines, _, _ = self.guarded_plan(
            "seoul", "terraform-show-no-changes.json", "--expect", "module.fleet.aws_instance.cp")
        self.assertEqual(out_lines, ["No changes.", "ABSENT module.fleet.aws_instance.cp", ops_cli.BLOCKED_LINE])
        self.assertEqual(exit_code, 3)

    def test_a_destroy_and_an_instance_update_are_guarded_other_updates_are_not(self):
        _, exit_code, out_lines, verdict_data, _ = self.guarded_plan(
            "oregon", "terraform-show-guard-destroy-update.json")
        unexpected_lines = [one_line for one_line in out_lines if one_line.startswith("UNEXPECTED")]
        self.assertEqual(unexpected_lines, ["UNEXPECTED - aws_iam_role_policy.old",
                                            "UNEXPECTED ~ module.fleet.aws_instance.data"])
        self.assertNotIn("aws_s3_bucket.logs", "\n".join(unexpected_lines))
        self.assertEqual(verdict_data["guarded"], ["aws_iam_role_policy.old", "module.fleet.aws_instance.data"])
        self.assertEqual(exit_code, 3)

    def test_a_failed_check_is_in_the_summary(self):
        _, exit_code, out_lines, verdict_data, plan_path = self.guarded_plan(
            "seoul", "terraform-show-failed-check.json")
        self.assertEqual(out_lines, [
            f"WARN {CHECK_MESSAGE}",
            "+ aws_iam_role.new_a",
            "Plan: 1 to add, 0 to change, 0 to destroy.",
            f"! {self.repo_root / CLI_TEXT} tf apply {plan_path}",
        ])
        self.assertEqual((verdict_data["verdict"], exit_code), ("clear", 0))

    def test_the_real_plans_failed_check_is_read(self):
        _, exit_code, out_lines, verdict_data, _ = self.guarded_plan("seoul", "terraform-show-real-checks.json")
        self.assertEqual(out_lines, [f"WARN {CHECK_MESSAGE}", "No changes."])
        self.assertEqual((verdict_data["verdict"], exit_code), ("clear", 0))

    def test_an_errored_check_without_problems_names_its_address_and_unknown_prints_nothing(self):
        def one_check(check_name, check_status):
            check_address = {"kind": "check", "name": check_name, "to_display": f"check.{check_name}"}
            return {"address": check_address, "status": check_status,
                    "instances": [{"address": {"to_display": f"check.{check_name}"}, "status": check_status}]}
        show_data = {"checks": [one_check("errored", "error"), one_check("pending", "unknown"),
                                one_check("fine", "pass"), one_check("failed_silently", "fail")]}
        self.assertEqual(ops_cli.plan_summary(show_data).check_messages,
                         ("check.errored error", "check.failed_silently fail"))

    def test_an_update_of_an_autoscaling_group_or_a_database_instance_is_guarded(self):
        _, exit_code, out_lines, verdict_data, _ = self.guarded_plan(
            "oregon", "terraform-show-guard-fleet-updates.json")
        self.assertEqual([one_line for one_line in out_lines if one_line.startswith("UNEXPECTED")],
                         ["UNEXPECTED ~ aws_db_instance.primary", "UNEXPECTED ~ module.fleet.aws_autoscaling_group.app"])
        self.assertEqual(verdict_data["guarded"], ["aws_db_instance.primary", "module.fleet.aws_autoscaling_group.app"])
        self.assertEqual(exit_code, 3)

    def test_expect_after_the_double_dash_belongs_to_terraform(self):
        fake_effects, exit_code, _, verdict_data, _ = self.guarded_plan(
            "seoul", "terraform-show-guard-replace.json", "--", "--expect", "module.fleet.aws_instance.cp")
        self.assertEqual(fake_effects.stream_calls[0][0][-2:], ("--expect", "module.fleet.aws_instance.cp"))
        self.assertEqual((verdict_data["expected"], verdict_data["verdict"], exit_code), ([], "blocked", 3))

    def test_expect_without_a_value_or_twice_is_an_ops_error(self):
        for plan_words, message_pattern in (
                (["--expect"], "--expect needs the address of a change you mean"),
                (["--expect", "--", "-var-file=x"], "--expect needs the address"),
                (["--expect="], "--expect needs the address"),
                (["--expect", "a.b", "--expect=a.b"], "--expect a.b is given twice; name each address once")):
            fake_effects = FakeEffects()
            with self.assertRaisesRegex(ops_cli.OpsError, message_pattern):
                self.run_main(["tf", "plan", "seoul", *plan_words], fake_effects)
            self.assertEqual(fake_effects.stream_calls, [])

    def test_a_moved_and_updated_instance_is_guarded_by_its_current_address(self):
        plan_info = ops_cli.plan_summary({"resource_changes": [
            {"address": "module.fleet.aws_instance.cp_next", "previous_address": "module.fleet.aws_instance.cp",
             "type": "aws_instance", "change": {"actions": ["update"]}}]})
        self.assertEqual(plan_info.guarded_markers, {"module.fleet.aws_instance.cp_next": "~ "})

    def test_an_address_holding_an_account_id_is_written_and_compared_redacted(self):
        show_text = json.dumps({"resource_changes": [
            {"address": f'aws_s3_bucket.x["logs-{PROFILE_ACCOUNTS["prod"]}"]', "type": "aws_s3_bucket",
             "change": {"actions": ["delete"]}}]})
        for expect_text in ('aws_s3_bucket.x["logs-<account:prod>"]',
                            f'aws_s3_bucket.x["logs-{PROFILE_ACCOUNTS["prod"]}"]'):
            with self.subTest(expect_text=expect_text):
                for stale_file in self.log_dir.glob("*"):
                    stale_file.unlink()
                _, exit_code, out_lines, verdict_data, plan_path = self.guarded_plan(
                    "seoul", "", "--expect", expect_text, show_text=show_text)
                self.assertEqual(verdict_data["guarded"], ['aws_s3_bucket.x["logs-<account:prod>"]'])
                self.assertEqual(verdict_data["expected"], ['aws_s3_bucket.x["logs-<account:prod>"]'])
                self.assertEqual(exit_code, 0)
                self.assert_no_ids(Path(f"{plan_path}.verdict.json").read_text(), "\n".join(out_lines))


class ApplyTest(SeoulCase):
    def cleared_plan(self, fixture_name="terraform-show-guard-replace.json",
                     plan_words=("--expect", "module.fleet.aws_instance.cp")):
        _, exit_code, _, _, plan_path = self.guarded_plan("seoul", fixture_name, *plan_words)
        return exit_code, plan_path

    def test_apply_runs_a_clear_plan_through_the_runner_redacted_and_logged(self):
        plan_exit, plan_path = self.cleared_plan()
        self.assertEqual(plan_exit, 0)
        stream_text = f"aws_instance.cp: Creation complete in account {PROFILE_ACCOUNTS['prod']}\n"
        fake_effects = FakeEffects(stream_text=stream_text, stream_exit=4)
        exit_code, out_text, err_text = self.run_main(["tf", "apply", str(plan_path)], fake_effects)
        self.assertEqual(exit_code, 4)
        command_argv, child_env, work_dir, echo_output = fake_effects.stream_calls[0]
        self.assertEqual(command_argv, self.script_argv(ops_cli.TERRAFORM_RUNNER, "-chdir=terraform/seoul", "apply",
                                                        str(plan_path)))
        self.assertEqual((child_env["AWS_PROFILE"], work_dir, echo_output), ("prod", self.repo_root, True))
        self.assertEqual(fake_effects.capture_calls[0][0],
                         ["aws", "sts", "get-caller-identity", "--output", "json", "--profile", "prod"])
        log_path = self.log_dir / f"ops-tf-apply-seoul-{STAMP_TEXT}.log"
        log_text = log_path.read_text()
        for checked_text in (out_text, log_text):
            self.assertIn("Creation complete in account <account:prod>", checked_text)
        self.assertIn(f"log {log_path}", err_text)
        self.assert_no_ids(out_text, err_text, log_text)

    def test_apply_takes_the_plan_file_by_a_relative_path(self):
        _, plan_path = self.cleared_plan()
        fake_effects = FakeEffects()
        exit_code, _, _ = self.run_main(["tf", "apply", str(plan_path.relative_to(self.repo_root))], fake_effects)
        self.assertEqual(exit_code, 0)
        self.assertEqual(fake_effects.stream_calls[0][0][-1], str(plan_path))

    def test_apply_refuses_a_plan_file_without_a_verdict_file(self):
        bare_plan = self.repo_root / "bare.plan"
        bare_plan.write_bytes(PLAN_BYTES)
        fake_effects = FakeEffects()
        with self.assertRaisesRegex(ops_cli.OpsError, r"is not a plan ops produced.*ops tf plan <stack>"):
            self.run_main(["tf", "apply", str(bare_plan)], fake_effects)
        self.assertEqual(fake_effects.stream_calls, [])

    def test_apply_refuses_a_blocked_verdict_and_names_its_addresses(self):
        _, plan_path = self.cleared_plan("terraform-show-guard-destroy-update.json", ("--expect", "aws_absent.x"))
        fake_effects = FakeEffects()
        with self.assertRaises(ops_cli.OpsError) as raised_error:
            self.run_main(["tf", "apply", str(plan_path)], fake_effects)
        for named_text in ("UNEXPECTED aws_iam_role_policy.old", "UNEXPECTED module.fleet.aws_instance.data",
                           "ABSENT aws_absent.x", "the guard blocked"):
            self.assertIn(named_text, str(raised_error.exception))
        self.assertEqual(fake_effects.stream_calls, [])

    def test_apply_refuses_a_plan_file_changed_after_it_was_judged(self):
        _, plan_path = self.cleared_plan()
        plan_path.write_bytes(PLAN_BYTES + b"changed")
        fake_effects = FakeEffects()
        with self.assertRaisesRegex(ops_cli.OpsError, "changed after it was judged"):
            self.run_main(["tf", "apply", str(plan_path)], fake_effects)
        self.assertEqual(fake_effects.stream_calls, [])

    def test_apply_refuses_an_env_that_disagrees_with_the_verdict(self):
        _, plan_path = self.cleared_plan()
        fake_effects = FakeEffects()
        with self.assertRaisesRegex(ops_cli.OpsError, "made with --env prod.*drop --env staging"):
            self.run_main(["--env", "staging", "tf", "apply", str(plan_path)], fake_effects)
        self.assertEqual(fake_effects.stream_calls, [])
        self.run_main(["tf", "apply", str(plan_path), "--env", "prod"], fake_effects)
        self.assertEqual(fake_effects.stream_calls[0][1]["AWS_PROFILE"], "prod")

    def test_apply_needs_the_stack_initialized(self):
        _, plan_path = self.cleared_plan()
        (self.repo_root / "terraform" / "seoul" / ".terraform").rmdir()
        fake_effects = FakeEffects()
        with self.assertRaisesRegex(ops_cli.OpsError, "terraform/seoul is not initialized"):
            self.run_main(["tf", "apply", str(plan_path)], fake_effects)
        self.assertEqual((fake_effects.stream_calls, fake_effects.capture_calls), ([], []))


class DoctorTest(RepoCase):
    def test_doctor_reports_each_item_and_the_next_action(self):
        fake_effects = FakeEffects(session_rows={"prod": (255, EXPIRED_STDERR)}, docker_up=False, process_list=[
            (4242, plugin_argv("3306", "danteplanner-mysql.abc123.us-west-2.rds.amazonaws.com", "us-west-2")),
            (4241, ("aws", "ssm", "start-session", "--region", "us-west-2")),
        ])
        exit_code, out_text, _ = self.run_main(["doctor"], fake_effects)
        out_lines = out_text.splitlines()
        self.assertEqual(exit_code, 1)
        self.assertIn("sso      EXPIRED prod (profile prod): the SSO session for profile 'prod' has expired; run: "
                      "aws sso login --sso-session danteplanner", out_lines)
        self.assertIn("sso      ok staging (profile staging): account <account:staging> as caller-x", out_lines)
        self.assert_no_ids(out_text)
        self.assertTrue(any(one_line.startswith("docker   UNREACHABLE") and "start Docker" in one_line
                            for one_line in out_lines))
        self.assertIn("tunnel   open 127.0.0.1:3306 -> danteplanner-mysql.abc123.us-west-2.rds.amazonaws.com "
                      f"(us-west-2, pid 4242); close: {CLI_TEXT} tunnel stop", out_lines)
        self.assertIn("2 problem(s); each line above names its fix", out_lines)

    def test_a_healthy_workstation_passes(self):
        exit_code, out_text, _ = self.run_main(["doctor"], FakeEffects())
        self.assertEqual(exit_code, 0)
        self.assertIn("tunnel   none open", out_text.splitlines())
        self.assertIn("image    ok pulled mysql:8@sha256:", out_text)

    def test_missing_tools_name_the_install(self):
        exit_code, out_text, _ = self.run_main(["doctor"], FakeEffects(tool_paths={}))
        self.assertEqual(exit_code, 1)
        self.assertIn("tool     MISSING jq; the access scripts parse JSON with it; install jq", out_text)

    def test_tunnels_are_read_from_the_plugin_argv(self):
        tunnel_rows = ops_cli.open_tunnels([
            (10, plugin_argv("3307", "danteplanner-mysql-seoul.x.ap-northeast-2.rds.amazonaws.com", "ap-northeast-2")),
            (11, ("session-manager-plugin", "--version")),
            (12, ("/usr/bin/vim", "notes.txt")),
        ])
        self.assertEqual(tunnel_rows, [ops_cli.OpenTunnel(
            10, "3307", "danteplanner-mysql-seoul.x.ap-northeast-2.rds.amazonaws.com", "ap-northeast-2")])



class ChecksMissingTest(unittest.TestCase):
    def setUp(self):
        self.temp_handle = tempfile.TemporaryDirectory()
        self.bare_cli = Path(self.temp_handle.name) / ".claude" / "skills" / "ops" / "bin" / "ops"
        self.bare_cli.parent.mkdir(parents=True)
        shutil.copy(CLI_FILE, self.bare_cli)

    def tearDown(self):
        self.temp_handle.cleanup()

    def run_bare(self, *cli_words, stdin_text=""):
        return subprocess.run([sys.executable, "-I", str(self.bare_cli), *cli_words], input=stdin_text,
                              capture_output=True, text=True)

    def test_commands_other_than_hook_never_load_the_checks_cli(self):
        list_run = self.run_bare("list")
        self.assertEqual((list_run.returncode, list_run.stderr), (0, ""))
        self.assertIn("ops tf plan", list_run.stdout)
        self.assertEqual(self.run_bare("help").returncode, 0)

    def hook_input(self, command_text):
        return json.dumps({"tool_name": "Bash", "tool_input": {"command": command_text}})

    def assert_fail_closed(self):
        for command_text in ("scripts/ops/access/rds-query.sh 'SELECT 1'",
                             "scripts/ops/terraform-run.sh -chdir=terraform/rds plan"):
            hook_run = self.run_bare("hook", stdin_text=self.hook_input(command_text))
            self.assertEqual(hook_run.returncode, 2, command_text)
            self.assertIn(f"git checkout -- {ops_cli.CHECKS_CLI_PATH}", hook_run.stderr)
        hook_run = self.run_bare("hook", stdin_text=self.hook_input(f"{CLI_TEXT} tf apply x.plan"))
        self.assertEqual(hook_run.returncode, 2)
        self.assertIn("together with the word 'apply'", hook_run.stderr)
        hook_run = self.run_bare("hook", stdin_text=self.hook_input("git status && ls scripts"))
        self.assertEqual((hook_run.returncode, hook_run.stdout, hook_run.stderr), (0, "", ""))

    def test_without_the_checks_cli_the_hook_refuses_only_wrapped_names(self):
        self.assert_fail_closed()

    def test_with_a_broken_checks_cli_the_hook_refuses_only_wrapped_names(self):
        checks_stub = Path(self.temp_handle.name) / ops_cli.CHECKS_CLI_PATH
        checks_stub.parent.mkdir(parents=True)
        checks_text = (REPO_ROOT / ops_cli.CHECKS_CLI_PATH).read_text()
        self.assertIn("def nested_scripts(", checks_text)
        checks_stub.write_text(checks_text.replace("def nested_scripts(", "def nested_scripts_gone("))
        self.assert_fail_closed()


class SystemEffectsTest(unittest.TestCase):
    def test_the_real_seam_runs_local_commands(self):
        system_effects = ops_cli.SystemEffects()
        self.assertTrue(system_effects.tool_path("sh"))
        self.assertEqual(system_effects.tool_path("no-such-tool-for-ops"), "")
        self.assertEqual(system_effects.run_captured(["sh", "-c", "echo seen"]).stdout, "seen\n")
        with tempfile.TemporaryFile("w+", encoding="utf-8") as log_file, \
                contextlib.redirect_stdout(io.StringIO()) as out_buffer:
            exit_code = system_effects.run_streamed(["sh", "-c", "echo streamed; exit 4"], dict(os.environ), "/",
                                                    log_file, True)
            log_file.seek(0)
            self.assertEqual(log_file.read(), "streamed\n")
        self.assertEqual(exit_code, 4)
        self.assertEqual(out_buffer.getvalue(), "streamed\n")
        own_rows = [argv_words for process_id, argv_words in system_effects.process_rows() if process_id == os.getpid()]
        self.assertEqual(len(own_rows), 1)

HOOK_CASES = (
    ("scripts/ops/access/logs-query.sh '{app=\"backend\"}' 1h",
     f"{CLI_TEXT} logs '{{app=\"backend\"}}' --since 1h"),
    ("AWS_PROFILE=prod scripts/ops/access/rds-query.sh \"SELECT 1\"", f"{CLI_TEXT} sql 'SELECT 1'"),
    ("/abs/path/scripts/ops/access/metrics-query.sh up", f"{CLI_TEXT} metrics up"),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds plan", f"{CLI_TEXT} tf plan rds"),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds plan -var-file=prod.tfvars -refresh=false",
     f"{CLI_TEXT} tf plan rds -- -refresh=false"),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds plan -var-file prod.tfvars", f"{CLI_TEXT} tf plan rds"),
    ("scripts/ops/terraform-run.sh -chdir=terraform/oregon plan -var-file=prod.tfvars -refresh=false",
     f"{CLI_TEXT} tf plan oregon -- -var-file=prod.tfvars -refresh=false"),
    ("AWS_PROFILE=staging scripts/ops/terraform-run.sh -chdir=terraform/secrets plan -var-file=staging.tfvars",
     f"{CLI_TEXT} --env staging tf plan secrets -- -var-file=staging.tfvars"),
    ("scripts/ops/access/metrics-query.sh 'rate(x[5m])' 6h 1m",
     f"{CLI_TEXT} metrics 'rate(x[5m])' --since 6h --step 1m"),
    ("scripts/ops/access/logs-query.sh --day 2026-07-23 '{app=\"backend\"}' 9",
     f"{CLI_TEXT} logs --day 2026-07-23 '{{app=\"backend\"}}' --limit 9"),
    ("scripts/ops/access/rds-query.sh -f q.sql seoul", f"{CLI_TEXT} sql -f q.sql --site seoul"),
    ("scripts/ops/access/rds-tunnel.sh start seoul", f"{CLI_TEXT} tunnel start --site seoul"),
    ("AWS_PROFILE=prod scripts/ops/access/db-observability-check.sh", f"{CLI_TEXT} verify db"),
    ("scripts/ops/fleet-verify.sh", f"{CLI_TEXT} verify fleet"),
    ("scripts/ops/fleet-verify.sh oregon", f"{CLI_TEXT} verify fleet"),
    ("AWS_PROFILE=staging scripts/ops/fleet-verify.sh seoul", f"{CLI_TEXT} --env staging verify fleet --site seoul"),
    ("bash scripts/ops/access/rds-tunnel.sh status", f"{CLI_TEXT} tunnel status"),
    ("bash -c 'scripts/ops/access/rds-tunnel.sh stop'", f"{CLI_TEXT} tunnel stop"),
    ("echo $(scripts/ops/access/rds-query.sh 'SELECT 1') > out.txt", f"{CLI_TEXT} sql 'SELECT 1'"),
    ("timeout 60 scripts/ops/access/logs-query.sh q", f"{CLI_TEXT} logs q"),
    (".claude/skills/ops/bin/ops logs '{app=\"backend\"}'", None),
    (".claude/skills/ops/bin/ops sql \"SELECT 'scripts/ops/access/rds-query.sh'\"", None),
    ("/home/u/repo/.claude/worktrees/x/.claude/skills/ops/bin/ops tf plan rds", None),
    ("AWS_PROFILE=mgmt scripts/ops/terraform-run.sh -chdir=terraform/registry plan", f"{CLI_TEXT} tf plan registry"),
    ("scripts/ops/terraform-run.sh -chdir=terraform/registry plan", f"{CLI_TEXT} tf plan registry"),
    ("AWS_PROFILE=prod scripts/ops/terraform-run.sh -chdir=terraform/registry plan",
     f"{CLI_TEXT} --env prod tf plan registry"),
    ("AWS_PROFILE=mgmt scripts/ops/terraform-run.sh -chdir=terraform/oregon plan",
     f"{CLI_TEXT} --env mgmt tf plan oregon"),
    ("scripts/ops/terraform-run.sh -chdir=terraform/log-archive plan", None),
    ("scripts/ops/terraform-run.sh -chdir=terraform/state-backend plan",
     f"{CLI_TEXT} --env '<env>' tf plan state-backend"),
    ("AWS_PROFILE=staging scripts/ops/terraform-run.sh -chdir=terraform/iam-bootstrap plan",
     f"{CLI_TEXT} --env staging tf plan iam-bootstrap"),
    ("AWS_PROFILE=prod scripts/ops/terraform-run.sh -chdir=terraform/iam-bootstrap plan",
     f"{CLI_TEXT} --env prod tf plan iam-bootstrap"),
    ("AWS_PROFILE=prod-provisioner scripts/ops/access/rds-query.sh 'SELECT 1'", None),
    ("AWS_PROFILE=prod-provisioner scripts/ops/terraform-run.sh -chdir=terraform/rds plan", None),
    ("AWS_PROFILE=staging scripts/ops/access/rds-query.sh 'SELECT 1'", f"{CLI_TEXT} --env staging sql 'SELECT 1'"),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds plan --out=x.plan", f"{CLI_TEXT} tf plan rds"),
    ("echo `scripts/ops/access/rds-query.sh 'SELECT 1'`", f"{CLI_TEXT} sql 'SELECT 1'"),
    ("source scripts/ops/access/rds-tunnel.sh start", f"{CLI_TEXT} tunnel start"),
    (". scripts/ops/access/rds-tunnel.sh stop", f"{CLI_TEXT} tunnel stop"),
    ("bash <<EOF\nscripts/ops/access/rds-tunnel.sh status\nEOF", f"{CLI_TEXT} tunnel status"),
    ("sh <<'EOF'\nscripts/ops/fleet-verify.sh seoul\nEOF", f"{CLI_TEXT} verify fleet --site seoul"),
    ("cat <<EOF | bash\nscripts/ops/access/rds-tunnel.sh status\nEOF", f"{CLI_TEXT} tunnel status"),
    ("git commit -F - <<'EOF'\nscripts/ops/access/rds-tunnel.sh status\nEOF", None),
    ("scripts//ops/access/rds-tunnel.sh status", f"{CLI_TEXT} tunnel status"),
    ("./scripts/ops/./access/rds-tunnel.sh status", f"{CLI_TEXT} tunnel status"),
    ("scripts/ops/terraform-run.sh -chdir=terraform//rds/ plan", f"{CLI_TEXT} tf plan rds"),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds init -reconfigure", None),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds fmt", None),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds validate", None),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds workspace list", None),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds state list", None),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds show x.plan", None),
    (".claude/skills/ops/bin/ops tf plan seoul --expect module.fleet.aws_instance.cp", None),
    ("scripts/ops/provision/grafana-secrets.sh", None),
    ("scripts/ops/fleet-verify.sh --kubeconfig seoul", None),
    ("rg -n 'access/rds-query.sh' docs", None),
    ("cat scripts/ops/access/rds-query.sh", None),
    ("scripts/ops/access/staging-env.sh", None),
)


ABS_CLI = "/home/u/repo/.claude/worktrees/x/.claude/skills/ops/bin/ops"
RUNNER_USER_TEXT = f"! {CLI_TEXT} tf apply <plan file>, for a plan file {CLI_TEXT} tf plan <stack> cleared"
USER_ONLY_CASES = (
    (".claude/skills/ops/bin/ops tf apply x.plan", "! .claude/skills/ops/bin/ops tf apply x.plan"),
    (f"{ABS_CLI} tf apply x.plan", f"! {ABS_CLI} tf apply x.plan"),
    ("./.claude/skills/ops/bin/ops tf apply x.plan", "! ./.claude/skills/ops/bin/ops tf apply x.plan"),
    (".claude/skills/ops/bin/ops --env prod tf apply x.plan",
     "! .claude/skills/ops/bin/ops --env prod tf apply x.plan"),
    (".claude/skills/ops/bin/ops --env=staging tf apply x.plan",
     "! .claude/skills/ops/bin/ops --env=staging tf apply x.plan"),
    (".claude/skills/ops/bin/ops tf apply x.plan --env prod",
     "! .claude/skills/ops/bin/ops tf apply x.plan --env prod"),
    (f"bash -c '{ABS_CLI} tf apply x.plan'", f"! {ABS_CLI} tf apply x.plan"),
    (f"bash <<EOF\n{ABS_CLI} tf apply x.plan\nEOF", f"! {ABS_CLI} tf apply x.plan"),
    (f"git status && {ABS_CLI} tf apply x.plan; echo done", f"! {ABS_CLI} tf apply x.plan"),
    (f"echo $({ABS_CLI} tf apply x.plan)", f"! {ABS_CLI} tf apply x.plan"),
    (f"env AWS_PROFILE=prod {ABS_CLI} tf apply x.plan", f"! env AWS_PROFILE=prod {ABS_CLI} tf apply x.plan"),
    (f"AWS_PROFILE=prod {ABS_CLI} tf apply x.plan", f"! AWS_PROFILE=prod {ABS_CLI} tf apply x.plan"),
    (f"python3 {ABS_CLI} tf apply x.plan", f"! python3 {ABS_CLI} tf apply x.plan"),
    (f"python3 -I {ABS_CLI} tf apply x.plan", f"! python3 -I {ABS_CLI} tf apply x.plan"),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds apply x.plan", RUNNER_USER_TEXT),
    ("/abs/repo/scripts/ops/terraform-run.sh -chdir=/abs/repo/terraform/rds apply x.plan", RUNNER_USER_TEXT),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds destroy", RUNNER_USER_TEXT),
    ("/abs/repo/scripts/ops/terraform-run.sh -chdir=terraform/rds destroy -auto-approve", RUNNER_USER_TEXT),
    ("GUARD=0 scripts/ops/terraform-run.sh -chdir=terraform/rds apply x.plan", RUNNER_USER_TEXT),
    ("AWS_PROFILE=prod-provisioner scripts/ops/terraform-run.sh -chdir=terraform/rds apply", RUNNER_USER_TEXT),
    ("bash scripts/ops/terraform-run.sh -chdir=terraform/rds apply x.plan", RUNNER_USER_TEXT),
)


NET_TOOLS = (
    (ABS_CLI, "tf", "apply"),
    (CLI_TEXT, "tf", "apply"),
    ("$CLAUDE_PROJECT_DIR/.claude/skills/ops/bin/ops", "tf", "apply"),
    ("scripts/ops/terraform-run.sh", "-chdir=terraform/rds", "apply"),
    ("/abs/repo/scripts/ops/terraform-run.sh", "-chdir=terraform/rds", "destroy"),
)
NET_TEMPLATES = (
    "{path} {pre} {verb} /tmp/x.plan # don't",
    "{path} {pre} {verb} x.plan\necho \"oops",
    "run_it() {{ {path} {pre} {verb} x.plan; }}; run_it",
    "printf '{verb} x.plan' | xargs {path} {pre}",
    "find . -name '*.plan' -exec {path} {pre} {verb} {{}} ;",
    "sh -c '\"$0\" {pre} {verb} x.plan' {path}",
    "bash <<< \"{path} {pre} {verb} x.plan\"",
    "a={verb}; {path} {pre} $a x.plan",
    "{path} {pre} {{{verb},}} x.plan",
    "{path} {pre} $'{verb}' x.plan",
    "{path} {pre} \\\n{verb} x.plan",
    "cp {path} /tmp/o && /tmp/o {pre} {verb} x.plan",
    "python3 -c \"import subprocess; subprocess.run(['{path}','{pre}','{verb}','x.plan'])\"",
    "python3 -m runpy {path} {pre} {verb} x.plan",
    "echo {verb}; {path} {pre} x.plan",
    "({path} {pre} {verb} x.plan)",
)
NET_CASES = tuple(one_template.format(path=tool_path, pre=pre_word, verb=verb_word)
                  for one_template in NET_TEMPLATES for tool_path, pre_word, verb_word in NET_TOOLS)
ORDINARY_CASES = (
    f"{CLI_TEXT} tf plan seoul --expect module.fleet.aws_instance.cp -- -var-file=prod.tfvars -destroy",
    f"{CLI_TEXT} tf plan oregon",
    f"{CLI_TEXT} list",
    f"{CLI_TEXT} help",
    "python3 -m unittest discover -s .claude/skills/ops/tests",
    'git commit -m "ops tf apply reads the verdict file"',
    "scripts/ops/terraform-run.sh -chdir=terraform/seoul show -json x.plan",
    "scripts/ops/terraform-run.sh -chdir=terraform/seoul init -reconfigure",
    "scripts/ops/terraform-run.sh -chdir=terraform/seoul fmt",
    "scripts/ops/terraform-run.sh -chdir=terraform/seoul validate",
    "scripts/ops/terraform-run.sh -chdir=terraform/seoul workspace list",
    "scripts/ops/terraform-run.sh -chdir=terraform/seoul state list",
    f"rg -n apply_line {ABS_CLI}",
    f"{CLI_TEXT} tf plan rds -- -var-file=reapply.tfvars -auto-approve",
)
KNOWN_COST_CASES = (f"rg -n apply {CLI_TEXT}",)


class HookTest(unittest.TestCase):
    def test_indirect_and_unreadable_spellings_are_refused(self):
        for command_text in NET_CASES:
            with self.subTest(command_text=command_text):
                refusal_text = ops_cli.hook_refusal(command_text)
                self.assertIn("only the user", refusal_text)
                self.assertIn("  user runs  ! ", refusal_text)

    def test_a_command_the_lexer_cannot_read_is_still_refused(self):
        for command_text in (f"{CLI_TEXT} tf apply /tmp/x.plan # don't",
                             f"{CLI_TEXT} tf apply /tmp/x.plan\necho \"oops"):
            with self.subTest(command_text=command_text):
                self.assertEqual(ops_cli.classify_command(command_text), [])
                self.assertIn(f"names {ops_cli.NET_NAMES[0]} together with the word 'apply'",
                              ops_cli.hook_refusal(command_text))

    def test_the_text_net_does_not_refuse_ordinary_work(self):
        for command_text in ORDINARY_CASES:
            with self.subTest(command_text=command_text):
                self.assertEqual(ops_cli.hook_refusal(command_text), "")

    def test_naming_the_cli_with_the_word_is_the_known_cost(self):
        for command_text in KNOWN_COST_CASES:
            self.assertIn("rg -f <pattern file>", ops_cli.hook_refusal(command_text))

    def test_the_net_word_is_standalone(self):
        for word_text, is_match in (("apply;", True), ("apply)", True), ("'apply'", True), ('"apply"', True),
                                    ("apply\n", True), ("(destroy", True), ("reapply", False),
                                    ("apply_line", False), ("-destroy", False), ("destroy-x", False),
                                    ("apply2", False), ("-auto-approve", False)):
            with self.subTest(word_text=word_text):
                self.assertEqual(bool(ops_cli.text_net_match(f"{CLI_TEXT} {word_text}")), is_match)

    def test_the_parse_level_refusal_keeps_its_exact_text(self):
        refusal_text = ops_cli.hook_refusal(f"{CLI_TEXT} tf apply x.plan")
        self.assertIn(f"  user runs  ! {CLI_TEXT} tf apply x.plan", refusal_text)
        self.assertNotIn("cannot show it harmless", refusal_text)

    def test_hook_classification(self):
        for command_text, expected_replacement in HOOK_CASES:
            with self.subTest(command_text=command_text):
                found_refusals = ops_cli.classify_command(command_text)
                if expected_replacement is None:
                    self.assertEqual(found_refusals, [])
                else:
                    self.assertEqual([one_refusal.replacement_text for one_refusal in found_refusals],
                                     [expected_replacement])

    def test_an_agent_cannot_apply(self):
        for command_text, expected_user_text in USER_ONLY_CASES:
            with self.subTest(command_text=command_text):
                found_refusals = ops_cli.classify_command(command_text)
                self.assertEqual([type(one_refusal) for one_refusal in found_refusals], [ops_cli.UserOnlyCommand])
                self.assertEqual(found_refusals[0].user_text, expected_user_text)

    def test_the_hook_script_refuses_apply_with_the_user_text(self):
        hook_run = self.run_hook(json.dumps({"tool_name": "Bash", "tool_input": {
            "command": f"{CLI_TEXT} tf apply x.plan"}}))
        self.assertEqual(hook_run.returncode, 2)
        self.assertIn("ops: an apply or destroy is refused from the Bash tool; only the user runs it", hook_run.stderr)
        self.assertIn(f"  user runs  ! {CLI_TEXT} tf apply x.plan", hook_run.stderr)
        self.assertNotIn("run      ", hook_run.stderr)

    def run_hook(self, hook_input):
        return subprocess.run(["bash", str(HOOK_FILE)], input=hook_input, capture_output=True, text=True)

    def test_the_hook_script_refuses_with_the_ops_command(self):
        hook_run = self.run_hook(json.dumps({"tool_name": "Bash", "tool_input": {
            "command": "AWS_PROFILE=prod scripts/ops/access/rds-query.sh \"SELECT 1\""}}))
        self.assertEqual(hook_run.returncode, 2)
        self.assertIn(f"run      {CLI_TEXT} sql 'SELECT 1'", hook_run.stderr)

    def test_the_hook_script_passes_ops_and_other_tools(self):
        for hook_input in (json.dumps({"tool_name": "Bash", "tool_input": {"command": f"{CLI_TEXT} doctor"}}),
                           json.dumps({"tool_name": "Read", "tool_input": {"file_path": "x"}}), "not json"):
            hook_run = self.run_hook(hook_input)
            self.assertEqual(hook_run.returncode, 0, hook_run.stderr)

    def test_the_hook_is_registered_beside_the_checks_hook(self):
        settings_data = json.loads((REPO_ROOT / ".claude" / "settings.json").read_text())
        bash_hooks = [one_hook["command"] for one_group in settings_data["hooks"]["PreToolUse"]
                      if one_group["matcher"] == "Bash" for one_hook in one_group["hooks"]]
        checks_index = bash_hooks.index("$CLAUDE_PROJECT_DIR/.claude/hooks/check-output-redirect.sh")
        self.assertEqual(bash_hooks[checks_index + 1], "$CLAUDE_PROJECT_DIR/.claude/hooks/ops-redirect.sh")
        self.assertEqual(settings_data["permissions"]["allow"], ["Bash(.claude/skills/ops/bin/ops *)"])


PROFILE_ASSIGNMENT = re.compile(r"(^|[\s;])(export\s+)?AWS_PROFILE=|AWS_PROFILE:-|AWS_PROFILE:=")
QUOTED_SPAN = re.compile(r"\"[^\"]*\"|'[^']*'")
DANTE_PROFILE = re.compile(r"AWS_PROFILE[=:\-\"'{ ]+danteplanner\b|--profile[= ]+danteplanner\b")


class ProfileTest(unittest.TestCase):
    def script_lines(self):
        for script_file in sorted((REPO_ROOT / "scripts").rglob("*.sh")):
            for line_number, text_line in enumerate(script_file.read_text(encoding="utf-8").splitlines(), start=1):
                yield script_file, line_number, text_line

    def test_no_script_assigns_a_profile(self):
        found_sites = [f"{script_file.relative_to(REPO_ROOT)}:{line_number}" for script_file, line_number, text_line
                       in self.script_lines()
                       if not text_line.lstrip().startswith("#")
                       and PROFILE_ASSIGNMENT.search(QUOTED_SPAN.sub("", text_line))]
        self.assertEqual(found_sites, [])

    def test_no_code_path_names_a_danteplanner_profile(self):
        found_sites = [f"{script_file.relative_to(REPO_ROOT)}:{line_number}" for script_file, line_number, text_line
                       in self.script_lines() if DANTE_PROFILE.search(text_line)]
        self.assertEqual(found_sites, [])
        self.assertNotRegex(CLI_FILE.read_text(), DANTE_PROFILE)

    def test_a_direct_call_without_credentials_names_ops_doctor(self):
        with tempfile.TemporaryDirectory() as fake_dir:
            fake_aws = Path(fake_dir) / "aws"
            fake_aws.write_text("#!/bin/sh\necho 'Unable to locate credentials' >&2\nexit 255\n")
            fake_aws.chmod(0o755)
            child_env = {env_key: env_value for env_key, env_value in os.environ.items()
                         if not env_key.startswith("AWS_")}
            child_env["PATH"] = f"{fake_dir}:/usr/bin:/bin"
            script_run = subprocess.run([str(REPO_ROOT / ops_cli.SQL_SCRIPT), "SELECT 1"], env=child_env,
                                        capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=60)
        self.assertNotEqual(script_run.returncode, 0)
        self.assertIn(f"{CLI_TEXT} doctor", script_run.stderr)
        self.assertIn("AWS_PROFILE=prod", script_run.stderr)
        self.assertIn("aws sso login --sso-session danteplanner", script_run.stderr)


if __name__ == "__main__":
    unittest.main()
