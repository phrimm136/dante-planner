import json
import os
import re
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[3]
USER_DATA_DIR = REPO_ROOT / "terraform" / "modules" / "fleet" / "user-data"
BOOT_LIB_FILE = USER_DATA_DIR / "boot-lib.sh"
VARIABLES_FILE = REPO_ROOT / "terraform" / "modules" / "fleet" / "variables.tf"
TERRAFORM_RUNNER = REPO_ROOT / "scripts" / "ops" / "terraform-run.sh"
EXEC_PATTERN = re.compile(r'INSTALL_K3S_EXEC="((?:[^"\\]|\\.)*)"', re.DOTALL)
CONFIG_PATTERN = re.compile(r"cat > /etc/rancher/k3s/config\.yaml <<'(\w+)'\n(.*?)\n\1\n", re.DOTALL)
FORBIDDEN_EXEC_CHARS = set("'\"*?[")
SNAPSHOT_KEYS = ("etcd-s3", "etcd-s3-bucket", "etcd-s3-region", "etcd-snapshot-schedule-cron",
                 "etcd-snapshot-retention")
SNAPSHOT_FLAG_PREFIXES = ("etcd-s3", "etcd-snapshot")
ROOT_PATCH_PATTERN = re.compile(
    r"^kubectl -n argocd patch application danteplanner-(\S+) --type merge -p '(.*)'$", re.MULTILINE)
CRON_PATTERN = re.compile(r"[^\s\\]+( [^\s\\]+){4}")
TOKEN_VARIABLE_PATTERN = re.compile(r"K3S_TOKEN=|(?<!IMDS_)TOKEN=\$\(")
HEREDOC_PATTERN = re.compile(r"<<'?(\w+)'?$")
FUNCTION_PATTERN = re.compile(r"^(\w+)\(\) \{$")
RETRY_WORD_PATTERN = re.compile(r"\bretry (?=\w)")
NETWORK_PATTERN = re.compile(
    r"\b(?:curl|dnf install|helm repo|helm upgrade|git clone|aws ssm|(?:sh|bash) /root/|kubectl apply\b.*https://)")

SAMPLE_VARS = {
    "region": "us-west-2", "k3s_version": "v1.36.3+k3s1", "region_suffix": "oregon",
    "token_param": "/p/oregon/k3s-join-token", "kubeconfig_param": "/p/oregon/kubeconfig",
    "s3_bucket": "p-oregon-etcd-snapshots-bucket", "snapshot_retention": 5,
    "gitops_repo_url": "https://example.invalid/repo.git", "gitops_revision": "main",
    "argocd_version": "v3.0.0", "gateway_api_version": "v1.3.0", "traefik_version": "v3.1.0",
    "eso_chart_version": "0.10.4", "image_repository": "registry.invalid/backend",
    "install_scrape_annotate": "echo scrape-annotate",
    "server_url": "https://10.0.0.1:6443", "node_label": "role=app", "cred_provider_version": "v1.31.0",
    "set_nofile": True, "boot_lib": BOOT_LIB_FILE.read_text(encoding="utf-8"),
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


def merged(target, patch):
    if not isinstance(patch, dict):
        return patch
    result = dict(target) if isinstance(target, dict) else {}
    for key, value in patch.items():
        if value is None:
            result.pop(key, None)
        else:
            result[key] = merged(result.get(key), value)
    return result


def booted_source(script_text, region_suffix):
    (patched_suffix, patch_text), = ROOT_PATCH_PATTERN.findall(script_text)
    root_file = REPO_ROOT / "deploy" / "argocd" / f"root-app-{region_suffix}.yaml"
    root_app = yaml.safe_load(root_file.read_text(encoding="utf-8"))
    if root_app["metadata"]["name"] != f"danteplanner-{patched_suffix}":
        raise AssertionError(f"the patch names danteplanner-{patched_suffix}, {root_file.name} {root_app['metadata']['name']}")
    return merged(root_app, json.loads(patch_text))["spec"]["source"]


def source_problems(source, region_suffix):
    expected_helm = {
        "valueFiles": [f"values-{region_suffix}.yaml"],
        "parameters": [{"name": "image.repository", "value": SAMPLE_VARS["image_repository"]}],
    }
    problem_texts = []
    if source.get("path") != "deploy/chart":
        problem_texts.append(f"path is {source.get('path')}")
    if source.get("helm") != expected_helm:
        problem_texts.append(f"helm is {source.get('helm')}")
    if set(source) != {"repoURL", "targetRevision", "path", "helm"}:
        problem_texts.append(f"the source holds {sorted(source)}")
    if source.get("targetRevision") != SAMPLE_VARS["gitops_revision"]:
        problem_texts.append(f"targetRevision is {source.get('targetRevision')}")
    if not (REPO_ROOT / "deploy" / "chart" / f"values-{region_suffix}.yaml").is_file():
        problem_texts.append(f"values-{region_suffix}.yaml is missing")
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


def code_lines(script_text):
    logical_lines, heredoc_end = [], None
    for script_line in script_text.replace("\\\n", " ").split("\n"):
        if heredoc_end is not None:
            heredoc_end = None if script_line == heredoc_end else heredoc_end
        elif script_line.strip() and not script_line.lstrip().startswith("#"):
            logical_lines.append(script_line)
            heredoc_match = HEREDOC_PATTERN.search(script_line)
            heredoc_end = heredoc_match.group(1) if heredoc_match else None
    return logical_lines


def unretried_steps(script_text):
    logical_lines = code_lines(script_text)
    retried_names = set(re.findall(r"\bretry (\w+)", "\n".join(logical_lines)))
    problem_lines, function_name = [], None
    for code_line in logical_lines:
        function_match = FUNCTION_PATTERN.match(code_line)
        if function_match:
            function_name = function_match.group(1)
        elif code_line == "}":
            function_name = None
        elif function_name not in retried_names and any(
                not code_line[:step_match.start()].endswith("retry ")
                for step_match in NETWORK_PATTERN.finditer(code_line)):
            problem_lines.append(code_line.strip())
    return problem_lines


def shell_function(script_text, function_name):
    return re.search(rf"^{function_name}\(\) \{{\n.*?^\}}$", script_text, re.DOTALL | re.MULTILINE).group(0)


def run_shell(shell_text, stub_scripts, trace=False):
    with tempfile.TemporaryDirectory() as stub_dir:
        for command_name, stub_text in stub_scripts.items():
            stub_path = Path(stub_dir) / command_name
            stub_path.write_text(f"#!/bin/bash\n{stub_text}\n", encoding="utf-8")
            stub_path.chmod(0o755)
        return subprocess.run(["bash", *(["-x"] if trace else []), "-c", f"umask 022\n{shell_text}"],
                              env={**os.environ, "PATH": f"{stub_dir}:{os.environ['PATH']}"},
                              capture_output=True, text=True, check=False)


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

    def test_the_boot_patch_gives_each_root_application_its_chart_source(self):
        for region_suffix in ("oregon", "seoul"):
            with self.subTest(region_suffix):
                cp_text = render(USER_DATA_DIR / "cp.sh.tftpl",
                                 {**SAMPLE_VARS, "snapshot_hours": interval_hours(), "region_suffix": region_suffix})
                self.assertEqual(source_problems(booted_source(cp_text, region_suffix), region_suffix), [])

    def test_a_boot_patch_that_sets_a_second_tool_or_replaces_the_values_files_fails(self):
        second_tool_text = self.cp_text.replace('"helm":{"parameters"', '"plugin":{},"helm":{"parameters"')
        self.assertEqual(source_problems(booted_source(second_tool_text, "oregon"), "oregon"),
                         ["the source holds ['helm', 'path', 'plugin', 'repoURL', 'targetRevision']"])
        values_text = self.cp_text.replace('"helm":{"parameters"', '"helm":{"valueFiles":[],"parameters"')
        (problem_text,) = source_problems(booted_source(values_text, "oregon"), "oregon")
        self.assertIn("'valueFiles': []", problem_text)

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

    def boot_scripts(self):
        return {"cp": self.cp_text, "agent": self.agent_text}

    def token_fetch(self, script_name, token_path):
        script_text = self.boot_scripts()[script_name]
        fetch_text = shell_function(script_text, "fetch_token")
        if script_name == "cp":
            (umask_line,) = re.findall(r"^umask .*$", script_text, re.MULTILINE)
            self.assertLess(script_text.index(f"\n{umask_line}\n"), script_text.index(fetch_text))
            fetch_text = f"{umask_line}\n{fetch_text}"
        return fetch_text.replace("/etc/k3s-token", str(token_path))

    def test_the_join_token_reaches_the_file_and_never_the_trace(self):
        for script_name in self.boot_scripts():
            with self.subTest(script_name), tempfile.TemporaryDirectory() as work_dir:
                token_path = Path(work_dir) / "k3s-token"
                fetch_run = run_shell(f"{self.token_fetch(script_name, token_path)}\nfetch_token",
                                      {"aws": "echo join-token-sentinel"}, trace=True)
                self.assertEqual(fetch_run.returncode, 0, fetch_run.stderr)
                self.assertEqual(token_path.read_text(encoding="utf-8"), "join-token-sentinel\n")
                self.assertEqual(stat.S_IMODE(token_path.stat().st_mode), 0o600)
                self.assertIn("aws ssm get-parameter", fetch_run.stderr)
                self.assertNotIn("join-token-sentinel", fetch_run.stdout + fetch_run.stderr)

    def test_a_failed_token_fetch_leaves_no_earlier_partial_token(self):
        for script_name in self.boot_scripts():
            with self.subTest(script_name), tempfile.TemporaryDirectory() as work_dir:
                token_path, mark_path = Path(work_dir) / "k3s-token", Path(work_dir) / "mark"
                shell_text = f"{SAMPLE_VARS['boot_lib']}\n{self.token_fetch(script_name, token_path)}\nretry fetch_token"
                fetch_run = run_shell(shell_text, {
                    "sleep": "true",
                    "aws": f"[ -e {mark_path} ] || echo partial-token\ntouch {mark_path}\nexit 1"})
                self.assertNotEqual(fetch_run.returncode, 0)
                self.assertEqual(token_path.read_text(encoding="utf-8"), "")

    def test_no_boot_script_holds_the_join_token_in_a_variable(self):
        for script_name, script_text in self.boot_scripts().items():
            with self.subTest(script_name):
                self.assertEqual(TOKEN_VARIABLE_PATTERN.findall(script_text), [])
                self.assertIn("--token-file /etc/k3s-token", EXEC_PATTERN.search(script_text).group(1))

    def test_the_installer_gets_its_settings_and_a_forced_restart(self):
        for script_name, script_text in self.boot_scripts().items():
            with self.subTest(script_name):
                install_run = run_shell(
                    f"CPV=/cpv CP_PRIVATE_IP=10.0.0.9\n{shell_function(script_text, 'install_k3s')}\ninstall_k3s",
                    {"sh": 'echo "$1|$INSTALL_K3S_FORCE_RESTART|$INSTALL_K3S_VERSION|$INSTALL_K3S_EXEC"'})
                script_path, force_restart, k3s_version, exec_value = install_run.stdout.split("|")
                self.assertIn(f"retry curl -sSfL -o {script_path} https://get.k3s.io", code_lines(script_text))
                self.assertEqual(force_restart, "true")
                self.assertEqual(k3s_version, SAMPLE_VARS["k3s_version"])
                self.assertIn("--token-file /etc/k3s-token", exec_value)

    def test_retry_stops_at_the_first_success_or_the_fifth_failure(self):
        for success_run, expected_runs, expected_code in ((3, 3, 0), (99, 5, 1)):
            with self.subTest(success_run), tempfile.TemporaryDirectory() as work_dir:
                count_path, sleep_path = Path(work_dir) / "count", Path(work_dir) / "slept"
                retry_run = run_shell(f"{SAMPLE_VARS['boot_lib']}\nretry flaky-step argument-sentinel", {
                    "sleep": f'echo "$1" >> {sleep_path}',
                    "flaky-step": f"echo run >> {count_path}\n[ $(wc -l < {count_path}) -ge {success_run} ]"})
                self.assertEqual(retry_run.returncode, expected_code)
                self.assertEqual(count_path.read_text(encoding="utf-8"), "run\n" * expected_runs)
                self.assertEqual(sleep_path.read_text(encoding="utf-8"), "10\n" * (expected_runs - 1))
                self.assertIn("flaky-step", retry_run.stderr)
                self.assertNotIn("argument-sentinel", retry_run.stdout + retry_run.stderr)

    def test_a_failed_clone_under_retry_runs_no_checkout(self):
        with tempfile.TemporaryDirectory() as work_dir:
            call_path = Path(work_dir) / "calls"
            fetch_text = shell_function(self.cp_text, "fetch_gitops").replace("/opt/gitops", f"{work_dir}/gitops")
            fetch_run = run_shell(f"set -euo pipefail\n{SAMPLE_VARS['boot_lib']}\n{fetch_text}\nretry fetch_gitops",
                                  {"sleep": "true", "git": f'echo "$1" >> {call_path}\nexit 1'})
            self.assertEqual(fetch_run.returncode, 1)
            self.assertEqual(call_path.read_text(encoding="utf-8"), "clone\n" * 5)

    def test_every_network_step_is_under_retry(self):
        for script_name, script_text in self.boot_scripts().items():
            with self.subTest(script_name):
                self.assertEqual(unretried_steps(script_text), [])

    def test_a_network_step_that_loses_its_retry_is_reported(self):
        for script_name, script_text in self.boot_scripts().items():
            script_lines = script_text.split("\n")
            for line_index, script_line in enumerate(script_lines):
                if script_line.startswith("#") or not RETRY_WORD_PATTERN.search(script_line):
                    continue
                with self.subTest(script_name, line=script_line):
                    broken_line = RETRY_WORD_PATTERN.sub("", script_line)
                    broken_lines = [*script_lines[:line_index], broken_line, *script_lines[line_index + 1:]]
                    self.assertEqual(len(unretried_steps("\n".join(broken_lines))), 1)

    def test_the_boot_scripts_parse_with_and_without_the_descriptor_limit(self):
        plain_agent = render(USER_DATA_DIR / "agent.sh.tftpl", {**SAMPLE_VARS, "set_nofile": False})
        self.assertIn("LimitNOFILE", self.agent_text)
        self.assertNotIn("LimitNOFILE", plain_agent)
        for script_name, script_text in {**self.boot_scripts(), "plain agent": plain_agent}.items():
            with self.subTest(script_name):
                parse_run = subprocess.run(["bash", "-n"], input=script_text, capture_output=True, text=True, check=False)
                self.assertEqual((parse_run.returncode, parse_run.stderr), (0, ""))


if __name__ == "__main__":
    unittest.main()
