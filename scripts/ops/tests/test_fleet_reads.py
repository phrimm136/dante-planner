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

FAKE_AWS = """#!/bin/sh
printf '%s\\n' "$*" >> "$FAKE_DIR/aws-calls"
case "$1 $2" in
  "sts get-caller-identity") printf '%s\\n' 111122223333 ;;
  "ssm send-command")
    while [ "$1" != "--parameters" ]; do shift; done
    printf '%s\\n' "$2" | jq -r '.commands[]' > "$FAKE_DIR/remote"
    "$FAKE_SHELL" "$FAKE_DIR/remote" > "$FAKE_DIR/stdout" 2> "$FAKE_DIR/stderr" \\
      && printf '%s\\n' Success > "$FAKE_DIR/status" || printf '%s\\n' Failed > "$FAKE_DIR/status"
    [ -f "$FAKE_DIR/failing-command" ] && grep -qF "$(cat "$FAKE_DIR/failing-command")" "$FAKE_DIR/remote" \\
      && printf '%s\\n' Failed > "$FAKE_DIR/status"
    printf '%s\\n' cmd-1 ;;
  "ssm get-command-invocation")
    case "$*" in
      *"--query Status"*) cat "$FAKE_DIR/status" ;;
      *StandardErrorContent*) cat "$FAKE_DIR/stderr" ;;
      *) cat "$FAKE_DIR/stdout" ;;
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
esac
cat "$FAKE_DIR/k3s-output"
"""


def node_line(node_name, private_ip, ready_text="True", role_text="app", cp_text=""):
    return "\t".join([node_name, private_ip, ready_text, role_text, cp_text])


def instance_row(instance_id, private_ip, role_text, age_seconds, **extra_tags):
    launch_text = time.strftime("%Y-%m-%dT%H:%M:%S+00:00", time.gmtime(time.time() - age_seconds))
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
        for tool_name, tool_text in (("aws", FAKE_AWS), ("k3s", FAKE_K3S)):
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


class RedisRestartTest(FakeFleetCase):
    def test_a_failed_remote_command_still_reports_what_it_printed(self):
        redis_text = (OPS_DIR / "redis-auth-restart.sh").read_text()
        self.assertNotIn("send-command --region", redis_text)
        (self.work_dir / "k3s-fails").write_text("")
        (self.work_dir / "failing-command").write_text("=== SECRET ===")
        redis_run = self.run_read(OPS_DIR / "redis-auth-restart.sh", "--dry-run")
        self.assertEqual((self.work_dir / "status").read_text(), "Failed\n")
        self.assertEqual(redis_run.returncode, 1)
        self.assertIn("key-absent", redis_run.stdout)
        self.assertIn("exec-failed", redis_run.stdout)
        self.assertIn("pre-checks failed; not restarting", redis_run.stdout)
        self.assertIn("ended with status Failed", redis_run.stderr)
        self.assertIn("connection refused", redis_run.stderr)

    def test_a_failed_restart_command_still_reports_that_redis_is_not_ready(self):
        (self.fake_bin / "sleep").write_text("#!/bin/sh\nexit 0\n")
        (self.fake_bin / "sleep").chmod(0o755)
        (self.work_dir / "k3s-output").write_text("set\n")
        (self.work_dir / "wait-fails").write_text("")
        (self.work_dir / "failing-command").write_text("REDIS_NOT_READY")
        redis_run = self.run_read(OPS_DIR / "redis-auth-restart.sh", "--apply")
        self.assertEqual((self.work_dir / "status").read_text(), "Failed\n")
        self.assertEqual(redis_run.returncode, 1)
        self.assertIn("REDIS_NOT_READY", redis_run.stdout.splitlines())
        self.assertIn("redis-auth-0 not Ready; exporter left untouched", redis_run.stdout)
        self.assertIn("ended with status Failed", redis_run.stderr)
        self.assertIn("timed out waiting", redis_run.stderr)
        self.assertNotIn("app=redis-exporter-auth --wait", (self.work_dir / "k3s-calls").read_text())


for read_class in (FleetNodesTest, ArgocdAppTest, RedisRestartTest):
    with_second_shell(read_class, globals())


if __name__ == "__main__":
    unittest.main()
