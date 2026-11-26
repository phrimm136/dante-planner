import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

GUARD = Path(__file__).resolve().parents[1] / "lib" / "terraform-guard.sh"
BACKEND_FIXTURE = Path(__file__).resolve().parent / "fixtures" / "terraform-backend-s3.json"
ACCOUNT_A = "111111111111"
ACCOUNT_B = "222222222222"
FAKE_AWS = "#!/bin/sh\nprintf '%s\\n' \"$FAKE_CALLER_ACCOUNT\"\n"


def write_backend(directory, account):
    directory.mkdir(parents=True, exist_ok=True)
    recorded = json.loads(BACKEND_FIXTURE.read_text())
    recorded["backend"]["config"]["bucket"] = f"danteplanner-tfstate-{account}"
    (directory / "terraform.tfstate").write_text(json.dumps(recorded))


class TerraformGuardDataDirTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.stack = self.root / "stack"
        write_backend(self.stack / ".terraform", ACCOUNT_A)
        self.bin_dir = self.root / "bin"
        self.bin_dir.mkdir()
        (self.bin_dir / "aws").write_text(FAKE_AWS)
        (self.bin_dir / "aws").chmod(0o755)

    def guard(self, caller, data_dir=None):
        env = {key: value for key, value in os.environ.items() if key != "TF_DATA_DIR"}
        env.update({"PATH": f"{self.bin_dir}:{os.environ['PATH']}", "FAKE_CALLER_ACCOUNT": caller})
        if data_dir is not None:
            env["TF_DATA_DIR"] = str(data_dir)
        return subprocess.run(
            ["bash", "-c", f'source "{GUARD}"; terraform_guard "$@"', "guard",
             f"-chdir={self.stack}", "state", "pull"],
            env=env, capture_output=True, text=True, check=False)

    def test_unset_data_dir_judges_the_chdir_backend(self):
        self.assertEqual(self.guard(ACCOUNT_A).returncode, 0)
        refused = self.guard(ACCOUNT_B)
        self.assertEqual(refused.returncode, 2)
        self.assertIn(f"danteplanner-tfstate-{ACCOUNT_A}", refused.stderr)

    def test_absolute_data_dir_is_judged_instead_of_the_chdir_backend(self):
        data_dir = self.root / "elsewhere"
        write_backend(data_dir, ACCOUNT_B)
        self.assertEqual(self.guard(ACCOUNT_B, data_dir).returncode, 0)
        refused = self.guard(ACCOUNT_A, data_dir)
        self.assertEqual(refused.returncode, 2)
        self.assertIn(f"danteplanner-tfstate-{ACCOUNT_B}", refused.stderr)
        self.assertIn(str(data_dir), refused.stderr)

    def test_relative_data_dir_resolves_under_the_chdir(self):
        write_backend(self.stack / "own-data", ACCOUNT_B)
        self.assertEqual(self.guard(ACCOUNT_B, "own-data").returncode, 0)
        self.assertEqual(self.guard(ACCOUNT_A, "own-data").returncode, 2)

    def test_uninitialised_data_dir_is_never_judged_by_the_chdir_backend(self):
        empty = self.root / "empty"
        empty.mkdir()
        self.assertEqual(self.guard(ACCOUNT_B, empty).returncode, 0)

    def test_empty_data_dir_behaves_as_unset(self):
        self.assertEqual(self.guard(ACCOUNT_A, "").returncode, 0)
        self.assertEqual(self.guard(ACCOUNT_B, "").returncode, 2)

    def test_data_dir_path_with_a_single_quote_is_judged(self):
        data_dir = self.root / "it's here"
        write_backend(data_dir, ACCOUNT_B)
        self.assertEqual(self.guard(ACCOUNT_B, data_dir).returncode, 0)
        refused = self.guard(ACCOUNT_A, data_dir)
        self.assertEqual(refused.returncode, 2)
        self.assertIn(f"danteplanner-tfstate-{ACCOUNT_B}", refused.stderr)

    def test_corrupt_backend_file_refuses_and_names_the_file(self):
        for corrupt_text in ("{not json", "[]", '{"backend": "s3"}'):
            with self.subTest(corrupt_text=corrupt_text):
                (self.stack / ".terraform" / "terraform.tfstate").write_text(corrupt_text)
                refused = self.guard(ACCOUNT_A)
                self.assertEqual(refused.returncode, 2)
                self.assertIn(str(self.stack / ".terraform" / "terraform.tfstate"), refused.stderr)


if __name__ == "__main__":
    unittest.main()
