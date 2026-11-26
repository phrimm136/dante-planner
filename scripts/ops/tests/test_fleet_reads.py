import json
import os
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

from shells import FIRST_SHELL, with_second_shell

OPS_DIR = Path(__file__).resolve().parents[1]
NODES_SCRIPT = OPS_DIR / "access" / "fleet-nodes.sh"
BOOTLOG_SCRIPT = OPS_DIR / "access" / "boot-log.sh"
APP_SCRIPT = OPS_DIR / "access" / "argocd-app.sh"
METRICS_SCRIPT = OPS_DIR / "access" / "metrics-query.sh"
GROUP_NAME = "danteplanner-oregon-app"
CLOCK_NOW = 1767225600
FAKE_DATE = f"""#!/bin/sh
[ "$*" = +%s ] && {{ printf '%s\\n' {CLOCK_NOW}; exit 0; }}
PATH=${{PATH#*:}}; export PATH; exec date "$@"
"""

FAKE_AWS = """#!/bin/sh
printf '%s\\n' "$*" >> "$FAKE_DIR/aws-calls"
case "$1 $2" in
  "sts get-caller-identity") printf '%s\\n' 111122223333 ;;
  "ssm send-command")
    while [ "$1" != "--parameters" ]; do shift; done
    printf '%s\\n' "$2" | jq -r '.commands[]' > "$FAKE_DIR/remote"
    [ -f "$FAKE_DIR/send-fails" ] && grep -qF "$(cat "$FAKE_DIR/send-fails")" "$FAKE_DIR/remote" \\
      && { printf '%s\\n' 'An error occurred (InvalidInstanceId) when calling the SendCommand operation' >&2; exit 254; }
    "$FAKE_SHELL" "$FAKE_DIR/remote" > "$FAKE_DIR/stdout" 2> "$FAKE_DIR/stderr" \\
      && printf '%s\\n' Success > "$FAKE_DIR/status" || printf '%s\\n' Failed > "$FAKE_DIR/status"
    [ -f "$FAKE_DIR/failing-command" ] && grep -qF "$(cat "$FAKE_DIR/failing-command")" "$FAKE_DIR/remote" \\
      && printf '%s\\n' "$(cat "$FAKE_DIR/failing-status" 2>/dev/null || echo Failed)" > "$FAKE_DIR/status"
    printf '%s\\n' cmd-1 ;;
  "ssm get-command-invocation")
    case "$*" in
      *"--query Status"*) cat "$FAKE_DIR/status" ;;
      *StandardErrorContent*) cat "$FAKE_DIR/stderr" ;;
      *)
        [ -f "$FAKE_DIR/output-read-fails" ] && grep -qF "$(cat "$FAKE_DIR/output-read-fails")" "$FAKE_DIR/remote" \\
          && { printf '%s\\n' 'An error occurred (ThrottlingException) when calling the GetCommandInvocation operation' >&2; exit 254; }
        cat "$FAKE_DIR/stdout" ;;
    esac ;;
  "ec2 describe-instances") cat "$FAKE_DIR/instances" ;;
  "autoscaling describe-auto-scaling-groups") cat "$FAKE_DIR/members" ;;
  "ec2 get-console-output") cat "$FAKE_DIR/console" ;;
  "secretsmanager get-secret-value")
    printf '%s\\n' '{"remote_write_username":"u","observability_read_token":"t"}' ;;
  *) exit 9 ;;
esac
"""

FAKE_K3S = """#!/bin/sh
printf '%s\\n' "$*" >> "$FAKE_DIR/k3s-calls"
[ -f "$FAKE_DIR/k3s-fails" ] && { printf '%s\\n' 'connection refused' >&2; exit 1; }
case " $* " in
  *" wait "*) [ -f "$FAKE_DIR/wait-fails" ] && { printf '%s\\n' 'timed out waiting' >&2; exit 1; } ;;
  *" redis-cli "*)
    [ -f "$FAKE_DIR/auth-off" ] && { printf '%s\\n' PONG; exit 0; }
    printf '%s\\n' 'NOAUTH Authentication required.'; exit 0 ;;
esac
cat "$FAKE_DIR/k3s-output"
"""


def node_line(node_name, private_ip, ready_text="True", role_text="app", cp_text=""):
    return "\t".join([node_name, private_ip, ready_text, role_text, cp_text])


def instance_row(instance_id, private_ip, role_text, age_seconds, **extra_tags):
    launch_text = time.strftime("%Y-%m-%dT%H:%M:%S+00:00", time.gmtime(CLOCK_NOW - age_seconds))
    tag_rows = [{"Key": "Role", "Value": role_text}] + [{"Key": tag_key, "Value": tag_value}
                                                        for tag_key, tag_value in extra_tags.items()]
    return {"InstanceId": instance_id, "PrivateIpAddress": private_ip, "LaunchTime": launch_text,
            "State": {"Name": "running"}, "Tags": tag_rows}


class FakeFleetCase(unittest.TestCase):
    shell_path = FIRST_SHELL

    def setUp(self):
        self.work_handle = tempfile.TemporaryDirectory()
        self.work_dir = Path(self.work_handle.name)
        fake_bin = self.work_dir / "bin"
        fake_bin.mkdir()
        for tool_name, tool_text in (("aws", FAKE_AWS), ("k3s", FAKE_K3S), ("date", FAKE_DATE)):
            (fake_bin / tool_name).write_text(tool_text)
            (fake_bin / tool_name).chmod(0o755)
        self.fake_bin = fake_bin

    def tearDown(self):
        self.work_handle.cleanup()

    def fake_env(self, **extra_env):
        return {**os.environ, "PATH": f"{self.fake_bin}:{os.environ['PATH']}", "FAKE_DIR": str(self.work_dir),
                "FAKE_SHELL": self.shell_path, "CP_INSTANCE_ID": "i-0cc", "AWS_PROFILE": "prod", **extra_env}

    def run_read(self, script_path, *script_args, **extra_env):
        return subprocess.run([str(script_path), *script_args], capture_output=True, text=True,
                              env=self.fake_env(**extra_env), stdin=subprocess.DEVNULL)


class FleetNodesTest(FakeFleetCase):
    def nodes_run(self, instances, node_lines, members=(), *script_args):
        (self.work_dir / "instances").write_text(json.dumps(instances))
        (self.work_dir / "members").write_text(json.dumps(
            [{"InstanceId": member_id, "LifecycleState": lifecycle_text} for member_id, lifecycle_text in members]))
        (self.work_dir / "k3s-output").write_text("".join(f"{one_line}\n" for one_line in node_lines))
        return self.run_read(NODES_SCRIPT, *script_args)

    def test_every_row_joined_is_exit_0_with_one_line_per_instance(self):
        node_run = self.nodes_run(
            [instance_row("i-0a", "10.0.0.2", "app", 9000), instance_row("i-0c", "10.0.0.1", "control-plane", 9000),
             instance_row("i-0d", "10.0.0.3", "data", 9000), instance_row("i-0e", "10.0.0.4", "ingress", 9000)],
            [node_line("cp-node", "10.0.0.1", role_text="", cp_text="true"), node_line("app-node", "10.0.0.2"),
             node_line("data-node", "10.0.0.3", "Unknown", "data"), node_line("in-node", "10.0.0.4", "True", "ingress")],
            [("i-0a", "InService")])
        self.assertEqual(node_run.returncode, 0, node_run.stderr)
        cells = [one_line.split("  ") for one_line in node_run.stdout.splitlines()]
        self.assertEqual([one_row[:4] for one_row in cells], [
            ["joined", "app", "i-0a", "InService"], ["joined", "cp", "i-0c", "running"],
            ["joined", "data", "i-0d", "running"], ["joined", "ingress", "i-0e", "running"]])
        self.assertEqual([one_row[5:] for one_row in cells], [
            ["10.0.0.2", "app-node Ready"], ["10.0.0.1", "cp-node Ready"], ["10.0.0.3", "data-node NotReady"],
            ["10.0.0.4", "in-node Ready"]])

    def test_an_instance_under_ten_minutes_without_a_node_is_joining(self):
        node_run = self.nodes_run([instance_row("i-0a", "10.0.0.2", "app", 590, Surge="true")], [])
        self.assertEqual(node_run.returncode, 4, node_run.stderr)
        self.assertTrue(node_run.stdout.startswith("joining  app  i-0a  running"))
        self.assertNotIn("!", node_run.stdout)

    def test_a_surge_app_instance_past_ten_minutes_gets_the_exact_command(self):
        node_run = self.nodes_run([instance_row("i-0a", "10.0.0.2", "app", 600, Surge="true")], [],
                                  [("i-0a", "InService")])
        self.assertEqual(node_run.returncode, 1, node_run.stderr)
        out_lines = node_run.stdout.splitlines()
        self.assertTrue(out_lines[0].startswith("NOT JOINED  app  i-0a  InService"))
        self.assertTrue(out_lines[0].endswith("no node  surge"))
        self.assertEqual(out_lines[1:], [
            "! AWS_PROFILE=prod aws autoscaling terminate-instance-in-auto-scaling-group --region us-west-2 "
            "--instance-id i-0a --no-should-decrement-desired-capacity"])
        self.assertNotIn("terminate", (self.work_dir / "aws-calls").read_text())

    def test_no_command_without_the_surge_tag_or_outside_the_group_or_while_terminating(self):
        node_run = self.nodes_run(
            [instance_row("i-0a", "10.0.0.2", "app", 900), instance_row("i-0b", "10.0.0.3", "app", 900, Surge="true"),
             instance_row("i-0c", "10.0.0.5", "app", 900, Surge="true"),
             instance_row("i-0d", "10.0.0.4", "data", 900)], [],
            [("i-0a", "InService"), ("i-0c", "Terminating:Wait")])
        self.assertEqual(node_run.returncode, 1)
        self.assertEqual(node_run.stdout.splitlines()[4:], [
            "i-0a: no terminate command, it has no Surge=true tag",
            f"i-0b: no terminate command, it is not in the ASG {GROUP_NAME}",
            "i-0c: no terminate command, the ASG is terminating it",
            "i-0d: no terminate command, it has no Surge=true tag"])

    def test_a_node_without_an_instance_is_gone_and_an_ip_prefix_is_not_a_match(self):
        node_run = self.nodes_run([instance_row("i-0a", "10.0.0.21", "app", 900)],
                                  [node_line("app-node", "10.0.0.21"), node_line("old-node", "10.0.0.2")])
        self.assertEqual(node_run.returncode, 1, node_run.stderr)
        self.assertEqual(node_run.stdout.splitlines()[1], "gone  app  -  -  -  10.0.0.2  old-node Ready")

    def test_not_joined_outranks_joining(self):
        node_run = self.nodes_run([instance_row("i-0a", "10.0.0.2", "app", 60),
                                   instance_row("i-0b", "10.0.0.3", "app", 900)], [])
        self.assertEqual(node_run.returncode, 1)

    def test_an_unreadable_node_list_is_exit_2_and_prints_no_row(self):
        (self.work_dir / "k3s-fails").write_text("")
        node_run = self.nodes_run([instance_row("i-0a", "10.0.0.2", "app", 900, Surge="true")], [],
                                  [("i-0a", "InService")])
        self.assertEqual((node_run.returncode, node_run.stdout), (2, ""))
        self.assertIn("connection refused", node_run.stderr)

    def test_an_empty_fleet_is_not_a_pass(self):
        node_run = self.nodes_run([], [])
        self.assertEqual((node_run.returncode, node_run.stdout), (2, ""))
        self.assertIn("no instance and no node found for oregon", node_run.stderr)

    def test_the_site_picks_the_region_and_the_group(self):
        self.nodes_run([], [], (), "seoul")
        aws_calls = (self.work_dir / "aws-calls").read_text()
        self.assertIn("--region ap-northeast-2 --auto-scaling-group-names danteplanner-seoul-app", aws_calls)
        self.assertIn("Name=tag:Name,Values=danteplanner-seoul-*", aws_calls)
        self.assertNotIn("us-west-2", aws_calls)

    def test_an_unknown_site_is_refused(self):
        node_run = self.run_read(NODES_SCRIPT, "tokyo")
        self.assertEqual(node_run.returncode, 2)
        self.assertFalse((self.work_dir / "aws-calls").exists())


class BootLogTest(FakeFleetCase):
    def test_the_console_output_is_printed_without_carriage_returns(self):
        (self.work_dir / "console").write_text("line one\r\nline two\r\n")
        boot_run = self.run_read(BOOTLOG_SCRIPT, "i-0abc12", "seoul")
        self.assertEqual((boot_run.returncode, boot_run.stdout), (0, "line one\nline two\n"))
        self.assertIn("--region ap-northeast-2 --instance-id i-0abc12 --latest", (self.work_dir / "aws-calls").read_text())

    def test_an_id_that_is_not_hex_is_refused(self):
        for instance_id in ("i-0xyz", "0abc", "i-", "i-0abc;id", ""):
            with self.subTest(instance_id=instance_id):
                self.assertEqual(self.run_read(BOOTLOG_SCRIPT, instance_id).returncode, 2)
        self.assertFalse((self.work_dir / "aws-calls").exists())


class ArgocdAppTest(FakeFleetCase):
    def app_run(self, *output_lines):
        (self.work_dir / "k3s-output").write_text("".join(f"{one_line}\n" for one_line in output_lines))
        return self.run_read(APP_SCRIPT)

    def test_the_application_is_summarised_and_only_unsynced_resources_are_listed(self):
        app_run = self.app_run(
            "path\tdeploy/chart", "values\tvalues-oregon.yaml", "sync\tOutOfSync", "health\tDegraded",
            'automated\t{"prune":true,"selfHeal":true}', "resource\tSynced\tService\tbackend",
            "resource\tOutOfSync\tDaemonSet\tbackend", "resource\t\tJob\thook-job",
            "condition\tSyncError\tfirst line", "second line")
        self.assertEqual(app_run.returncode, 0, app_run.stderr)
        self.assertEqual(app_run.stdout.splitlines(), [
            "source path     deploy/chart", "values file     values-oregon.yaml", "sync            OutOfSync",
            "health          Degraded", "automated sync  on", "not synced      OutOfSync DaemonSet backend",
            "not synced      - Job hook-job", "condition       SyncError: first line", "                second line"])
        self.assertIn("applications.argoproj.io danteplanner-oregon", (self.work_dir / "k3s-calls").read_text())

    def test_automated_sync_off(self):
        for automated_text in ("", '{"enabled":false,"prune":true}'):
            with self.subTest(automated_text=automated_text):
                self.assertIn("automated sync  off", self.app_run(f"automated\t{automated_text}").stdout)
        self.assertIn("automated sync  on", self.app_run("automated\t{}").stdout)

    def test_a_failed_read_is_a_failure(self):
        (self.work_dir / "k3s-fails").write_text("")
        app_run = self.app_run("path\tdeploy/chart")
        self.assertNotEqual(app_run.returncode, 0)
        self.assertIn("connection refused", app_run.stderr)

    def test_a_complete_read_whose_command_failed_reports_nothing(self):
        (self.work_dir / "failing-command").write_text("=== END ===")
        app_run = self.app_run("path\tdeploy/chart", "sync\tSynced")
        self.assertEqual(app_run.returncode, 1)
        self.assertEqual(app_run.stdout, "")
        self.assertIn("ended with status Failed", app_run.stderr)

    def test_a_truncated_read_says_so(self):
        output_text = "path\tdeploy/chart\nresource\tOutOfSy"
        app_run = subprocess.run(["bash", "-c", f'source "{APP_SCRIPT}"\n'
                                  f'run_on_control_plane() {{ printf "%s" "$OUTPUT_TEXT"; }}\nmain oregon'],
                                 capture_output=True, text=True, env=self.fake_env(OUTPUT_TEXT=output_text))
        self.assertEqual(app_run.returncode, 1)
        self.assertIn("incomplete", app_run.stderr)


class MetricNamesTest(FakeFleetCase):
    def metrics_run(self, *script_args):
        (self.fake_bin / "curl").write_text("#!/bin/sh\ncat \"$FAKE_DIR/metrics\"\n")
        (self.fake_bin / "curl").chmod(0o755)
        (self.work_dir / "metrics").write_text(json.dumps({"data": {"result": [
            {"metric": {"__name__": "up", "job": "a"}, "values": [[0, "1"]]},
            {"metric": {"job": "b"}, "values": [[60, "2"]]}]}}))
        return self.run_read(METRICS_SCRIPT, *script_args).stdout

    def test_names_adds_the_metric_name_and_without_it_nothing_changes(self):
        self.assertEqual(self.metrics_run("up"), "1970-01-01T00:00:00Z  1  job=a\n1970-01-01T00:01:00Z  2  job=b\n")
        self.assertEqual(self.metrics_run("--names", "up", "1h"),
                         "1970-01-01T00:00:00Z  1  up  job=a\n1970-01-01T00:01:00Z  2  -  job=b\n")


RESTART_STEPS = (("pre-check", "=== SECRET ===", "key-present", ("pre-checks passed", "pre-checks failed")),
                 ("Redis restart", "REDIS_NOT_READY", "AUTH_REQUIRED",
                  ("Ready and requires AUTH", "not Ready; exporter left untouched", "does not require AUTH")),
                 ("exporter restart", "EXPORTER_NOT_READY", "EXPORTER_READY",
                  ("auth exporter Ready", "auth exporter not Ready")),
                 ("AUTH check", "AUTH_NOT_REQUIRED", "AUTH_REQUIRED",
                  ("does not require AUTH; exporter left untouched", "Restarting auth exporter pods")))
MODE_STEPS = {"--apply": ("pre-check", "Redis restart", "exporter restart"),
              "--exporter-only": ("AUTH check", "exporter restart")}
STEP_FAULTS = (("Failed", "failing-command", "SENT_FAILED", True, "ended with status Failed"),
               ("TimedOut", "failing-command", "SENT_OUTCOME_UNKNOWN", True, "ended with status TimedOut"),
               ("output unreadable", "output-read-fails", "SENT_OUTCOME_UNKNOWN", False, "ThrottlingException"),
               ("not sent", "send-fails", "NOT_SENT", False, "InvalidInstanceId"))
RERUN_RESTARTS_REDIS = "a rerun of --apply restarts auth Redis again"


def next_action(step_name, ending_name):
    if step_name in ("pre-check", "AUTH check"):
        return f"the {step_name} only reads; a rerun is safe"
    if step_name == "exporter restart":
        return "rerun with --exporter-only to restart only the exporter"
    if ending_name == "NOT_SENT":
        return "nothing ran; a rerun is safe"
    return RERUN_RESTARTS_REDIS


class RedisRestartTest(FakeFleetCase):
    def restart_run(self, *script_args):
        (self.fake_bin / "sleep").write_text("#!/bin/sh\nexit 0\n")
        (self.fake_bin / "sleep").chmod(0o755)
        (self.work_dir / "k3s-output").write_text("set\n")
        return self.run_read(OPS_DIR / "redis-auth-restart.sh", *script_args)

    def steps_sent(self):
        sent_texts = (self.work_dir / "aws-calls").read_text().split("ssm send-command")[1:]
        return [next(step_name for step_name, step_marker, _, _ in RESTART_STEPS if step_marker in sent_text)
                for sent_text in sent_texts]

    def redis_deleted(self):
        calls_path = self.work_dir / "k3s-calls"
        return calls_path.is_file() and "delete pod redis-auth-0" in calls_path.read_text()

    def test_the_script_reaches_the_control_plane_only_through_the_shared_function(self):
        self.assertNotIn("send-command --region", (OPS_DIR / "redis-auth-restart.sh").read_text())

    def test_every_step_succeeding_restarts_redis_then_the_exporter(self):
        redis_run = self.restart_run("--apply")
        self.assertEqual(redis_run.returncode, 0, redis_run.stderr)
        self.assertIn("redis-auth-0 Ready and requires AUTH", redis_run.stdout)
        self.assertIn("auth exporter Ready", redis_run.stdout)
        self.assertEqual(self.steps_sent(), ["pre-check", "Redis restart", "exporter restart"])

    def test_a_dry_run_sends_only_the_pre_check(self):
        redis_run = self.restart_run("--dry-run")
        self.assertEqual(redis_run.returncode, 0, redis_run.stderr)
        self.assertIn("pre-checks passed; re-run with --apply to restart", redis_run.stdout)
        self.assertEqual(self.steps_sent(), ["pre-check"])

    def test_a_succeeded_restart_that_reports_redis_not_ready_leaves_the_exporter(self):
        (self.work_dir / "wait-fails").write_text("")
        redis_run = self.restart_run("--apply")
        self.assertEqual((self.work_dir / "status").read_text(), "Success\n")
        self.assertEqual(redis_run.returncode, 1)
        self.assertIn("REDIS_NOT_READY", redis_run.stdout.splitlines())
        self.assertIn("redis-auth-0 not Ready; exporter left untouched", redis_run.stdout)
        self.assertEqual(self.steps_sent(), ["pre-check", "Redis restart"])

    def test_exporter_only_checks_auth_then_restarts_only_the_exporter(self):
        exporter_run = self.restart_run("--exporter-only")
        self.assertEqual(exporter_run.returncode, 0, exporter_run.stderr)
        self.assertIn("redis-auth-0 requires AUTH", exporter_run.stdout)
        self.assertIn("auth exporter Ready", exporter_run.stdout)
        self.assertEqual(self.steps_sent(), ["AUTH check", "exporter restart"])
        self.assertFalse(self.redis_deleted())

    def test_exporter_only_restarts_nothing_while_redis_does_not_require_auth(self):
        (self.work_dir / "auth-off").write_text("")
        exporter_run = self.restart_run("--exporter-only")
        self.assertEqual(exporter_run.returncode, 1)
        self.assertIn("AUTH_NOT_REQUIRED", exporter_run.stdout.splitlines())
        self.assertIn("redis-auth-0 does not require AUTH; exporter left untouched; run --apply", exporter_run.stdout)
        self.assertEqual(self.steps_sent(), ["AUTH check"])
        self.assertFalse(self.redis_deleted())

    def test_a_step_that_does_not_succeed_stops_with_its_ending_and_next_action(self):
        step_rows = {one_step[0]: one_step for one_step in RESTART_STEPS}
        for mode_flag, mode_steps in MODE_STEPS.items():
            for step_index, step_name in enumerate(mode_steps):
                self.check_step_faults(mode_flag, mode_steps[:step_index + 1], step_rows[step_name])

    def check_step_faults(self, mode_flag, steps_until, step_row):
        step_name, step_marker, output_line, verdict_lines = step_row
        for fault_name, fault_file, ending_name, output_printed, fault_text in STEP_FAULTS:
            with self.subTest(mode_flag=mode_flag, step_name=step_name, fault_name=fault_name):
                for state_name in ("aws-calls", "failing-command", "failing-status", "send-fails",
                                   "output-read-fails"):
                    (self.work_dir / state_name).unlink(missing_ok=True)
                (self.work_dir / fault_file).write_text(step_marker)
                if fault_file == "failing-command":
                    (self.work_dir / "failing-status").write_text(f"{fault_name}\n")
                redis_run = self.restart_run(mode_flag)
                self.assertEqual(redis_run.returncode, 1)
                self.assertIn(f"the {step_name} command ended {ending_name} on the control plane; stopping",
                              redis_run.stderr)
                self.assertIn(fault_text, redis_run.stderr)
                self.assertIn(next_action(step_name, ending_name), redis_run.stderr)
                self.assertEqual(RERUN_RESTARTS_REDIS in redis_run.stderr,
                                 next_action(step_name, ending_name) == RERUN_RESTARTS_REDIS)
                self.assertEqual("ops verify fleet --site oregon" in redis_run.stderr,
                                 step_name in ("Redis restart", "exporter restart") and ending_name != "NOT_SENT")
                self.assertEqual(output_line in redis_run.stdout.splitlines(), output_printed)
                self.assertEqual(self.steps_sent(), list(steps_until))
                for verdict_line in verdict_lines:
                    self.assertNotIn(verdict_line, redis_run.stdout)


HELPER_MARKER = "helper-remote-ran"
HELPER_ENDINGS = (("Success", {}, "SUCCESS"),
                  ("Failed", {"failing-command": HELPER_MARKER, "failing-status": "Failed\n"}, "SENT_FAILED"),
                  ("Failed, output unreadable", {"failing-command": HELPER_MARKER, "failing-status": "Failed\n",
                                                 "output-read-fails": HELPER_MARKER}, "SENT_OUTCOME_UNKNOWN"),
                  ("TimedOut", {"failing-command": HELPER_MARKER, "failing-status": "TimedOut\n"},
                   "SENT_OUTCOME_UNKNOWN"),
                  ("Cancelled", {"failing-command": HELPER_MARKER, "failing-status": "Cancelled\n"},
                   "SENT_OUTCOME_UNKNOWN"),
                  ("local bound", {"failing-command": HELPER_MARKER, "failing-status": "InProgress\n"},
                   "SENT_OUTCOME_UNKNOWN"),
                  ("Success, output unreadable", {"output-read-fails": HELPER_MARKER}, "SENT_OUTCOME_UNKNOWN"),
                  ("send fails", {"send-fails": HELPER_MARKER}, "NOT_SENT"))
HELPER_CALL = f"""source "{OPS_DIR}/lib/common.sh"
source "{OPS_DIR}/lib/control-plane.sh"
helper_output=$(run_on_control_plane us-west-2 i-0cc "echo {HELPER_MARKER}" 10)
helper_code=$?
printf '%s\\n' "$helper_output"
exit "$helper_code"
"""


class ControlPlaneEndingTest(FakeFleetCase):
    def helper_run(self, fault_files, **extra_env):
        for state_name in ("aws-calls", "failing-command", "failing-status", "send-fails", "output-read-fails"):
            (self.work_dir / state_name).unlink(missing_ok=True)
        for state_name, state_text in fault_files.items():
            (self.work_dir / state_name).write_text(state_text)
        (self.fake_bin / "sleep").write_text("#!/bin/sh\nexit 0\n")
        (self.fake_bin / "sleep").chmod(0o755)
        helper_run = subprocess.run(["bash", "-c", HELPER_CALL], capture_output=True, text=True,
                                    env=self.fake_env(**extra_env), stdin=subprocess.DEVNULL)
        return helper_run.returncode, helper_run.stdout, helper_run.stderr

    def test_each_ending_is_written_through_command_substitution(self):
        ending_path = self.work_dir / "ending"
        for fault_name, fault_files, ending_name in HELPER_ENDINGS:
            with self.subTest(fault_name=fault_name):
                ending_path.unlink(missing_ok=True)
                return_code, _, _ = self.helper_run(fault_files, CONTROL_PLANE_ENDING_FILE=str(ending_path))
                self.assertEqual((return_code == 0, ending_path.read_text()), (ending_name == "SUCCESS",
                                                                             f"{ending_name}\n"))

    def test_a_caller_without_the_variable_sees_the_same_code_and_output(self):
        ending_path = self.work_dir / "ending"
        for fault_name, fault_files, _ in HELPER_ENDINGS:
            with self.subTest(fault_name=fault_name):
                reported_run = self.helper_run(fault_files, CONTROL_PLANE_ENDING_FILE=str(ending_path))
                ending_path.unlink()
                files_before = sorted(self.work_dir.iterdir())
                plain_run = self.helper_run(fault_files)
                self.assertEqual(plain_run, reported_run)
                self.assertEqual(sorted(self.work_dir.iterdir()), files_before)


for read_class in (FleetNodesTest, ArgocdAppTest, RedisRestartTest):
    with_second_shell(read_class, globals())


if __name__ == "__main__":
    unittest.main()
