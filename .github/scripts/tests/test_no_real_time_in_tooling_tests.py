import subprocess
import sys
import unittest
from pathlib import Path

CHECKER_PATH = Path(__file__).resolve().parents[1] / "no-real-time-in-tooling-tests.py"
FIXTURE_ROOT = Path(__file__).resolve().parent / "fixtures" / "no-real-time"
TRIPPING_HITS = [
    ".githooks/tests/tripping_case.sh:2: shell-sleep",
    ".githooks/tests/tripping_case.sh:3: shell-clock",
    ".githooks/tests/tripping_case.sh:4: shell-clock",
    ".githooks/tests/tripping_case.sh:5: path-without-guard",
    "scripts/ops/tests/tripping_case.py:4: clock-call",
    "scripts/ops/tests/tripping_case.py:7: shell-sleep",
    "scripts/ops/tests/tripping_case.py:8: shell-sleep",
    "scripts/ops/tests/tripping_case.py:9: shell-clock",
    "scripts/ops/tests/tripping_case.py:10: shell-clock",
    "scripts/ops/tests/tripping_case.py:16: clock-call",
    "scripts/ops/tests/tripping_case.py:17: clock-call",
    "scripts/ops/tests/tripping_case.py:18: clock-call",
    "scripts/ops/tests/tripping_case.py:19: clock-call",
    "scripts/ops/tests/tripping_case.py:19: duration-assert",
    "scripts/ops/tests/tripping_case.py:20: duration-assert",
    "scripts/ops/tests/tripping_case.py:25: shell-sleep",
    "scripts/ops/tests/tripping_case.py:28: shell-sleep",
    "scripts/ops/tests/tripping_case.py:34: shell-sleep",
    "scripts/ops/tests/tripping_case.py:38: path-without-guard",
    "scripts/ops/tests/tripping_case.py:43: path-without-guard",
    "scripts/ops/tests/tripping_case.py:44: path-without-guard",
    "scripts/ops/tests/tripping_case.py:44: path-without-guard",
]


def run_checker(fixture_name):
    return subprocess.run([sys.executable, str(CHECKER_PATH), "--root", str(FIXTURE_ROOT / fixture_name)],
                          capture_output=True, text=True)


def hit_keys(checker_run):
    return sorted((one_line.split(": ")[0] + ": " + one_line.split(": ")[1] for one_line in checker_run.stdout.splitlines()
                   if not one_line.startswith("no-real-time-in-tooling-tests:")),
                  key=lambda hit_key: (hit_key.split(":")[0], int(hit_key.split(":")[1]), hit_key))


class CheckerTest(unittest.TestCase):
    def test_every_pattern_in_the_tripping_fixture_is_reported_at_its_line(self):
        tripping_run = run_checker("tripping")
        self.assertEqual(tripping_run.returncode, 1, tripping_run.stdout)
        self.assertEqual(hit_keys(tripping_run), TRIPPING_HITS)

    def test_time_scripted_as_an_input_passes(self):
        passing_run = run_checker("passing")
        self.assertEqual((passing_run.returncode, passing_run.stdout.strip()),
                         (0, "no-real-time-in-tooling-tests: no real time in the tooling tests"))


if __name__ == "__main__":
    unittest.main()
