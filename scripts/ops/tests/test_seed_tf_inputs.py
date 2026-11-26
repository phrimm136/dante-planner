import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "provision" / "seed-tf-inputs.sh"
ACCOUNT = "111122223333"
VPC = "vpc-0a1b2c3d4e5f60718"
OPERATOR_CIDR = "198.51.100.7/32"
SECOND_CIDR = "203.0.113.0/24"

FAKE_AWS = """#!/bin/sh
printf '%s\\n' "$*" >> "$FAKE_DIR/calls"
case "$1 $2" in
  "sts get-caller-identity") printf '%s\\n' "$FAKE_ACCOUNT" ;;
esac
"""


def tfvars(account=ACCOUNT, cidrs=f'["{OPERATOR_CIDR}"]', extra=""):
    return (f'aws_account_id = "{account}"\n'
            f'# a comment naming ingress_allowed_cidrs = ["10.0.0.0/8"]\n'
            f'ingress_allowed_cidrs = {cidrs}\n'
            f'rds_vpc_id = "{VPC}"\n'
            f'gitops_target_revision = "main"\n'
            f'{extra}')


class SeedTfInputsTest(unittest.TestCase):
    def setUp(self):
        self.work = Path(tempfile.mkdtemp(prefix="seed-tf-inputs-"))
        self.addCleanup(shutil.rmtree, self.work)
        subprocess.run(["git", "init", "-q", str(self.work)], check=True)
        provision = self.work / "scripts" / "ops" / "provision"
        provision.mkdir(parents=True)
        shutil.copy(SCRIPT, provision / SCRIPT.name)
        self.script = provision / SCRIPT.name
        self.fake_dir = self.work / "fake"
        bin_dir = self.fake_dir / "bin"
        bin_dir.mkdir(parents=True)
        aws = bin_dir / "aws"
        aws.write_text(FAKE_AWS)
        aws.chmod(0o755)
        self.env = {**os.environ, "PATH": f"{bin_dir}:{os.environ['PATH']}",
                    "FAKE_DIR": str(self.fake_dir), "FAKE_ACCOUNT": ACCOUNT}

    def write(self, stack, text, env="prod"):
        directory = self.work / "terraform" / stack
        directory.mkdir(parents=True, exist_ok=True)
        (directory / f"{env}.tfvars").write_text(text)

    def write_both(self, text, env="prod"):
        self.write("oregon", text, env)
        self.write("seoul", text, env)

    def run_seed(self, *args):
        return subprocess.run(["bash", str(self.script), *args], env=self.env,
                              capture_output=True, text=True, timeout=30)

    def calls(self):
        path = self.fake_dir / "calls"
        return path.read_text().splitlines() if path.exists() else []

    def assert_no_values(self, result):
        for value in (ACCOUNT, VPC, OPERATOR_CIDR, SECOND_CIDR):
            self.assertNotIn(value, result.stdout + result.stderr)

    def test_dry_run_lists_the_parameters_without_values_or_aws_calls(self):
        self.write_both(tfvars())
        result = self.run_seed("--env", "prod")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.splitlines(), [
            "  /danteplanner/prod/oregon/ingress_allowed_cidrs  StringList  1 entries",
            "  /danteplanner/prod/seoul/ingress_allowed_cidrs  StringList  1 entries",
            "dry run, region us-west-2: nothing written; run again with --apply to write these 2 parameters",
        ])
        self.assertEqual(self.calls(), [])
        self.assert_no_values(result)

    def test_apply_writes_each_stack_list_in_its_env(self):
        for env in ("prod", "staging"):
            with self.subTest(env=env):
                (self.fake_dir / "calls").unlink(missing_ok=True)
                self.write_both(tfvars(cidrs=f'[\n  "{OPERATOR_CIDR}", # operator\n  "{SECOND_CIDR}",\n]'), env=env)
                result = self.run_seed("--env", env, "--apply")
                self.assertEqual(result.returncode, 0, result.stderr)
                put = ("ssm put-parameter --region us-west-2 --name /danteplanner/{}/{}/ingress_allowed_cidrs"
                       " --type StringList --value {} --overwrite")
                self.assertEqual(self.calls(), [
                    "sts get-caller-identity --query Account --output text",
                    put.format(env, "oregon", f"{OPERATOR_CIDR},{SECOND_CIDR}"),
                    put.format(env, "seoul", f"{OPERATOR_CIDR},{SECOND_CIDR}"),
                ])
                self.assertIn("2 entries", result.stdout)
                self.assert_no_values(result)

    def test_apply_under_another_account_writes_nothing(self):
        self.write_both(tfvars())
        self.env["FAKE_ACCOUNT"] = "444455556666"
        result = self.run_seed("--env", "prod", "--apply")
        self.assertEqual(result.returncode, 1)
        self.assertIn("set AWS_PROFILE to the prod profile", result.stderr)
        self.assertEqual(self.calls(), ["sts get-caller-identity --query Account --output text"])
        self.assert_no_values(result)

    def test_stacks_naming_different_accounts_are_refused(self):
        self.write("oregon", tfvars())
        self.write("seoul", tfvars(account="444455556666"))
        result = self.run_seed("--env", "prod", "--apply")
        self.assertEqual(result.returncode, 1)
        self.assertIn("names a different account", result.stderr)
        self.assertEqual(self.calls(), [])

    def test_a_non_cidr_entry_is_refused(self):
        self.write_both(tfvars(cidrs='["REPLACE_WITH_CLOUDFLARE_CIDRS"]'))
        result = self.run_seed("--env", "prod", "--apply")
        self.assertEqual(result.returncode, 1)
        self.assertIn("is not an IPv4 CIDR", result.stderr)
        self.assertEqual(self.calls(), [])

    def test_a_key_assigned_twice_is_refused(self):
        self.write_both(tfvars(extra=f'ingress_allowed_cidrs = ["{SECOND_CIDR}"]\n'))
        result = self.run_seed("--env", "prod")
        self.assertEqual(result.returncode, 1)
        self.assertIn("assigns ingress_allowed_cidrs 2 times", result.stderr)

    def test_a_missing_file_is_refused_before_any_call(self):
        self.write("oregon", tfvars())
        result = self.run_seed("--env", "prod", "--apply")
        self.assertEqual(result.returncode, 1)
        self.assertIn("seoul/prod.tfvars; copy it from a checkout that has it, or write it", result.stderr)
        self.assertEqual(self.calls(), [])

    def test_an_unknown_env_or_flag_prints_the_usage(self):
        for args in (["--env", "dev"], ["--env"], ["--env", "prod", "--force"], []):
            with self.subTest(args=args):
                result = self.run_seed(*args)
                self.assertEqual(result.returncode, 2)
                self.assertIn("usage: seed-tf-inputs.sh --env <prod|staging> [--apply]", result.stderr)


if __name__ == "__main__":
    unittest.main()
