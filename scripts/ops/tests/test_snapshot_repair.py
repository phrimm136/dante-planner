import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "provision" / "k3s-snapshot-schedule-repair.sh"
FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures"
MALFORMED_LINE = "\t'0 \\*/6 \\* \\* \\*' \\"
REPAIRED_LINE = "\t'0 */6 * * *' \\"

FAKE_K3S = """#!/bin/sh
echo "k3s $*" >> "$FAKE_DIR/calls"
case "$*" in
  "etcd-snapshot save")
    [ -n "${FAKE_SAVE_FAILS:-}" ] && exit 1
    echo local >> "$FAKE_DIR/snapshots"
    [ -n "${FAKE_S3_FAILS:-}" ] || echo s3 >> "$FAKE_DIR/snapshots" ;;
  "kubectl get etcdsnapshotfiles.k3s.cattle.io -o name")
    [ -f "$FAKE_DIR/snapshots" ] && sed 's/^/etcdsnapshotfile.k3s.cattle.io\\/snap-/' "$FAKE_DIR/snapshots" ;;
  "kubectl get etcdsnapshotfiles.k3s.cattle.io -l etcd.k3s.cattle.io/snapshot-storage-node=s3 -o name")
    [ -f "$FAKE_DIR/snapshots" ] && sed -n 's/^s3$/etcdsnapshotfile.k3s.cattle.io\\/snap-s3/p' "$FAKE_DIR/snapshots" ;;
  "kubectl get --raw /readyz")
    [ -n "${FAKE_NEVER_READY:-}" ] && exit 1
    echo ok ;;
  "kubectl get nodes --no-headers")
    echo "node-a   Ready    control-plane,etcd   1d   v1.36.3+k3s1" ;;
esac
"""

FAKE_SYSTEMCTL = """#!/bin/sh
echo "systemctl $*" >> "$FAKE_DIR/calls"
case "$*" in
  "is-active"*)
    [ -n "${FAKE_K3S_DOWN:-}" ] && { echo inactive; exit 3; }
    echo active ;;
  "show -p KillMode k3s")
    echo "KillMode=${FAKE_KILL_MODE:-process}" ;;
esac
"""

FAKE_AWS = """#!/bin/sh
echo "aws $*" >> "$FAKE_DIR/aws-calls"
case "$1 $2" in
  "sts get-caller-identity") echo "acct" ;;
  "ssm send-command")
    while [ $# -gt 0 ]; do [ "$1" = --parameters ] && printf '%s' "$2" > "$FAKE_DIR/parameters"; shift; done
    echo cmd-1 ;;
  "ssm get-command-invocation")
    case "$*" in
      *"--query Status"*) echo "${FAKE_STATUS:-Success}" ;;
      *) printf '{"StandardOutputContent": "remote out", "StandardErrorContent": ""}' ;;
    esac ;;
esac
"""


def write_tool(bin_dir, tool_name, tool_text):
    tool_path = bin_dir / tool_name
    tool_path.write_text(tool_text)
    tool_path.chmod(0o755)


class RepairCase(unittest.TestCase):
    def setUp(self):
        self.work_handle = tempfile.TemporaryDirectory()
        self.work_dir = Path(self.work_handle.name)
        bin_dir = self.work_dir / "bin"
        bin_dir.mkdir()
        write_tool(bin_dir, "k3s", FAKE_K3S)
        write_tool(bin_dir, "systemctl", FAKE_SYSTEMCTL)
        write_tool(bin_dir, "aws", FAKE_AWS)
        self.unit_path = self.work_dir / "k3s.service"
        self.unit_path.write_text((FIXTURE_DIR / "k3s.service").read_text())
        self.base_env = {**os.environ, "PATH": f"{bin_dir}:{os.environ['PATH']}", "FAKE_DIR": str(self.work_dir)}

    def tearDown(self):
        self.work_handle.cleanup()

    def remote_text(self, mode_name):
        build_run = subprocess.run(["bash", "-c", f'source "{SCRIPT}"; remote_script "$@"', "repair-test", mode_name,
                                    str(self.unit_path), "0 */6 * * *", "2", "1"],
                                   capture_output=True, text=True, check=True)
        return build_run.stdout

    def run_remote(self, mode_name, **fake_flags):
        return subprocess.run(["sh", "-c", self.remote_text(mode_name)], capture_output=True, text=True,
                              env={**self.base_env, **{flag_name: "1" if flag_value is True else flag_value
                                                       for flag_name, flag_value in fake_flags.items()}})

    def calls(self):
        calls_path = self.work_dir / "calls"
        return calls_path.read_text().splitlines() if calls_path.is_file() else []

    def unit_lines(self):
        return self.unit_path.read_text().splitlines()

    def backups(self):
        return sorted(self.work_dir.glob("k3s.service.bak-*"))


class RemoteScriptTest(RepairCase):
    def test_the_repair_saves_backs_up_replaces_restarts_and_reports(self):
        original_text = self.unit_path.read_text()
        repair_run = self.run_remote("repair")
        self.assertEqual(repair_run.returncode, 0, repair_run.stdout)
        self.assertEqual([one_line.split(":")[0] for one_line in repair_run.stdout.splitlines()
                          if one_line.startswith("STEP")], ["STEP 1", "STEP 2", "STEP 3", "STEP 4", "STEP 5", "STEP 6"])
        self.assertLess(self.calls().index("systemctl is-active --quiet k3s"), self.calls().index("k3s etcd-snapshot save"))
        self.assertLess(self.calls().index("k3s etcd-snapshot save"), self.calls().index("systemctl daemon-reload"))
        self.assertLess(self.calls().index("systemctl daemon-reload"), self.calls().index("systemctl restart k3s"))
        self.assertEqual(original_text.replace(MALFORMED_LINE, REPAIRED_LINE), self.unit_path.read_text())
        self.assertEqual([one_backup.read_text() for one_backup in self.backups()], [original_text])
        self.assertIn("snapshots: local 1, s3 1", repair_run.stdout)
        self.assertNotIn("snap-", repair_run.stdout)
        self.assertIn("unit cron line: '0 */6 * * *' \\", repair_run.stdout)
        self.assertFalse((self.work_dir / "k3s.service.repair").exists())

    def test_a_second_run_changes_nothing_and_says_so(self):
        self.assertEqual(self.run_remote("repair").returncode, 0)
        repaired_text = self.unit_path.read_text()
        call_count = len(self.calls())
        rerun = self.run_remote("repair")
        self.assertEqual(rerun.returncode, 0, rerun.stdout)
        self.assertIn("ALREADY REPAIRED", rerun.stdout)
        self.assertEqual(self.unit_path.read_text(), repaired_text)
        self.assertEqual(len(self.backups()), 1)
        self.assertNotIn("k3s etcd-snapshot save", self.calls()[call_count:])
        self.assertNotIn("systemctl restart k3s", self.calls()[call_count:])

    def test_a_hand_edited_line_is_refused_before_any_change(self):
        self.unit_path.write_text(self.unit_path.read_text().replace(MALFORMED_LINE, "\t'0 3 * * *' \\"))
        edited_text = self.unit_path.read_text()
        refused_run = self.run_remote("repair")
        self.assertEqual(refused_run.returncode, 1)
        self.assertIn("FAILED step check: line ", refused_run.stdout)
        self.assertIn("is not the known malformed line", refused_run.stdout)
        self.assertEqual((self.unit_path.read_text(), self.backups(), self.calls()), (edited_text, [], []))

    def test_a_missing_or_doubled_flag_is_refused(self):
        original_text = self.unit_path.read_text()
        self.unit_path.write_text(original_text.replace("\t'--etcd-snapshot-schedule-cron' \\\n", ""))
        self.assertIn("argument 0 times, not once", self.run_remote("repair").stdout)
        self.unit_path.write_text(original_text + "\t'--etcd-snapshot-schedule-cron' \\\n")
        self.assertIn("argument 2 times, not once", self.run_remote("repair").stdout)
        self.assertEqual(self.backups(), [])

    def test_a_missing_unit_is_refused(self):
        self.unit_path.unlink()
        self.assertIn("FAILED step check:", self.run_remote("repair").stdout)

    def test_k3s_down_stops_at_step_one_without_a_change(self):
        original_text = self.unit_path.read_text()
        down_run = self.run_remote("repair", FAKE_K3S_DOWN=True)
        self.assertEqual(down_run.returncode, 1)
        self.assertIn("FAILED step 1: k3s is not active", down_run.stdout)
        self.assertEqual((self.unit_path.read_text(), self.backups()), (original_text, []))

    def test_a_save_without_an_s3_copy_stops_at_step_one(self):
        original_text = self.unit_path.read_text()
        s3_run = self.run_remote("repair", FAKE_S3_FAILS=True)
        self.assertEqual(s3_run.returncode, 1)
        self.assertIn("FAILED step 1: no S3 copy of the new snapshot is listed 2s after the save (1 new local); "
                      "the local snapshot was written but the S3 copy is missing; read the k3s journal for the "
                      "upload error", s3_run.stdout)
        self.assertEqual((self.unit_path.read_text(), self.backups()), (original_text, []))
        self.assertNotIn("systemctl restart k3s", self.calls())

    def test_an_older_s3_snapshot_does_not_count_for_this_save(self):
        (self.work_dir / "snapshots").write_text("local\ns3\n")
        s3_run = self.run_remote("repair", FAKE_S3_FAILS=True)
        self.assertIn("the local snapshot was written but the S3 copy is missing", s3_run.stdout)

    def test_a_kill_mode_other_than_process_is_refused_before_any_change(self):
        original_text = self.unit_path.read_text()
        mode_run = self.run_remote("repair", FAKE_KILL_MODE="control-group")
        self.assertEqual(mode_run.returncode, 1)
        self.assertIn("FAILED step check: k3s runs with KillMode=control-group, not KillMode=process", mode_run.stdout)
        self.assertEqual((self.unit_path.read_text(), self.backups()), (original_text, []))
        self.assertNotIn("k3s etcd-snapshot save", self.calls())

    def test_a_failed_save_stops_at_step_one(self):
        save_run = self.run_remote("repair", FAKE_SAVE_FAILS=True)
        self.assertIn("FAILED step 1: k3s etcd-snapshot save failed", save_run.stdout)
        self.assertEqual(self.backups(), [])

    def test_a_cluster_that_never_gets_ready_is_restored_and_restarted(self):
        original_text = self.unit_path.read_text()
        never_run = self.run_remote("repair", FAKE_NEVER_READY=True)
        self.assertEqual(never_run.returncode, 1)
        self.assertIn("FAILED step 5: the API server or a node was not ready within 2s; restored", never_run.stdout)
        self.assertEqual(self.unit_path.read_text(), original_text)
        self.assertEqual(self.calls().count("systemctl restart k3s"), 2)
        self.assertEqual(self.calls().count("systemctl daemon-reload"), 2)

    def test_the_dry_run_only_reads(self):
        original_text = self.unit_path.read_text()
        dry_run = self.run_remote("dry-run")
        self.assertEqual(dry_run.returncode, 0, dry_run.stdout)
        self.assertIn("unit line ", dry_run.stdout)
        self.assertIn("as found: '0 \\*/6 \\* \\* \\*' \\", dry_run.stdout)
        self.assertIn("k3s: active", dry_run.stdout)
        self.assertIn("unit KillMode line: KillMode=process\neffective KillMode=process", dry_run.stdout)
        self.assertIn("snapshots: local 0, s3 0", dry_run.stdout)
        self.assertIn("would: k3s etcd-snapshot save;", dry_run.stdout)
        self.assertEqual((self.unit_path.read_text(), self.backups()), (original_text, []))
        self.assertEqual(self.calls(), ["systemctl show -p KillMode k3s", "systemctl is-active k3s",
                                        "k3s kubectl get etcdsnapshotfiles.k3s.cattle.io -o name",
                                        "k3s kubectl get etcdsnapshotfiles.k3s.cattle.io "
                                        "-l etcd.k3s.cattle.io/snapshot-storage-node=s3 -o name"])

    def test_the_dry_run_counts_local_and_s3_apart(self):
        (self.work_dir / "snapshots").write_text("local\nlocal\ns3\n")
        self.assertIn("snapshots: local 2, s3 1", self.run_remote("dry-run").stdout)

    def test_the_remote_script_is_posix_sh(self):
        for mode_name in ("dry-run", "repair"):
            lint_run = subprocess.run(["shellcheck", "-s", "sh", "-"], input=self.remote_text(mode_name),
                                      capture_output=True, text=True)
            self.assertEqual(lint_run.returncode, 0, lint_run.stdout)


class LocalScriptTest(RepairCase):
    def run_local(self, *script_args, **extra_env):
        return subprocess.run([str(SCRIPT), *script_args], capture_output=True, text=True,
                              env={**self.base_env, "CP_INSTANCE_ID": "i-0test", **extra_env})

    def sent_script(self):
        return "\n".join(json.loads((self.work_dir / "parameters").read_text())["commands"])

    def test_the_site_picks_the_region_and_the_mode_reaches_the_cp(self):
        local_run = self.run_local("--site", "seoul", "--dry-run")
        self.assertEqual(local_run.returncode, 0, local_run.stderr)
        aws_calls = (self.work_dir / "aws-calls").read_text()
        self.assertIn("ssm send-command --region ap-northeast-2 --instance-ids i-0test", aws_calls)
        self.assertIn("MODE=dry-run\nUNIT='/etc/systemd/system/k3s.service'\nCRON='0 */6 * * *'", self.sent_script())
        self.assertIn("remote out", local_run.stdout)
        self.run_local("--site", "oregon")
        self.assertIn("ssm send-command --region us-west-2", (self.work_dir / "aws-calls").read_text())
        self.assertIn("MODE=repair", self.sent_script())

    def test_a_failed_remote_command_fails_the_run(self):
        failed_run = self.run_local("--site", "oregon", FAKE_STATUS="Failed")
        self.assertEqual(failed_run.returncode, 1)
        self.assertIn("remote out", failed_run.stdout)
        self.assertIn("FAILED", failed_run.stderr)

    def test_a_bad_site_or_option_prints_the_usage(self):
        for bad_args in ((), ("--site", "tokyo"), ("--site",), ("--force",)):
            bad_run = self.run_local(*bad_args)
            self.assertEqual(bad_run.returncode, 2, bad_args)
            self.assertIn("usage: k3s-snapshot-schedule-repair.sh --site oregon|seoul [--dry-run]", bad_run.stderr)
        self.assertFalse((self.work_dir / "aws-calls").exists())


if __name__ == "__main__":
    unittest.main()
