import contextlib
import copy
import importlib.util
import io
import tempfile
import unittest
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[3]
RULE_PATH = REPO_ROOT / ".github" / "scripts" / "rendered-redis-probes.py"
FIXTURES = Path(__file__).resolve().parent / "fixtures"


def load_rule():
    rule_spec = importlib.util.spec_from_file_location("rendered_redis_probes", RULE_PATH)
    rule_module = importlib.util.module_from_spec(rule_spec)
    rule_spec.loader.exec_module(rule_module)
    return rule_module


RENDERED_REDIS_PROBES = load_rule()


def recorded_objects(file_name):
    fixture_text = (FIXTURES / file_name).read_text(encoding="utf-8")
    return [manifest for manifest in yaml.safe_load_all(fixture_text) if manifest]


RECORDED_REGIONS = {
    region_name: recorded_objects(f"rendered-redis-{region_name}.yaml") for region_name in ("oregon", "seoul")
}

AUTH = "StatefulSet redis-auth"
RATELIMIT = "Deployment redis-ratelimit"
AUTH_READINESS = ["sh", "-c", '[ "$(REDISCLI_AUTH="$REDIS_AUTH_PASSWORD" redis-cli PING)" = PONG ]']
RATELIMIT_READINESS = ["sh", "-c", '[ "$(redis-cli PING)" = PONG ]']


def only_match(candidates):
    (single_match,) = candidates
    return single_match


def readiness_problem(subject, found_command, want_command):
    return f"{subject}: readinessProbe runs {found_command!r}, want {want_command!r}, which passes only on the reply PONG"


class RenderedRedisProbesTest(unittest.TestCase):
    def setUp(self):
        self.regions = copy.deepcopy(RECORDED_REGIONS)

    def container(self, region_name, subject):
        kind_name, workload_name = subject.split()
        workload = only_match(
            manifest
            for manifest in self.regions[region_name]
            if manifest["kind"] == kind_name and manifest["metadata"]["name"] == workload_name
        )
        return only_match(
            pod_container for pod_container in workload["spec"]["template"]["spec"]["containers"]
            if pod_container["name"] == "redis"
        )

    def run_rule(self):
        with tempfile.TemporaryDirectory() as work_dir:
            region_paths = []
            for region_name, region_objects in self.regions.items():
                region_path = Path(work_dir) / f"{region_name}.yaml"
                region_path.write_text(yaml.safe_dump_all(region_objects), encoding="utf-8")
                region_paths.append(str(region_path))
            rule_output = io.StringIO()
            with contextlib.redirect_stdout(rule_output):
                exit_code = RENDERED_REDIS_PROBES.main(region_paths)
        return exit_code, rule_output.getvalue().splitlines()

    def assert_fails(self, problem_lines):
        self.assertEqual(self.run_rule(), (1, problem_lines))

    def test_the_recorded_renders_pass(self):
        self.assertEqual(self.run_rule(), (0, []))

    def test_no_arguments_print_the_usage(self):
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(RENDERED_REDIS_PROBES.main([]), 2)

    def test_a_readiness_probe_that_trusts_the_exit_code_fails(self):
        trusting_command = ["sh", "-c", 'REDISCLI_AUTH="$REDIS_AUTH_PASSWORD" redis-cli PING']
        self.container("oregon", AUTH)["readinessProbe"]["exec"]["command"] = trusting_command
        self.assert_fails([f"oregon.yaml: {readiness_problem(AUTH, trusting_command, AUTH_READINESS)}"])

    def test_a_missing_readiness_probe_fails(self):
        del self.container("seoul", RATELIMIT)["readinessProbe"]
        self.assert_fails([f"seoul.yaml: {readiness_problem(RATELIMIT, None, RATELIMIT_READINESS)}"])

    def test_an_exec_liveness_probe_fails(self):
        self.container("oregon", RATELIMIT)["livenessProbe"] = {"exec": {"command": RATELIMIT_READINESS}}
        self.assert_fails([f"oregon.yaml: {RATELIMIT}: livenessProbe is not a tcpSocket probe on port 6379"])

    def test_a_tcp_probe_on_another_port_fails(self):
        self.container("oregon", AUTH)["livenessProbe"]["tcpSocket"]["port"] = 6380
        self.assert_fails([f"oregon.yaml: {AUTH}: livenessProbe is not a tcpSocket probe on port 6379"])

    def test_a_tcp_probe_by_port_number_passes(self):
        self.container("oregon", AUTH)["livenessProbe"]["tcpSocket"]["port"] = 6379
        self.assertEqual(self.run_rule(), (0, []))

    def test_rediscli_auth_on_the_container_fails(self):
        self.container("oregon", AUTH)["env"].append(
            {"name": "REDISCLI_AUTH", "valueFrom": {"secretKeyRef": {"name": "backend-runtime-config", "key": "AUTH_REDIS_PASSWORD"}}}
        )
        self.assert_fails(
            [f"oregon.yaml: {AUTH}: env REDISCLI_AUTH is set on the container, so every redis-cli run in it authenticates"]
        )

    def test_a_missing_workload_fails(self):
        self.regions["seoul"] = [manifest for manifest in self.regions["seoul"] if manifest["kind"] != "Deployment"]
        self.assert_fails([f"seoul.yaml: {RATELIMIT}: rendered 0 times, want once"])

    def test_a_renamed_container_fails(self):
        self.container("oregon", RATELIMIT)["name"] = "server"
        self.assert_fails([f"oregon.yaml: {RATELIMIT}: no container redis"])


if __name__ == "__main__":
    unittest.main()
