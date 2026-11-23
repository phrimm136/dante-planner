import json
import os
import re
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

from shells import FIRST_SHELL, with_second_shell

SCRIPT = Path(__file__).resolve().parents[1] / "provision" / "k3s-snapshot-schedule-repair.sh"
FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures"
MALFORMED_LINE = "\t'0 \\*/6 \\* \\* \\*' \\"
REPAIRED_LINE = "\t'0 */6 * * *' \\"
NODE_ROWS = ("node-a   Ready    control-plane,etcd   1d   v1.36.3+k3s1",
             "node-b   Ready    <none>               1d   v1.36.3+k3s1")
S3_SELECTOR = "-l etcd.k3s.cattle.io/snapshot-storage-node=s3"
BOUND = "13"
BUCKET = "p-oregon-etcd-snapshots-bucket"
SAVE_CALL = f"k3s etcd-snapshot save --etcd-s3 --etcd-s3-bucket {BUCKET} --etcd-s3-region us-west-2"

FAKE_MODE = """if ls "$FAKE_DIR"/k3s.service.bak-* >/dev/null 2>&1; then
  [ -n "${FAKE_HANG_POST:-}" ] && exec sleep 30 >/dev/null 2>&1
  [ -n "${FAKE_SLOW_POST:-}" ] && sleep 0.9
fi
n=$(cat "$FAKE_DIR/restarts" 2>/dev/null || printf '%s\\n' 0)
eval "mode=\\${FAKE_RESTART$n:-ok}"
"""

FAKE_K3S = """#!/bin/sh
printf '%s\n' "k3s $*" >> "$FAKE_DIR/calls"
""" + FAKE_MODE + """case "$*" in
  "etcd-snapshot save"*)
    [ -n "${FAKE_SAVE_HANGS:-}" ] && exec sleep 30
    if [ -n "${FAKE_SAVE_FAILS:-}" ]; then
      printf '%s\n' "level=info msg=Saving etcd snapshot to s3://p-oregon-etcd-snapshots-bucket/k3s"
      printf '%s\n' "level=fatal msg=AccessDenied: arn:aws:iam::123456789012:role/cp is not allowed on $*" >&2
      exit 1
    fi
    printf '%s\n' "saving to s3://$*" >&2
    printf '%s\n' local >> "$FAKE_DIR/snapshots"
    if [ "$*" = "etcd-snapshot save --etcd-s3 --etcd-s3-bucket p-oregon-etcd-snapshots-bucket --etcd-s3-region us-west-2" ] \
      && [ -z "${FAKE_S3_FAILS:-}" ]; then
      printf '%s\n' s3 >> "$FAKE_DIR/snapshots"
    fi ;;
  "kubectl get etcdsnapshotfiles.k3s.cattle.io -o name --request-timeout=1s")
    [ -n "${FAKE_SLOW_LIST:-}" ] && sleep "$FAKE_SLOW_LIST"
    [ -f "$FAKE_DIR/snapshots" ] && sed 's/^/etcdsnapshotfile.k3s.cattle.io\\/snap-/' "$FAKE_DIR/snapshots" ;;
  "kubectl get etcdsnapshotfiles.k3s.cattle.io """ + S3_SELECTOR + """ -o name --request-timeout=1s")
    [ -n "${FAKE_SLOW_LIST:-}" ] && sleep "$FAKE_SLOW_LIST"
    [ -f "$FAKE_DIR/snapshots" ] && sed -n 's/^s3$/etcdsnapshotfile.k3s.cattle.io\\/snap-s3/p' "$FAKE_DIR/snapshots" ;;
  "kubectl get --raw /readyz --request-timeout=1s")
    if [ -n "${FAKE_TERM_ONCE:-}" ] && [ "$n" -ge 1 ] && [ ! -f "$FAKE_DIR/termed" ]; then
      touch "$FAKE_DIR/termed"
      kill -s TERM -- "-$(ps -o sid= -p $$ | tr -d ' ')"
    fi
    case "$mode" in never-active|failed|loop|never-ready) exit 1 ;; esac
    printf '%s\n' ok ;;
  "kubectl get nodes --no-headers --request-timeout=1s")
    cat "$FAKE_DIR/nodes" ;;
  "kubectl -n kube-node-lease get leases -o "*)
    while read -r node_name rest; do
      if [ "$mode" = stale ] && [ "$node_name" = "${FAKE_STALE_NODE:-}" ]; then
        printf '%s %s\\n' "$node_name" "$(date -u -d '-1 hour' +%Y-%m-%dT%H:%M:%S.000000Z)"
      else
        printf '%s %s\\n' "$node_name" "$(date -u -d '+5 seconds' +%Y-%m-%dT%H:%M:%S.000000Z)"
      fi
    done < "$FAKE_DIR/nodes" ;;
esac
"""

FAKE_SYSTEMCTL = """#!/bin/sh
printf '%s\n' "systemctl $*" >> "$FAKE_DIR/calls"
""" + FAKE_MODE + """case "$*" in
  "restart --no-block k3s")
    printf '%s\n' $((n + 1)) > "$FAKE_DIR/restarts"
    [ -z "${FAKE_RESTART_REFUSED:-}" ] ;;
  "show -p KillMode k3s")
    printf '%s\n' "KillMode=${FAKE_KILL_MODE:-process}" ;;
  "show -p NRestarts --value k3s")
    reads=$(cat "$FAKE_DIR/nrestart-reads-$n" 2>/dev/null || printf '%s\n' 0)
    printf '%s\n' $((reads + 1)) > "$FAKE_DIR/nrestart-reads-$n"
    if [ "$n" = 0 ] || [ "$reads" = 0 ]; then printf '%s\n' "${FAKE_PRE_NRESTARTS:-0}"
    elif [ "$mode" = loop ]; then printf '%s\n' "$reads"
    else printf '%s\n' 0; fi ;;
  "is-failed --quiet k3s")
    [ "$mode" = failed ] ;;
  "is-active --quiet k3s"|"is-active k3s")
    state=active
    case "$mode" in never-active|loop) state=activating ;; failed) state=failed ;; esac
    [ -n "${FAKE_K3S_DOWN:-}" ] && state=inactive
    [ "$2" = --quiet ] || printf '%s\n' "$state"
    [ "$state" = active ] ;;
esac
"""

FAKE_AWS = """#!/bin/sh
printf '%s\n' "aws $*" >> "$FAKE_DIR/aws-calls"
case "$1 $2" in
  "sts get-caller-identity") printf '%s\n' "acct" ;;
  "ssm send-command")
    while [ $# -gt 0 ]; do [ "$1" = --parameters ] && printf '%s' "$2" > "$FAKE_DIR/parameters"; shift; done
    printf '%s\n' cmd-1 ;;
  "ssm get-command-invocation")
    case "$*" in
      *"--query Status"*) printf '%s\n' "${FAKE_STATUS:-Success}" ;;
      *) jq -n --arg o "${FAKE_REMOTE_OUT:-REPAIRED}" '{StandardOutputContent: $o, StandardErrorContent: ""}' ;;
    esac ;;
esac
"""


def write_tool(bin_dir, tool_name, tool_text, shell_path):
    tool_path = bin_dir / tool_name
    tool_path.write_text(tool_text.replace("#!/bin/sh\n", f"#!{shell_path}\n", 1))
    tool_path.chmod(0o755)


class RepairCase(unittest.TestCase):
    shell_path = FIRST_SHELL

    def setUp(self):
        self.work_handle = tempfile.TemporaryDirectory()
        self.work_dir = Path(self.work_handle.name)
        bin_dir = self.work_dir / "bin"
        bin_dir.mkdir()
        write_tool(bin_dir, "k3s", FAKE_K3S, self.shell_path)
        write_tool(bin_dir, "systemctl", FAKE_SYSTEMCTL, self.shell_path)
        write_tool(bin_dir, "aws", FAKE_AWS, self.shell_path)
        self.unit_path = self.work_dir / "k3s.service"
        self.unit_path.write_text((FIXTURE_DIR / "k3s.service").read_text())
        self.original_text = self.unit_path.read_text()
        self.lock_dir = self.work_dir / "repair.lock"
        self.set_nodes(*NODE_ROWS)
        self.base_env = {**os.environ, "PATH": f"{bin_dir}:{os.environ['PATH']}", "FAKE_DIR": str(self.work_dir)}

    def tearDown(self):
        self.work_handle.cleanup()

    def set_nodes(self, *node_rows):
        (self.work_dir / "nodes").write_text("\n".join(node_rows) + "\n")

    def remote_text(self, mode_name, restart_bound=BOUND, ssm_timeout="600", function_override=""):
        build_run = subprocess.run(["bash", "-c", f'source "{SCRIPT}"; eval "$1"; shift; remote_script "$@"',
                                    "repair-test", function_override, mode_name, str(self.unit_path),
                                    "0 */6 * * *", "2", restart_bound, BOUND, ssm_timeout, "1", "1", "1",
                                    str(self.lock_dir)],
                                   capture_output=True, text=True, check=True)
        return build_run.stdout

    def run_remote(self, mode_name, script_text=None, restart_bound=BOUND, ssm_timeout="600", **fake_flags):
        return subprocess.run([self.shell_path, "-c", script_text or self.remote_text(mode_name, restart_bound, ssm_timeout)],
                              capture_output=True, text=True, start_new_session=True,
                              env={**self.base_env, **{flag_name: "1" if flag_value is True else flag_value
                                                       for flag_name, flag_value in fake_flags.items()}})

    def final_line(self, remote_run):
        return [one_line for one_line in remote_run.stdout.splitlines() if one_line.strip()][-1]

    def calls(self):
        calls_path = self.work_dir / "calls"
        return calls_path.read_text().splitlines() if calls_path.is_file() else []

    def restarts(self):
        restarts_path = self.work_dir / "restarts"
        return int(restarts_path.read_text()) if restarts_path.is_file() else 0

    def backups(self):
        return sorted(self.work_dir.glob("k3s.service.bak-*"))

    def assert_untouched(self, remote_run, reason_text):
        self.assertEqual(remote_run.returncode, 3, remote_run.stdout)
        self.assertTrue(self.final_line(remote_run).startswith("NOTHING CHANGED: "), remote_run.stdout)
        self.assertIn(reason_text, self.final_line(remote_run))
        self.assertEqual((self.unit_path.read_text(), self.backups(), self.restarts()), (self.original_text, [], 0))

    def assert_rolled_back(self, remote_run, ending_text, exit_code):
        self.assertEqual(remote_run.returncode, exit_code, remote_run.stdout)
        self.assertTrue(self.final_line(remote_run).startswith(ending_text), remote_run.stdout)
        self.assertEqual(self.unit_path.read_text(), self.original_text)
        self.assertEqual(self.restarts(), 2)
        self.assertFalse(self.lock_dir.exists())


class RemoteScriptTest(RepairCase):
    def test_the_repair_ends_repaired_with_the_counts_and_no_node_names(self):
        repair_run = self.run_remote("repair")
        self.assertEqual((repair_run.returncode, self.final_line(repair_run)), (0, "REPAIRED"), repair_run.stdout)
        self.assertEqual(self.original_text.replace(MALFORMED_LINE, REPAIRED_LINE), self.unit_path.read_text())
        self.assertEqual([one_backup.read_text() for one_backup in self.backups()], [self.original_text])
        self.assertIn("nodes Ready before the run: 2 of 2", repair_run.stdout)
        self.assertIn("snapshots: local 1, s3 1", repair_run.stdout)
        self.assertIn("unit cron line: '0 */6 * * *' \\", repair_run.stdout)
        self.assertNotIn("node-", repair_run.stdout)
        self.assertNotIn("snap-", repair_run.stdout)
        self.assertNotIn(BUCKET, repair_run.stdout + repair_run.stderr)
        self.assertIn(SAVE_CALL, self.calls())
        self.assertIn("systemctl restart --no-block k3s", self.calls())
        self.assertNotIn("systemctl restart k3s", self.calls())
        self.assertFalse(self.lock_dir.exists())

    def test_a_second_run_ends_already_repaired(self):
        self.run_remote("repair")
        rerun = self.run_remote("repair")
        self.assertEqual((rerun.returncode, self.final_line(rerun)), (0, "ALREADY REPAIRED"))
        self.assertEqual((len(self.backups()), self.restarts()), (1, 1))

    def test_already_repaired_with_k3s_down_is_not_a_success(self):
        self.run_remote("repair")
        down_run = self.run_remote("repair", FAKE_K3S_DOWN=True)
        self.assertEqual(down_run.returncode, 3)
        self.assertEqual(self.final_line(down_run),
                         "NOTHING CHANGED: the unit holds the repaired line, but k3s is inactive")

    def test_already_repaired_with_the_api_not_ready_is_not_a_success(self):
        self.run_remote("repair")
        not_ready_run = self.run_remote("repair", FAKE_RESTART1="never-ready")
        self.assertEqual(self.final_line(not_ready_run), "NOTHING CHANGED: the unit holds the repaired line and "
                                                         "k3s is active, but the API /readyz is not ok")

    def test_a_hand_edited_line_is_refused_and_says_whether_k3s_serves(self):
        self.unit_path.write_text(self.original_text.replace(MALFORMED_LINE, "\t'0 3 * * *' \\"))
        self.original_text = self.unit_path.read_text()
        refused_run = self.run_remote("repair")
        self.assert_untouched(refused_run, "is not the known malformed line")
        self.assertTrue(self.final_line(refused_run).endswith("; k3s is serving"))
        self.assertTrue(self.final_line(self.run_remote("repair", FAKE_K3S_DOWN=True)).endswith("; k3s is NOT serving"))

    def test_a_missing_or_doubled_flag_is_refused(self):
        fixture_text = self.original_text
        self.unit_path.write_text(fixture_text.replace("\t'--etcd-snapshot-schedule-cron' \\\n", ""))
        self.original_text = self.unit_path.read_text()
        self.assert_untouched(self.run_remote("repair"), "argument 0 times, not once")
        self.unit_path.write_text(fixture_text + "\t'--etcd-snapshot-schedule-cron' \\\n")
        self.original_text = self.unit_path.read_text()
        self.assert_untouched(self.run_remote("repair"), "argument 2 times, not once")

    def test_a_second_run_while_the_lock_is_held_refuses(self):
        self.lock_dir.mkdir()
        locked_run = self.run_remote("repair")
        self.assert_untouched(locked_run, "another repair run holds")
        self.assertTrue(self.lock_dir.is_dir())
        self.assertFalse([one_call for one_call in self.calls() if one_call.startswith("k3s etcd-snapshot save")])

    def test_k3s_down_stops_before_step_one(self):
        self.assert_untouched(self.run_remote("repair", FAKE_K3S_DOWN=True), "k3s is not active")

    def test_a_unit_without_the_s3_arguments_changes_nothing(self):
        for flag_line in ("\t'--etcd-s3-bucket' \\\n", "\t'--etcd-s3-region' \\\n"):
            fixture_text = (FIXTURE_DIR / "k3s.service").read_text()
            self.unit_path.write_text(fixture_text.replace(flag_line, ""))
            self.original_text = self.unit_path.read_text()
            self.assertNotEqual(self.original_text, fixture_text)
            self.assert_untouched(self.run_remote("repair"), "the unit names no --etcd-s3-bucket or no --etcd-s3-region")
        self.assertFalse([one_call for one_call in self.calls() if one_call.startswith("k3s etcd-snapshot save")])

    def test_a_save_without_the_s3_arguments_makes_no_s3_copy(self):
        flagged_save = ('k3s etcd-snapshot save --etcd-s3 --etcd-s3-bucket "$s3_bucket" \\\n'
                        '  --etcd-s3-region "$s3_region"')
        script_text = self.remote_text("repair")
        self.assertEqual(script_text.count(flagged_save), 1)
        script_text = script_text.replace(flagged_save, "k3s etcd-snapshot save")
        self.assert_untouched(self.run_remote("repair", script_text), "the S3 copy is missing")

    def test_a_failed_save_changes_nothing_and_shows_its_scrubbed_output(self):
        failed_run = self.run_remote("repair", FAKE_SAVE_FAILS=True)
        self.assert_untouched(failed_run, "NOTHING CHANGED: k3s etcd-snapshot save failed (exit 1); its last lines are above")
        self.assertIn("save output: level=info msg=Saving etcd snapshot to s3://<bucket>/k3s", failed_run.stdout)
        self.assertIn("save output: level=fatal msg=AccessDenied: arn:aws:iam::<account>:role/cp is not allowed on "
                      "etcd-snapshot save --etcd-s3 --etcd-s3-bucket <bucket> --etcd-s3-region us-west-2",
                      failed_run.stdout)
        self.assertNotIn(BUCKET, failed_run.stdout + failed_run.stderr)
        self.assertNotIn("123456789012", failed_run.stdout + failed_run.stderr)

    def test_a_save_over_its_bound_is_named_a_timeout(self):
        hang_run = self.run_remote("repair", FAKE_SAVE_HANGS=True)
        self.assert_untouched(hang_run, "NOTHING CHANGED: k3s etcd-snapshot save ran over 2s (timeout exit 124)")
        self.assertNotIn(BUCKET, hang_run.stdout + hang_run.stderr)

    def test_a_save_without_an_s3_copy_changes_nothing(self):
        self.assert_untouched(self.run_remote("repair", FAKE_S3_FAILS=True),
                              "the local snapshot was written but the S3 copy is missing; read the k3s journal")

    def test_an_older_s3_snapshot_does_not_count_for_this_save(self):
        (self.work_dir / "snapshots").write_text("local\ns3\n")
        self.assert_untouched(self.run_remote("repair", FAKE_S3_FAILS=True), "the S3 copy is missing")

    def test_a_kill_mode_other_than_process_changes_nothing(self):
        self.assert_untouched(self.run_remote("repair", FAKE_KILL_MODE="control-group"),
                              "k3s runs with KillMode=control-group, not KillMode=process")
        self.assertFalse([one_call for one_call in self.calls() if one_call.startswith("k3s etcd-snapshot save")])

    def test_k3s_never_active_after_the_restart_rolls_back(self):
        self.assert_rolled_back(self.run_remote("repair", FAKE_RESTART1="never-active"),
                                "ROLLED BACK, k3s serving: k3s not active within 13s", 4)

    def test_a_refused_restart_rolls_back(self):
        refused_run = self.run_remote("repair", FAKE_RESTART_REFUSED=True)
        self.assertEqual(refused_run.returncode, 5, refused_run.stdout)
        self.assertEqual(self.unit_path.read_text(), self.original_text)

    def test_a_failed_unit_rolls_back_before_the_bound(self):
        started = time.monotonic()
        failed_run = self.run_remote("repair", restart_bound="40", FAKE_RESTART1="failed")
        self.assertLess(time.monotonic() - started, 15)
        self.assert_rolled_back(failed_run, "ROLLED BACK, k3s serving: systemd reports k3s failed", 4)

    def test_a_restart_loop_rolls_back_before_the_bound(self):
        started = time.monotonic()
        loop_run = self.run_remote("repair", restart_bound="40", FAKE_RESTART1="loop")
        self.assertLess(time.monotonic() - started, 15)
        self.assert_rolled_back(loop_run, "ROLLED BACK, k3s serving: k3s exited and systemd restarted it", 4)

    def test_an_api_never_ready_rolls_back(self):
        self.assert_rolled_back(self.run_remote("repair", FAKE_RESTART1="never-ready"),
                                "ROLLED BACK, k3s serving: API /readyz not ok within 13s", 4)

    def test_a_restart_loop_is_seen_when_the_manual_restart_resets_the_counter(self):
        started = time.monotonic()
        loop_run = self.run_remote("repair", restart_bound="40", FAKE_RESTART1="loop", FAKE_PRE_NRESTARTS="7")
        self.assertLess(time.monotonic() - started, 15)
        self.assert_rolled_back(loop_run, "ROLLED BACK, k3s serving: k3s exited and systemd restarted it", 4)

    def test_a_counter_reset_by_the_manual_restart_is_not_a_loop(self):
        reset_run = self.run_remote("repair", FAKE_PRE_NRESTARTS="7")
        self.assertEqual(self.final_line(reset_run), "REPAIRED", reset_run.stdout)

    def test_a_fast_run_with_the_same_budget_edits_the_unit(self):
        self.assertEqual(self.final_line(self.run_remote("repair", ssm_timeout="74")), "REPAIRED")

    def test_a_slow_phase_before_the_edit_leaves_no_time_and_changes_nothing(self):
        slow_run = self.run_remote("repair", ssm_timeout="74", FAKE_SLOW_LIST="0.9")
        self.assert_untouched(slow_run, "not enough time left to restart and roll back within this run (")
        self.assertTrue(self.final_line(slow_run).endswith("s left); run again"))
        self.assertIn("s3", (self.work_dir / "snapshots").read_text())

    def test_a_rollback_with_a_late_lease_but_a_serving_api_ends_serving(self):
        late_run = self.run_remote("repair", FAKE_RESTART1="never-ready", FAKE_RESTART2="stale",
                                   FAKE_STALE_NODE="node-b")
        self.assert_rolled_back(late_run, "ROLLED BACK, k3s serving: API /readyz not ok within 13s; after the rollback "
                                          "1 of 2 nodes that were Ready before the run have not renewed their lease yet", 4)

    def test_calls_that_all_run_close_to_their_limit_after_the_edit_still_end_within_the_reserve(self):
        started = time.monotonic()
        slow_run = self.run_remote("repair", FAKE_SLOW_POST=True, FAKE_RESTART1="never-ready",
                                   FAKE_RESTART2="never-ready")
        elapsed = time.monotonic() - started
        self.assertEqual(self.restarts(), 2)
        self.assertTrue(self.final_line(slow_run).startswith("ROLLED BACK, k3s NOT serving: API /readyz not ok"),
                        slow_run.stdout)
        self.assertGreater(elapsed, 10)
        self.assertLess(elapsed, header_values(self.remote_text("repair"))["RESERVE"])
        self.assertEqual(self.unit_path.read_text(), self.original_text)

    def test_calls_that_all_hang_after_the_edit_are_cut_and_end_rolled_back(self):
        started = time.monotonic()
        hang_run = self.run_remote("repair", FAKE_HANG_POST=True)
        self.assertLess(time.monotonic() - started, header_values(self.remote_text("repair"))["RESERVE"])
        self.assertTrue(self.final_line(hang_run).startswith("ROLLED BACK, k3s NOT serving: "), hang_run.stdout)
        self.assertEqual(self.unit_path.read_text(), self.original_text)

    def test_a_wait_starts_no_round_that_could_end_after_its_bound(self):
        started = time.monotonic()
        short_run = self.run_remote("repair", restart_bound="12", FAKE_RESTART1="never-ready")
        self.assertLess(time.monotonic() - started, 8)
        self.assert_rolled_back(short_run, "ROLLED BACK, k3s serving: not ready within 12s", 4)
        after_restart = self.calls()[self.calls().index("systemctl restart --no-block k3s"):]
        second_restart = after_restart.index("systemctl restart --no-block k3s", 1)
        self.assertNotIn("k3s kubectl get --raw /readyz --request-timeout=1s", after_restart[:second_restart])

    def test_a_rollback_that_never_gets_ready_ends_not_serving_and_stops(self):
        not_serving_run = self.run_remote("repair", FAKE_RESTART1="never-ready", FAKE_RESTART2="never-ready")
        self.assert_rolled_back(not_serving_run, "ROLLED BACK, k3s NOT serving: API /readyz not ok within 13s; "
                                                 "after the rollback API /readyz not ok within 13s; run journalctl -u k3s", 5)

    def test_a_stale_lease_does_not_pass_as_ready(self):
        stale_run = self.run_remote("repair", FAKE_RESTART1="stale", FAKE_STALE_NODE="node-b")
        self.assert_rolled_back(stale_run, "ROLLED BACK, k3s serving: a node that was Ready before the run did not "
                                           "report Ready with a new lease within 13s", 4)

    def test_a_cordoned_node_counts_as_ready(self):
        self.set_nodes(NODE_ROWS[0], "node-b   Ready,SchedulingDisabled    <none>   1d   v1.36.3+k3s1")
        cordoned_run = self.run_remote("repair")
        self.assertEqual(self.final_line(cordoned_run), "REPAIRED", cordoned_run.stdout)
        self.assertIn("nodes Ready before the run: 2 of 2", cordoned_run.stdout)

    def test_a_node_not_ready_before_the_run_is_reported_and_does_not_block(self):
        self.set_nodes(*NODE_ROWS, "node-c   NotReady    <none>   1d   v1.36.3+k3s1")
        not_ready_run = self.run_remote("repair")
        self.assertEqual(self.final_line(not_ready_run), "REPAIRED", not_ready_run.stdout)
        self.assertIn("nodes Ready before the run: 2 of 3", not_ready_run.stdout)
        self.assertIn("nodes not Ready before the run: 1; the run does not check them afterwards", not_ready_run.stdout)

    def test_a_terminated_run_rolls_back_through_the_exit_trap(self):
        term_run = self.run_remote("repair", FAKE_TERM_ONCE=True)
        self.assert_rolled_back(term_run, "ROLLED BACK, k3s serving: the run stopped before it finished", 4)

    def test_the_dry_run_only_reads_and_reports_lock_nodes_and_kill_mode(self):
        dry_run = self.run_remote("dry-run")
        self.assertEqual((dry_run.returncode, self.final_line(dry_run)), (0, "NOTHING CHANGED: dry run"))
        for expected_text in (f"lock {self.lock_dir}: free", "as found: '0 \\*/6 \\* \\* \\*' \\",
                              "unit KillMode line: KillMode=process\neffective KillMode=process",
                              "unit S3 arguments: bucket found, region found",
                              "nodes Ready before the run: 2 of 2",
                              "node leases of the Ready nodes: renewed within 60s", "k3s: active",
                              "snapshots: local 0, s3 0"):
            self.assertIn(expected_text, dry_run.stdout)
        self.assertEqual((self.unit_path.read_text(), self.backups(), self.lock_dir.exists()),
                         (self.original_text, [], False))
        self.assertEqual(self.calls(), [
            "systemctl show -p KillMode k3s", "k3s kubectl get nodes --no-headers --request-timeout=1s",
            "k3s kubectl get nodes --no-headers --request-timeout=1s",
            "k3s kubectl -n kube-node-lease get leases -o "
            "jsonpath={range .items[*]}{.metadata.name}{\" \"}{.spec.renewTime}{\"\\n\"}{end} --request-timeout=1s",
            "systemctl is-active k3s", "k3s kubectl get etcdsnapshotfiles.k3s.cattle.io -o name --request-timeout=1s",
            f"k3s kubectl get etcdsnapshotfiles.k3s.cattle.io {S3_SELECTOR} -o name --request-timeout=1s"])

    def test_no_ready_node_before_the_run_changes_nothing(self):
        self.set_nodes("node-a   NotReady    control-plane,etcd   1d   v1.36.3+k3s1")
        self.assert_untouched(self.run_remote("repair"), "no node is Ready before the run")

    def test_the_dry_run_reports_a_missing_s3_argument_without_values(self):
        self.unit_path.write_text(self.original_text.replace("\t'--etcd-s3-region' \\\n", ""))
        dry_run = self.run_remote("dry-run")
        self.assertIn("unit S3 arguments: bucket found, region missing", dry_run.stdout)
        self.assertNotIn(BUCKET, dry_run.stdout)

    def test_the_dry_run_reports_a_held_lock(self):
        self.lock_dir.mkdir()
        self.assertIn(f"lock {self.lock_dir}: held since ", self.run_remote("dry-run").stdout)
        self.assertTrue(self.lock_dir.is_dir())

    def test_the_remote_script_is_posix_sh(self):
        for mode_name in ("dry-run", "repair"):
            lint_run = subprocess.run(["shellcheck", "-s", "sh", "-"], input=self.remote_text(mode_name),
                                      capture_output=True, text=True)
            self.assertEqual(lint_run.returncode, 0, lint_run.stdout)


def header_values(script_text):
    return {name: int(value) for name, value in re.findall(r"^([A-Z_]+)=(\d+)$", script_text, re.MULTILINE)}


def function_bodies(script_text):
    bodies = {name: body for name, body in re.findall(r"^(\w+)\(\) \{ (.*) \}$", script_text, re.MULTILINE)}
    bodies.update(re.findall(r"^(\w+)\(\) \{\n(.*?)\n\}$", script_text, re.MULTILINE | re.DOTALL))
    return bodies


def bounded_calls(text, bodies, skipped):
    call_total = len(re.findall(r"(?<![\w-])bounded\s", text))
    for function_name, body in bodies.items():
        if function_name == "bounded" or function_name in skipped:
            continue
        uses = len(re.findall(rf"(?<![\w{{-]){function_name}(?![\w}}-])", text))
        if uses:
            call_total += uses * bounded_calls(body, bodies, skipped)
    return call_total


def reserve_problem(script_text):
    values = header_values(script_text)
    bodies = function_bodies(script_text)
    call_worst = values["CALL_BOUND"] + values["KILL_GRACE"]
    skipped = {"roll_back", "wait_ready", "end"}
    steps_text = script_text[script_text.index("time_left="):script_text.index("printf '%s\\n' \"STEP 5")]
    path_calls = (bounded_calls(steps_text, bodies, skipped) + bounded_calls(bodies["roll_back"], bodies, skipped)
                  + bounded_calls(bodies["end"], bodies, set()))
    loop_text = bodies["wait_ready"][bodies["wait_ready"].index("while"):]
    round_calls = bounded_calls(loop_text, bodies, set())
    problems = []
    if values["RESERVE"] != values["RESTART_BOUND"] + values["ROLLBACK_BOUND"] + path_calls * call_worst:
        problems.append(f"RESERVE {values['RESERVE']} is not the bounds plus {path_calls} calls x {call_worst}s")
    if values["ROUND_WORST"] != round_calls * call_worst + values["POLL"]:
        problems.append(f"ROUND_WORST {values['ROUND_WORST']} is not {round_calls} calls x {call_worst}s + POLL")
    return problems, path_calls, round_calls


class ReserveTest(RepairCase):
    def test_the_reserve_counts_every_bounded_call_after_the_edit(self):
        problems, path_calls, round_calls = reserve_problem(self.remote_text("repair"))
        self.assertEqual((problems, path_calls, round_calls), ([], 18, 6))

    def test_a_reserve_without_one_term_is_caught(self):
        script_text = SCRIPT.read_text()
        reserve_function = script_text[script_text.index("reserve_seconds() {"):script_text.index("\n}\n", script_text.index("reserve_seconds() {")) + 3]
        mutant_function = reserve_function.replace(" + UNLOCK_CALLS)", ")")
        self.assertNotEqual(mutant_function, reserve_function)
        problems, _, _ = reserve_problem(self.remote_text("repair", function_override=mutant_function))
        self.assertEqual(len(problems), 1, problems)


class MutantTest(RepairCase):
    def mutant(self, original_text, mutated_text):
        script_text = self.remote_text("repair")
        self.assertEqual(script_text.count(original_text), 1)
        return script_text.replace(original_text, mutated_text)

    def test_without_the_exit_trap_a_terminated_run_leaves_the_edited_unit(self):
        term_run = self.run_remote("repair", self.mutant("trap on_exit EXIT\n", ""), FAKE_TERM_ONCE=True)
        self.assertNotEqual(self.unit_path.read_text(), self.original_text, term_run.stdout)

    def test_without_the_rollback_in_the_trap_a_terminated_run_leaves_the_edited_unit(self):
        term_run = self.run_remote("repair", self.mutant('    roll_back "the run stopped before it finished"\n',
                                                         "    :\n"), FAKE_TERM_ONCE=True)
        self.assertNotEqual(self.unit_path.read_text(), self.original_text, term_run.stdout)

    def test_without_the_budget_check_a_slow_run_edits_the_unit(self):
        slow_run = self.run_remote("repair", self.mutant(
            '[ "$time_left" -ge "$RESERVE" ]', "true"),
            FAKE_SLOW_LIST="0.9", ssm_timeout="74")
        self.assertNotIn("not enough time left", slow_run.stdout)
        self.assertTrue(self.backups())

    def test_comparing_against_the_counter_before_the_restart_misses_a_loop(self):
        loop_run = self.run_remote("repair", self.mutant('[ "$restarts_now" -lt "$restarts_low" ] && '
                                                         'restarts_low=$restarts_now\n', ""),
                                   FAKE_RESTART1="loop", FAKE_PRE_NRESTARTS="7")
        self.assertNotIn("k3s exited and systemd restarted it", self.final_line(loop_run))

    def test_without_the_lease_time_check_a_stale_node_passes(self):
        stale_run = self.run_remote("repair", self.mutant('[ "$renew_epoch" -gt "$1" ]', '[ "$renew_epoch" -gt 0 ]'),
                                    FAKE_RESTART1="stale", FAKE_STALE_NODE="node-b")
        self.assertEqual(self.final_line(stale_run), "REPAIRED")


class LocalScriptTest(RepairCase):
    def run_local(self, *script_args, **extra_env):
        return subprocess.run([str(SCRIPT), *script_args], capture_output=True, text=True,
                              env={**self.base_env, "CP_INSTANCE_ID": "i-0test", **extra_env})

    def sent_parameters(self):
        return json.loads((self.work_dir / "parameters").read_text())

    def test_the_site_picks_the_region_and_the_mode_reaches_the_cp(self):
        local_run = self.run_local("--site", "seoul", "--dry-run", FAKE_REMOTE_OUT="NOTHING CHANGED: dry run")
        self.assertEqual(local_run.returncode, 0, local_run.stderr)
        aws_calls = (self.work_dir / "aws-calls").read_text()
        self.assertIn("ssm send-command --region ap-northeast-2 --instance-ids i-0test", aws_calls)
        self.assertIn("MODE=dry-run\nUNIT='/etc/systemd/system/k3s.service'\nCRON='0 */6 * * *'",
                      "\n".join(self.sent_parameters()["commands"]))
        self.run_local("--site", "oregon")
        self.assertIn("ssm send-command --region us-west-2", (self.work_dir / "aws-calls").read_text())
        self.assertIn("MODE=repair", "\n".join(self.sent_parameters()["commands"]))

    def test_the_ssm_timeout_covers_the_reserve_and_the_local_wait_covers_the_ssm_timeout(self):
        self.run_local("--site", "oregon")
        values = header_values("\n".join(self.sent_parameters()["commands"]))
        ssm_timeout = int(self.sent_parameters()["executionTimeout"][0])
        local_wait = int(subprocess.run(["bash", "-c", f'source "{SCRIPT}"; echo "$LOCAL_WAIT_SECONDS"'],
                                        capture_output=True, text=True, check=True).stdout)
        self.assertEqual((values["SAVE_BOUND"], values["RESTART_BOUND"], values["ROLLBACK_BOUND"], values["CALL_BOUND"],
                          values["KILL_GRACE"], values["RESERVE"], values["SSM_TIMEOUT"], ssm_timeout, local_wait),
                         (120, 300, 300, 10, 5, 870, 1300, 1300, 1360))
        self.assertGreater(ssm_timeout, 2 * values["SAVE_BOUND"] + values["RESERVE"])
        self.assertEqual(reserve_problem("\n".join(self.sent_parameters()["commands"]))[0], [])

    def test_the_exit_code_follows_the_final_line(self):
        for remote_out, exit_code in (("x\nREPAIRED", 0), ("ALREADY REPAIRED", 0), ("NOTHING CHANGED: dry run", 0),
                                      ("NOTHING CHANGED: lock", 3), ("ROLLED BACK, k3s serving: y", 4),
                                      ("ROLLED BACK, k3s NOT serving: y; run z", 5), ("half a run", 7)):
            local_run = self.run_local("--site", "oregon", FAKE_REMOTE_OUT=remote_out)
            self.assertEqual(local_run.returncode, exit_code, remote_out)

    def test_a_remote_run_longer_than_the_local_wait_says_not_to_rerun(self):
        wait_run = subprocess.run(["bash", "-c", f'source "{SCRIPT}"; LOCAL_WAIT_SECONDS=1; POLL_SECONDS=1; '
                                                 'run_on_cp us-west-2 i-0test "echo hi"'],
                                  capture_output=True, text=True, env={**self.base_env, "FAKE_STATUS": "InProgress"})
        self.assertEqual(wait_run.returncode, 6)
        self.assertIn("may still be going; do not rerun until command cmd-1 has ended", wait_run.stderr)

    def test_a_bad_site_or_option_prints_the_usage(self):
        for bad_args in ((), ("--site", "tokyo"), ("--site",), ("--force",)):
            bad_run = self.run_local(*bad_args)
            self.assertEqual(bad_run.returncode, 2, bad_args)
            self.assertIn("usage: k3s-snapshot-schedule-repair.sh --site oregon|seoul [--dry-run]", bad_run.stderr)
        self.assertFalse((self.work_dir / "aws-calls").exists())


with_second_shell(RemoteScriptTest, globals())


if __name__ == "__main__":
    unittest.main()
