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
PROFILE_ACCOUNTS = {"prod": "111122223333", "staging": "444455556666", "mgmt": "777788889999"}
UNMAPPED_ID = "999988887777"
FAKE_CONFIG_TEXT = "".join(f"[profile {profile_name}]\nsso_session = s\nsso_account_id = {account_id}\n\n"
                           for profile_name, account_id in PROFILE_ACCOUNTS.items())
FAKE_CONFIG_TEXT += "[profile prod-provisioner]\nsso_account_id = 111122223333\n"


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
                                         ("oregon", '  backend "s3" {\n  }\n'), ("state-backend", ""),
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
        self.assertTrue(ops_cli.parse_request(["--help"]).wants_help)

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
    def test_the_dispatch_table_is_the_read_and_plan_set(self):
        self.assertEqual([(one_spec.command_name, one_spec.script_path) for one_spec in ops_cli.DISPATCH_TABLE], [
            ("metrics", "scripts/ops/access/metrics-query.sh"),
            ("logs", "scripts/ops/access/logs-query.sh"),
            ("sql", "scripts/ops/access/rds-query.sh"),
            ("tunnel", "scripts/ops/access/rds-tunnel.sh"),
            ("verify db", "scripts/ops/access/db-observability-check.sh"),
            ("verify oregon", "scripts/ops/oregon-verify.sh"),
            ("tf plan", "scripts/ops/terraform-run.sh"),
            ("whoami", ""),
            ("doctor", ""),
            ("list", ""),
        ])

    def test_every_invocation_runs_a_script_from_the_table(self):
        for command_words in (["metrics", "up"], ["logs", "q"], ["sql", "SELECT 1"], ["tunnel", "status"],
                              ["verify", "db"], ["verify", "oregon"]):
            self.assertIn(ops_cli.build_invocation(command_words, "/").command_spec.script_path, TABLE_SCRIPTS)

    def test_oregon_verify_never_fetches_the_kubeconfig(self):
        self.assertEqual(ops_cli.build_invocation(["verify", "oregon"], "/").script_args, ())
        with self.assertRaisesRegex(ops_cli.OpsError, "ops verify takes one of db, oregon"):
            ops_cli.build_invocation(["verify", "oregon", "--kubeconfig"], "/")

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
                           ["verify", "db"], ["verify", "oregon"], ["tf", "plan", "rds"], ["whoami"]):
            fake_effects = FakeEffects(session_rows={"prod": (255, EXPIRED_STDERR)})
            with self.assertRaisesRegex(ops_cli.OpsError, "aws sso login --sso-session danteplanner"):
                self.run_main(argv_words, fake_effects)
            self.assertEqual(fake_effects.stream_calls, [], argv_words)

    def test_verify_oregon_needs_the_oregon_stack_initialized(self):
        (self.repo_root / "terraform" / "oregon" / ".terraform").rmdir()
        fake_effects = FakeEffects()
        with self.assertRaisesRegex(ops_cli.OpsError, "terraform/oregon is not initialized"):
            self.run_main(["verify", "oregon"], fake_effects)
        self.assertEqual(fake_effects.stream_calls, [])
        with unittest.mock.patch.dict(os.environ, {"CP_INSTANCE_ID": "i-0abc"}):
            self.run_main(["verify", "oregon"], fake_effects)
        self.assertEqual(fake_effects.stream_calls[0][0], self.script_argv(ops_cli.OREGON_VERIFY_SCRIPT))

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
        fake_effects, exit_code, out_lines, _ = self.plan_run("terraform-show-mixed.json")
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
            f"! {self.repo_root / ops_cli.TERRAFORM_RUNNER} -chdir={self.repo_root / 'terraform' / 'rds'} "
            f"apply {plan_path}",
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
            f"! {self.repo_root / ops_cli.TERRAFORM_RUNNER} -chdir={self.repo_root / 'terraform' / 'rds'} "
            f"apply {plan_path}",
        ])

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

    def test_a_local_state_stack_needs_a_plain_init_or_existing_state(self):
        with self.assertRaises(ops_cli.OpsError) as raised_error:
            self.run_main(["tf", "plan", "state-backend"], FakeEffects())
        self.assertIn(f"AWS_PROFILE=prod {self.repo_root / ops_cli.TERRAFORM_RUNNER} "
                      f"-chdir={self.repo_root / 'terraform' / 'state-backend'} init", str(raised_error.exception))
        self.assertNotIn("-backend-config", str(raised_error.exception))
        (self.repo_root / "terraform" / "state-backend" / "terraform.tfstate").write_text("{}\n")
        fake_effects = FakeEffects(show_text=(FIXTURE_DIR / "terraform-show-no-changes.json").read_text())
        exit_code, out_text, _ = self.run_main(["tf", "plan", "state-backend"], fake_effects)
        self.assertEqual((exit_code, out_text), (0, "No changes.\n"))

    def test_the_real_tree_backend_files(self):
        self.assertEqual(ops_cli.backend_file_name(REPO_ROOT, "registry"), "backend.management.hcl")
        self.assertEqual(ops_cli.backend_file_name(REPO_ROOT, "organization"), "backend.management.hcl")
        self.assertEqual(ops_cli.backend_file_name(REPO_ROOT, "log-archive"), "backend.log-archive.hcl")
        self.assertEqual(ops_cli.backend_file_name(REPO_ROOT, "oregon"), "")
        self.assertEqual(ops_cli.backend_file_name(REPO_ROOT, "cloudflare"), "")
        self.assertEqual(ops_cli.stack_info(REPO_ROOT, "registry").default_env(), "mgmt")
        self.assertTrue(ops_cli.stack_info(REPO_ROOT, "log-archive").is_unmapped)
        self.assertFalse(ops_cli.stack_info(REPO_ROOT, "iam-bootstrap").has_backend)

    def test_imports_and_moves_are_changes(self):
        for fixture_name, expected_lines in (
                ("terraform-show-import-move-mixed.json", [
                    "- terraform_data.gone", "+ terraform_data.fresh", "<= terraform_data.imported",
                    "-> terraform_data.old_name to terraform_data.new_name",
                    "Plan: 1 to import, 1 to add, 0 to change, 1 to destroy."]),
                ("terraform-show-import-only.json", [
                    "<= terraform_data.imported", "Plan: 1 to import, 0 to add, 0 to change, 0 to destroy."]),
                ("terraform-show-move-only.json", [
                    "-> terraform_data.old_name to terraform_data.new_name",
                    "Plan: 0 to add, 0 to change, 0 to destroy."])):
            with self.subTest(fixture_name=fixture_name):
                for stale_file in self.log_dir.glob("*"):
                    stale_file.unlink()
                _, exit_code, out_lines, _ = self.plan_run(fixture_name)
                self.assertEqual(exit_code, 0)
                self.assertEqual(out_lines[:-1], expected_lines)
                self.assertTrue(out_lines[-1].startswith(f"! {self.repo_root / ops_cli.TERRAFORM_RUNNER} "))

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

    def test_there_is_no_apply(self):
        for argv_words in (["tf", "apply"], ["tf", "apply", "rds"], ["tf", "destroy"]):
            with self.assertRaises(ops_cli.OpsError) as raised_error:
                self.run_main(argv_words, FakeEffects())
            self.assertIn("apply is run by you", str(raised_error.exception))
            self.assertIn(f"! {self.repo_root / ops_cli.TERRAFORM_RUNNER} "
                          f"-chdir={self.repo_root / 'terraform'}/<stack> apply <plan file>",
                          str(raised_error.exception))

    def test_the_real_tree_stacks_are_the_ones_with_a_backend(self):
        self.assertEqual(ops_cli.plan_stacks(REPO_ROOT), ["cloudflare", "iam-bootstrap", "log-archive", "oregon",
                                                          "organization", "rds", "registry", "secrets", "seoul",
                                                          "state-backend"])


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
        self.assertEqual(self.run_bare("--help").returncode, 0)

    def hook_input(self, command_text):
        return json.dumps({"tool_name": "Bash", "tool_input": {"command": command_text}})

    def assert_fail_closed(self):
        for command_text in ("scripts/ops/access/rds-query.sh 'SELECT 1'",
                             "scripts/ops/terraform-run.sh -chdir=terraform/rds plan"):
            hook_run = self.run_bare("hook", stdin_text=self.hook_input(command_text))
            self.assertEqual(hook_run.returncode, 2, command_text)
            self.assertIn(f"git checkout -- {ops_cli.CHECKS_CLI_PATH}", hook_run.stderr)
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
    ("AWS_PROFILE=staging scripts/ops/terraform-run.sh -chdir=terraform/secrets plan -var-file=staging.tfvars",
     f"{CLI_TEXT} --env staging tf plan secrets -- -var-file=staging.tfvars"),
    ("scripts/ops/access/metrics-query.sh 'rate(x[5m])' 6h 1m",
     f"{CLI_TEXT} metrics 'rate(x[5m])' --since 6h --step 1m"),
    ("scripts/ops/access/logs-query.sh --day 2026-07-23 '{app=\"backend\"}' 9",
     f"{CLI_TEXT} logs --day 2026-07-23 '{{app=\"backend\"}}' --limit 9"),
    ("scripts/ops/access/rds-query.sh -f q.sql seoul", f"{CLI_TEXT} sql -f q.sql --site seoul"),
    ("scripts/ops/access/rds-tunnel.sh start seoul", f"{CLI_TEXT} tunnel start --site seoul"),
    ("AWS_PROFILE=prod scripts/ops/access/db-observability-check.sh", f"{CLI_TEXT} verify db"),
    ("scripts/ops/oregon-verify.sh", f"{CLI_TEXT} verify oregon"),
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
    ("AWS_PROFILE=prod-provisioner scripts/ops/access/rds-query.sh 'SELECT 1'", None),
    ("AWS_PROFILE=prod-provisioner scripts/ops/terraform-run.sh -chdir=terraform/rds plan", None),
    ("AWS_PROFILE=staging scripts/ops/access/rds-query.sh 'SELECT 1'", f"{CLI_TEXT} --env staging sql 'SELECT 1'"),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds plan --out=x.plan", f"{CLI_TEXT} tf plan rds"),
    ("echo `scripts/ops/access/rds-query.sh 'SELECT 1'`", f"{CLI_TEXT} sql 'SELECT 1'"),
    ("source scripts/ops/access/rds-tunnel.sh start", f"{CLI_TEXT} tunnel start"),
    (". scripts/ops/access/rds-tunnel.sh stop", f"{CLI_TEXT} tunnel stop"),
    ("bash <<EOF\nscripts/ops/access/rds-tunnel.sh status\nEOF", f"{CLI_TEXT} tunnel status"),
    ("sh <<'EOF'\nscripts/ops/oregon-verify.sh\nEOF", f"{CLI_TEXT} verify oregon"),
    ("cat <<EOF | bash\nscripts/ops/access/rds-tunnel.sh status\nEOF", f"{CLI_TEXT} tunnel status"),
    ("git commit -F - <<'EOF'\nscripts/ops/access/rds-tunnel.sh status\nEOF", None),
    ("scripts//ops/access/rds-tunnel.sh status", f"{CLI_TEXT} tunnel status"),
    ("./scripts/ops/./access/rds-tunnel.sh status", f"{CLI_TEXT} tunnel status"),
    ("scripts/ops/terraform-run.sh -chdir=terraform//rds/ plan", f"{CLI_TEXT} tf plan rds"),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds apply x.plan", None),
    ("scripts/ops/terraform-run.sh -chdir=terraform/rds init -reconfigure", None),
    ("scripts/ops/provision/grafana-secrets.sh", None),
    ("scripts/ops/oregon-verify.sh --kubeconfig", None),
    ("rg -n 'access/rds-query.sh' docs", None),
    ("cat scripts/ops/access/rds-query.sh", None),
    ("scripts/ops/access/staging-env.sh", None),
)


class HookTest(unittest.TestCase):
    def test_hook_classification(self):
        for command_text, expected_replacement in HOOK_CASES:
            with self.subTest(command_text=command_text):
                found_refusals = ops_cli.classify_command(command_text)
                if expected_replacement is None:
                    self.assertEqual(found_refusals, [])
                else:
                    self.assertEqual([one_refusal.replacement_text for one_refusal in found_refusals],
                                     [expected_replacement])

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
