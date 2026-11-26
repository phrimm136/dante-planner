import datetime
import os
import re
import subprocess
import tempfile
import unittest
from pathlib import Path

from shells import FIRST_SHELL, with_second_shell

SCRIPT = Path(__file__).resolve().parents[1] / "fleet-verify.sh"
FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures"
INTERVAL_SECONDS = 21600
ANSI_PATTERN = re.compile(r"\x1b\[[0-9;]*m")
METRIC_PATH = "/api/v1/namespaces/danteplanner/services/kube-state-metrics:8080/proxy/metrics"
CLOCK_NOW = 1767225600
FAKE_DATE = f"""#!/bin/sh
[ "$*" = +%s ] && {{ printf '%s\\n' {CLOCK_NOW}; exit 0; }}
PATH=${{PATH#*:}}; export PATH; exec date "$@"
"""

FAKE_K3S = """#!/bin/sh
case "$*" in
  *"-l etcd.k3s.cattle.io/snapshot-storage-node=s3 -o name")
    [ -n "${FAKE_SNAPSHOT_ERROR:-}" ] && { printf '%s\n' "$FAKE_SNAPSHOT_ERROR" >&2; exit 1; }
    s3_left=$(cat "$FAKE_DIR/s3-count")
    while [ "$s3_left" -gt 0 ]; do printf '%s\n' "etcdsnapshotfile.k3s.cattle.io/s3-snap-$s3_left"; s3_left=$((s3_left - 1)); done
    exit 0 ;;
  "kubectl get etcdsnapshotfiles.k3s.cattle.io "*)
    [ -n "${FAKE_SNAPSHOT_ERROR:-}" ] && { printf '%s\n' "$FAKE_SNAPSHOT_ERROR" >&2; exit 1; }
    [ -f "$FAKE_DIR/snapshot-times" ] && while IFS= read -r one_time; do printf 'row %s\\n' "$one_time"; done < "$FAKE_DIR/snapshot-times"
    exit 0 ;;
  "kubectl get --raw """ + METRIC_PATH + """")
    [ -n "${FAKE_METRIC_ERROR:-}" ] && { printf '%s\n' "$FAKE_METRIC_ERROR" >&2; exit 1; }
    cat "$FAKE_DIR/metrics"
    exit 0 ;;
esac
exit 0
"""


def iso_time(seconds_ago):
    moment = datetime.datetime.fromtimestamp(CLOCK_NOW, datetime.timezone.utc) - datetime.timedelta(seconds=seconds_ago)
    return moment.strftime("%Y-%m-%dT%H:%M:%SZ")


def metric_text(series_count):
    sample_lines = [f'kube_etcd_snapshot_creation_timestamp_seconds{{name="s{index}",node="cp"}} 1.7e+09'
                    for index in range(series_count)]
    return "\n".join(["# HELP kube_etcd_snapshot_creation_timestamp_seconds x",
                      "# TYPE kube_etcd_snapshot_creation_timestamp_seconds gauge", *sample_lines,
                      'kube_node_info{node="cp"} 1']) + "\n"


def run_bash(script_text, *script_args, extra_env=None):
    return subprocess.run(["bash", "-c", f'source "{SCRIPT}"\n{script_text}', "fleet-verify-test", *script_args],
                          capture_output=True, text=True, env={**os.environ, **(extra_env or {})})


class FleetVerifyTest(unittest.TestCase):
    shell_path = FIRST_SHELL

    def setUp(self):
        self.work_handle = tempfile.TemporaryDirectory()
        self.work_dir = Path(self.work_handle.name)
        fake_bin = self.work_dir / "bin"
        fake_bin.mkdir()
        (fake_bin / "k3s").write_text(FAKE_K3S.replace("#!/bin/sh\n", f"#!{self.shell_path}\n", 1))
        (fake_bin / "k3s").chmod(0o755)
        (fake_bin / "date").write_text(FAKE_DATE.replace("#!/bin/sh\n", f"#!{self.shell_path}\n", 1))
        (fake_bin / "date").chmod(0o755)
        self.base_env = {"PATH": f"{fake_bin}:{os.environ['PATH']}", "FAKE_DIR": str(self.work_dir)}

    def tearDown(self):
        self.work_handle.cleanup()

    def remote_output(self, snapshot_ages=(), series_count=0, extra_env=None, raw_times=None, s3_count=None):
        times = raw_times if raw_times is not None else [iso_time(one_age) for one_age in snapshot_ages]
        if times:
            (self.work_dir / "snapshot-times").write_text("\n".join(times) + "\n")
        (self.work_dir / "s3-count").write_text(str(len(times) if s3_count is None else s3_count))
        (self.work_dir / "metrics").write_text(metric_text(series_count))
        remote_text = run_bash("remote_checks").stdout
        remote_run = subprocess.run([self.shell_path, "-c", remote_text], capture_output=True, text=True,
                                    env={**os.environ, **self.base_env, **(extra_env or {})})
        return remote_run.stdout

    def snapshot_verdict(self, output_text):
        verdict_run = run_bash('snapshot_verdict "$1" "$2"', output_text, str(INTERVAL_SECONDS))
        plain_lines = [ANSI_PATTERN.sub("", line_text) for line_text in verdict_run.stdout.splitlines()]
        return verdict_run.returncode, [line_text.split("] ", 1)[1] for line_text in plain_lines]

    def test_fresh_snapshots_pass(self):
        output_text = self.remote_output(snapshot_ages=(30000, 3600), series_count=2)
        self.assertIn("count=2\ns3_count=2", output_text)
        exit_code, verdict_lines = self.snapshot_verdict(output_text)
        self.assertEqual(exit_code, 0, verdict_lines)
        self.assertRegex(verdict_lines[0], r"^SNAPSHOTS: count 2, 2 in S3, newest 3600s old \(limit 32400s\)$")
        self.assertEqual(verdict_lines[1], "SNAPSHOT METRIC: 2 kube_etcd_snapshot_creation_timestamp_seconds series")

    def test_zero_snapshots_are_fatal(self):
        output_text = self.remote_output()
        self.assertIn("count=0", output_text)
        self.assertNotIn("newest_age_seconds", output_text)
        self.assertEqual(self.snapshot_verdict(output_text),
                         (1, ["SNAPSHOTS: count 0, no etcd snapshot exists",
                              "SNAPSHOT METRIC: 0 kube_etcd_snapshot_creation_timestamp_seconds series"]))

    def test_recorded_zero_snapshot_output_is_fatal(self):
        exit_code, verdict_lines = self.snapshot_verdict((FIXTURE_DIR / "fleet-verify-zero-snapshots.txt").read_text())
        self.assertEqual(exit_code, 1)
        self.assertEqual(verdict_lines[0], "SNAPSHOTS: count 0, no etcd snapshot exists")

    def test_a_stale_newest_snapshot_is_fatal(self):
        exit_code, verdict_lines = self.snapshot_verdict(self.remote_output(snapshot_ages=(40000,), series_count=1))
        self.assertEqual(exit_code, 1)
        self.assertRegex(verdict_lines[0], r"^SNAPSHOTS: count 1, 1 in S3, newest 40000s old, over 32400s "
                                           r"\(1\.5 x the 21600s schedule\)$")

    def test_snapshots_only_on_the_node_are_fatal(self):
        output_text = self.remote_output(snapshot_ages=(60, 120), series_count=2, s3_count=0)
        self.assertIn("count=2\ns3_count=0", output_text)
        exit_code, verdict_lines = self.snapshot_verdict(output_text)
        self.assertEqual((exit_code, verdict_lines[0]),
                         (1, "SNAPSHOTS: count 2, 0 in S3; snapshots that never leave the node are not a backup"))

    def test_a_missing_s3_count_is_fatal(self):
        exit_code, verdict_lines = self.snapshot_verdict("=== SNAPSHOTS ===\ncount=1\nnewest_age_seconds=60\n")
        self.assertEqual((exit_code, verdict_lines[0]), (1, "SNAPSHOTS: count 1, no S3 count reported"))

    def test_snapshots_without_a_metric_series_are_fatal(self):
        exit_code, verdict_lines = self.snapshot_verdict(self.remote_output(snapshot_ages=(60,), series_count=0))
        self.assertEqual(exit_code, 1)
        self.assertEqual(verdict_lines[1],
                         "SNAPSHOT METRIC: 0 kube_etcd_snapshot_creation_timestamp_seconds series for 1 snapshots")

    def test_an_unparsable_creation_time_is_fatal(self):
        output_text = self.remote_output(raw_times=["not-a-time"], series_count=1)
        self.assertIn("SNAPSHOT_TIME_UNPARSABLE", output_text)
        self.assertEqual(self.snapshot_verdict(output_text)[1][0],
                         "SNAPSHOTS: count 1, the newest creationTime does not parse as a date")

    def test_a_missing_crd_is_fatal(self):
        output_text = self.remote_output(extra_env={
            "FAKE_SNAPSHOT_ERROR": 'error: the server doesn\'t have a resource type "etcdsnapshotfiles"'})
        self.assertEqual(self.snapshot_verdict(output_text)[1][0], "SNAPSHOTS: the ETCDSnapshotFile CRD is missing")

    def test_a_failed_snapshot_list_is_fatal(self):
        output_text = self.remote_output(extra_env={"FAKE_SNAPSHOT_ERROR": "The connection to the server was refused"})
        self.assertEqual(self.snapshot_verdict(output_text)[1][0],
                         "SNAPSHOTS: listing etcdsnapshotfiles.k3s.cattle.io failed")

    def test_kube_state_metrics_down_is_fatal(self):
        output_text = self.remote_output(snapshot_ages=(60,), extra_env={
            "FAKE_METRIC_ERROR": 'Error from server (ServiceUnavailable): no endpoints available for service '
                                 '"kube-state-metrics:8080"'})
        exit_code, verdict_lines = self.snapshot_verdict(output_text)
        self.assertEqual((exit_code, verdict_lines[1]), (1, "SNAPSHOT METRIC: kube-state-metrics has no ready endpoint"))

    def test_a_failed_proxy_call_is_fatal(self):
        output_text = self.remote_output(snapshot_ages=(60,), extra_env={
            "FAKE_METRIC_ERROR": "Error from server (InternalError): an error on the server"})
        self.assertEqual(self.snapshot_verdict(output_text)[1][1],
                         "SNAPSHOT METRIC: the API server proxy call to kube-state-metrics failed")

    def test_missing_sections_are_fatal_not_a_crash(self):
        self.assertEqual(self.snapshot_verdict("=== NODES ===\n"),
                         (1, ["SNAPSHOTS: no snapshot count reported", "SNAPSHOT METRIC: no series count reported"]))

    def test_a_snapshot_failure_fails_the_whole_verdict(self):
        output_text = self.remote_output()
        verdict_run = run_bash('verdict "$1" "$2"', output_text, str(INTERVAL_SECONDS))
        self.assertEqual(verdict_run.returncode, 1)
        self.assertIn("fleet is NOT healthy", verdict_run.stdout)


FAKE_AWS = """#!/bin/sh
case "$1 $2" in
  "ssm send-command") printf '%s\n' cmd-1 ;;
  "ssm get-command-invocation")
    case "$*" in
      *"--query Status"*) printf '%s\n' "$FAKE_STATUS" ;;
      *) printf '%s\n' "=== NODES ===" ;;
    esac ;;
esac
"""


class CommandStatusTest(unittest.TestCase):
    def setUp(self):
        self.work_handle = tempfile.TemporaryDirectory()
        self.fake_bin = Path(self.work_handle.name)
        for tool_name, tool_text in (("aws", FAKE_AWS), ("sleep", "#!/bin/sh\nexit 0\n")):
            (self.fake_bin / tool_name).write_text(tool_text)
            (self.fake_bin / tool_name).chmod(0o755)

    def tearDown(self):
        self.work_handle.cleanup()

    def run_main(self, status_text):
        return run_bash("CHECK_BOUND_SECONDS=1; POLL_SECONDS=1; main seoul",
                        extra_env={"PATH": f"{self.fake_bin}:{os.environ['PATH']}", "CP_INSTANCE_ID": "i-0test",
                                   "FAKE_STATUS": status_text})

    def test_a_command_that_never_finishes_is_a_named_failure(self):
        main_run = self.run_main("InProgress")
        self.assertEqual(main_run.returncode, 1)
        self.assertIn("the control plane did not answer within 1 seconds; status InProgress", main_run.stderr)
        self.assertNotIn("none reported", main_run.stdout + main_run.stderr)

    def test_a_command_that_ends_in_failure_is_a_named_failure(self):
        main_run = self.run_main("TimedOut")
        self.assertEqual(main_run.returncode, 1)
        self.assertIn("the check command on the control plane ended with status TimedOut", main_run.stderr)
        self.assertNotIn("none reported", main_run.stdout + main_run.stderr)

    def test_a_finished_command_reaches_the_verdict(self):
        main_run = self.run_main("Success")
        self.assertEqual(main_run.returncode, 1)
        self.assertIn("NODES: none reported", ANSI_PATTERN.sub("", main_run.stdout))


with_second_shell(FleetVerifyTest, globals())


if __name__ == "__main__":
    unittest.main()
