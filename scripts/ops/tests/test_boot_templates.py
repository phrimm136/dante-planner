import json
import re
import subprocess
import tempfile
import unittest
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[3]
USER_DATA_DIR = REPO_ROOT / "terraform" / "modules" / "fleet" / "user-data"
VARIABLES_FILE = REPO_ROOT / "terraform" / "modules" / "fleet" / "variables.tf"
TERRAFORM_RUNNER = REPO_ROOT / "scripts" / "ops" / "terraform-run.sh"
EXEC_PATTERN = re.compile(r'INSTALL_K3S_EXEC="((?:[^"\\]|\\.)*)"', re.DOTALL)
CONFIG_PATTERN = re.compile(r"cat > /etc/rancher/k3s/config\.yaml <<'(\w+)'\n(.*?)\n\1\n", re.DOTALL)
FORBIDDEN_EXEC_CHARS = set("'\"*?[")
SNAPSHOT_KEYS = ("etcd-s3", "etcd-s3-bucket", "etcd-s3-region", "etcd-snapshot-schedule-cron",
                 "etcd-snapshot-retention")
SNAPSHOT_FLAG_PREFIXES = ("etcd-s3", "etcd-snapshot")
CRON_PATTERN = re.compile(r"[^\s\\]+( [^\s\\]+){4}")

SAMPLE_VARS = {
    "region": "us-west-2", "k3s_version": "v1.36.3+k3s1", "region_suffix": "oregon",
    "token_param": "/p/oregon/k3s-join-token", "kubeconfig_param": "/p/oregon/kubeconfig",
    "s3_bucket": "p-oregon-etcd-snapshots-bucket", "snapshot_retention": 5,
    "gitops_repo_url": "https://example.invalid/repo.git", "gitops_revision": "main",
    "argocd_version": "v3.0.0", "gateway_api_version": "v1.3.0", "traefik_version": "v3.1.0",
    "eso_chart_version": "0.10.4", "ecr_image": "danteplanner-backend=registry.invalid/backend:*",
    "install_scrape_annotate": "echo scrape-annotate",
    "server_url": "https://10.0.0.1:6443", "node_label": "role=app", "cred_provider_version": "v1.31.0",
    "set_nofile": True,
}


def interval_hours():
    variables_text = VARIABLES_FILE.read_text(encoding="utf-8")
    block_match = re.search(r'variable "etcd_snapshot_interval_hours" \{.*?default\s*=\s*(\d+)', variables_text,
                            re.DOTALL)
    return int(block_match.group(1))


def render(template_path, template_vars):
    with tempfile.TemporaryDirectory() as work_dir:
        console_input = f"jsonencode(templatefile({json.dumps(str(template_path))}, {json.dumps(template_vars)}))"
        console_run = subprocess.run([str(TERRAFORM_RUNNER), f"-chdir={work_dir}", "console"], input=console_input,
                                     capture_output=True, text=True, check=False)
    if console_run.returncode != 0:
        raise AssertionError(f"terraform console failed: {console_run.stderr}")
    return json.loads(json.loads(console_run.stdout))


def boot_problems(script_text):
    problem_texts = []
    exec_values = [exec_match.group(1) for exec_match in EXEC_PATTERN.finditer(script_text)]
    if len(exec_values) != 1:
        problem_texts.append(f"{len(exec_values)} INSTALL_K3S_EXEC values, want 1")
    for exec_value in exec_values:
        bad_chars = sorted(FORBIDDEN_EXEC_CHARS & set(exec_value))
        if bad_chars:
            problem_texts.append(f"INSTALL_K3S_EXEC holds {''.join(bad_chars)}, which the installer escapes")
        for exec_word in exec_value.split():
            if exec_word.lstrip("-").startswith(SNAPSHOT_FLAG_PREFIXES):
                problem_texts.append(f"INSTALL_K3S_EXEC carries the snapshot setting {exec_word}")
    return problem_texts


def config_problems(script_text):
    config_matches = CONFIG_PATTERN.findall(script_text)
    if len(config_matches) != 1:
        return [f"{len(config_matches)} config.yaml heredocs, want 1"]
    config_data = yaml.safe_load(config_matches[0][1])
    problem_texts = [f"config.yaml lacks {key_name}" for key_name in SNAPSHOT_KEYS if key_name not in config_data]
    if config_data.get("etcd-s3") is not True:
        problem_texts.append("etcd-s3 is not the boolean true")
    if not isinstance(config_data.get("etcd-snapshot-retention"), int):
        problem_texts.append("etcd-snapshot-retention is not a whole number")
    cron_text = config_data.get("etcd-snapshot-schedule-cron")
    if not isinstance(cron_text, str) or not CRON_PATTERN.fullmatch(cron_text):
        problem_texts.append(f"etcd-snapshot-schedule-cron {cron_text!r} is not five fields without backslashes")
    return problem_texts


class BootTemplateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cp_text = render(USER_DATA_DIR / "cp.sh.tftpl", {**SAMPLE_VARS, "snapshot_hours": interval_hours()})
        cls.agent_text = render(USER_DATA_DIR / "agent.sh.tftpl", SAMPLE_VARS)

    def test_the_boot_scripts_pass_no_quote_glob_or_snapshot_setting_to_the_installer(self):
        self.assertEqual(boot_problems(self.cp_text), [])
        self.assertEqual(boot_problems(self.agent_text), [])

    def test_the_cp_writes_the_snapshot_settings_into_config_yaml(self):
        self.assertEqual(config_problems(self.cp_text), [])
        config_data = yaml.safe_load(CONFIG_PATTERN.findall(self.cp_text)[0][1])
        self.assertEqual(config_data["etcd-snapshot-schedule-cron"], f"0 */{interval_hours()} * * *")
        self.assertEqual(config_data["etcd-s3-bucket"], SAMPLE_VARS["s3_bucket"])

    def test_the_config_is_written_before_the_installer_starts_k3s(self):
        self.assertLess(self.cp_text.index("/etc/rancher/k3s/config.yaml"), self.cp_text.index("get.k3s.io"))

    def test_a_quoted_cron_in_the_exec_string_fails(self):
        broken_text = self.cp_text.replace('--etcd-expose-metrics=true"',
                                           "--etcd-expose-metrics=true --etcd-snapshot-schedule-cron '0 */6 * * *'\"")
        problem_texts = boot_problems(broken_text)
        self.assertIn("INSTALL_K3S_EXEC holds '*, which the installer escapes", problem_texts)
        self.assertIn("INSTALL_K3S_EXEC carries the snapshot setting --etcd-snapshot-schedule-cron", problem_texts)

    def test_a_snapshot_flag_in_the_exec_string_fails(self):
        broken_text = self.cp_text.replace('--etcd-expose-metrics=true"', '--etcd-expose-metrics=true --etcd-s3"')
        self.assertEqual(boot_problems(broken_text), ["INSTALL_K3S_EXEC carries the snapshot setting --etcd-s3"])

    def test_an_escaped_or_short_cron_in_config_yaml_fails(self):
        cron_line = f'etcd-snapshot-schedule-cron: "0 */{interval_hours()} * * *"'
        for broken_line in ('etcd-snapshot-schedule-cron: "0 \\\\*/6 \\\\* \\\\* \\\\*"',
                            'etcd-snapshot-schedule-cron: "0 */6 * *"'):
            problem_texts = config_problems(self.cp_text.replace(cron_line, broken_line))
            self.assertEqual(len(problem_texts), 1, problem_texts)
            self.assertIn("is not five fields without backslashes", problem_texts[0])


if __name__ == "__main__":
    unittest.main()
