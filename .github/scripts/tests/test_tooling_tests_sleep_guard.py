import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

RUNNER_PATH = Path(__file__).resolve().parents[1] / "tooling-tests.sh"
FIXTURE_ROOT = Path(__file__).resolve().parent / "fixtures" / "sleep-guard"
GUARD_TEXT = "a tooling test ran the real sleep: 1"


def run_runner(fixture_name):
    with tempfile.TemporaryDirectory() as work_dir:
        tree_root = Path(work_dir) / fixture_name
        shutil.copytree(FIXTURE_ROOT / fixture_name, tree_root)
        (tree_root / ".github" / "scripts").mkdir(parents=True)
        shutil.copy(RUNNER_PATH, tree_root / ".github" / "scripts" / "tooling-tests.sh")
        return subprocess.run(["bash", str(tree_root / ".github" / "scripts" / "tooling-tests.sh")],
                              capture_output=True, text=True, stdin=subprocess.DEVNULL)


class SleepGuardTest(unittest.TestCase):
    def test_a_suite_that_reaches_the_real_sleep_fails_with_the_guard_exit(self):
        guarded_run = run_runner("real")
        self.assertEqual(guarded_run.returncode, 1, guarded_run.stdout)
        self.assertIn(f"FAIL .githooks/tests/real_sleep.sh  (exit 97) {GUARD_TEXT}", guarded_run.stdout)

    def test_a_suite_that_ignores_the_guard_exit_still_fails(self):
        guarded_run = run_runner("swallowed")
        self.assertEqual(guarded_run.returncode, 1, guarded_run.stdout)
        self.assertIn(f"FAIL .githooks/tests/swallowed_sleep.sh  (exit 97) {GUARD_TEXT}", guarded_run.stdout)

    def test_a_suite_that_rebuilds_path_from_the_exported_guard_dir_still_fails(self):
        guarded_run = run_runner("rebuilt")
        self.assertEqual(guarded_run.returncode, 1, guarded_run.stdout)
        self.assertIn(f"FAIL .githooks/tests/rebuilt_path.sh  (exit 97) {GUARD_TEXT}", guarded_run.stdout)

    def test_a_suite_whose_fake_sleep_comes_first_on_path_passes(self):
        guarded_run = run_runner("faked")
        self.assertEqual(guarded_run.returncode, 0, guarded_run.stdout)
        self.assertIn("PASS .githooks/tests/fake_sleep.sh", guarded_run.stdout)


if __name__ == "__main__":
    unittest.main()
