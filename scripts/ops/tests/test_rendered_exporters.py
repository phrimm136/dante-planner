import contextlib
import copy
import importlib.util
import io
import tempfile
import unittest
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[3]
RULE_PATH = REPO_ROOT / ".github" / "scripts" / "rendered-exporters.py"
FIXTURES = Path(__file__).resolve().parent / "fixtures"


def load_rule():
    rule_spec = importlib.util.spec_from_file_location("rendered_exporters", RULE_PATH)
    rule_module = importlib.util.module_from_spec(rule_spec)
    rule_spec.loader.exec_module(rule_module)
    return rule_module


RENDERED_EXPORTERS = load_rule()


def recorded_objects(file_name):
    fixture_text = (FIXTURES / file_name).read_text(encoding="utf-8")
    return [manifest for manifest in yaml.safe_load_all(fixture_text) if manifest]


RECORDED_CHARTS = recorded_objects("rendered-exporters.yaml")
RECORDED_REGIONS = {
    region_name: recorded_objects(f"rendered-region-{region_name}.yaml") for region_name in ("oregon", "seoul")
}

NODE = "DaemonSet node-exporter"
MYSQLD = "Deployment mysqld-exporter"
REDIS_AUTH = "Deployment redis-exporter-auth"
REDIS_RATELIMIT = "Deployment redis-exporter-ratelimit"
CNF_PROBLEM = f"{MYSQLD}: no single arg --config.my-cnf=<path> points at key my.cnf of a mounted Secret mysqld-exporter-dsn"
CA_PROBLEM = (
    f"{MYSQLD}: /etc/mysqld-certs is not a mount of ConfigMap rds-ca-bundle, "
    "so ssl-ca /etc/mysqld-certs/rds-global-bundle.pem is not its file"
)

SEOUL_MERGE_PROBLEM = (
    "seoul.yaml: the template of ExternalSecret mysqld-exporter-dsn does not merge, so its data keys are not in the Secret"
)
SEOUL_CNF_PROBLEM = "seoul.yaml: the template of ExternalSecret mysqld-exporter-dsn yields no key my.cnf"
SEOUL_CA_PROBLEM = "seoul.yaml: ConfigMap rds-ca-bundle has no key rds-global-bundle.pem, which ssl-ca names"
REDIS_PASSWORD_PROBLEM = (
    f"{REDIS_AUTH}: env REDIS_PASSWORD does not come from Secret backend-runtime-config, key AUTH_REDIS_PASSWORD"
)


def env_problem(env_name, secret_key):
    return f"{MYSQLD}: env {env_name} does not come from Secret mysqld-exporter-dsn, key {secret_key}"


def only_match(candidates):
    (single_match,) = candidates
    return single_match


class RenderedExportersTest(unittest.TestCase):
    def setUp(self):
        self.manifests = copy.deepcopy(RECORDED_CHARTS)
        self.regions = copy.deepcopy(RECORDED_REGIONS)

    def workload(self, subject):
        kind_name, workload_name = subject.split()
        return only_match(
            manifest
            for manifest in self.manifests
            if manifest["kind"] == kind_name and manifest["metadata"]["name"] == workload_name
        )

    def pod_template(self, subject):
        return self.workload(subject)["spec"]["template"]

    def first_container(self, subject):
        return self.pod_template(subject)["spec"]["containers"][0]

    def mysqld_mount(self, mount_path):
        return only_match(
            volume_mount
            for volume_mount in self.first_container(MYSQLD)["volumeMounts"]
            if volume_mount["mountPath"].rstrip("/") == mount_path
        )

    def mysqld_volume(self, volume_name):
        return only_match(
            pod_volume
            for pod_volume in self.pod_template(MYSQLD)["spec"]["volumes"]
            if pod_volume["name"] == volume_name
        )

    def env_entry(self, subject, env_name):
        return only_match(
            env_entry for env_entry in self.first_container(subject)["env"] if env_entry["name"] == env_name
        )

    def mysqld_env(self, env_name):
        return self.env_entry(MYSQLD, env_name)

    def seoul_object(self, kind_name):
        return only_match(manifest for manifest in self.regions["seoul"] if manifest["kind"] == kind_name)

    def seoul_template(self):
        return self.seoul_object("ExternalSecret")["spec"]["target"]["template"]

    def run_rule(self):
        with tempfile.TemporaryDirectory() as work_dir:
            rendered_path = Path(work_dir) / "chart-probe.yaml"
            rendered_path.write_text(yaml.safe_dump_all(self.manifests), encoding="utf-8")
            region_paths = []
            for region_name, region_objects in self.regions.items():
                region_path = Path(work_dir) / f"{region_name}.yaml"
                region_path.write_text(yaml.safe_dump_all(region_objects), encoding="utf-8")
                region_paths.append(str(region_path))
            rule_output = io.StringIO()
            with contextlib.redirect_stdout(rule_output):
                exit_code = RENDERED_EXPORTERS.main(
                    ["--regions", *region_paths, "--charts", str(rendered_path)]
                )
        return exit_code, rule_output.getvalue().splitlines()

    def assert_fails(self, *problem_lines):
        self.assertEqual(self.run_rule(), (1, list(problem_lines)))

    def test_the_recorded_renders_pass(self):
        self.assertEqual(self.run_rule(), (0, []))

    def test_an_absent_workload_fails(self):
        self.manifests.remove(self.workload(REDIS_AUTH))
        self.assert_fails(f"{REDIS_AUTH}: rendered 0 times, want once")

    def test_a_renamed_workload_is_absent(self):
        self.workload(NODE)["metadata"]["name"] = "prometheus-node-exporter"
        self.assert_fails(f"{NODE}: rendered 0 times, want once")

    def test_a_workload_rendered_twice_fails(self):
        self.manifests.append(copy.deepcopy(self.workload(REDIS_RATELIMIT)))
        self.assert_fails(f"{REDIS_RATELIMIT}: rendered 2 times, want once")

    def test_each_annotation_of_each_exporter_is_compared(self):
        expected_literals = {
            NODE: {"scrape": "true", "path": "/metrics", "port": "9100", "job": "node"},
            MYSQLD: {"scrape": "true", "path": "/metrics", "port": "9104", "job": "mysqld"},
            REDIS_AUTH: {"scrape": "true", "path": "/metrics", "port": "9121", "job": "redis-auth"},
            REDIS_RATELIMIT: {"scrape": "true", "path": "/metrics", "port": "9121", "job": "redis-ratelimit"},
        }
        for subject, literal_values in expected_literals.items():
            for short_name, expected_value in literal_values.items():
                with self.subTest(subject=subject, annotation=short_name):
                    self.setUp()
                    self.pod_template(subject)["metadata"]["annotations"][f"prometheus.io/{short_name}"] = "other"
                    self.assert_fails(
                        f"{subject}: pod annotation prometheus.io/{short_name} is 'other', want '{expected_value}'"
                    )

    def test_a_pod_without_annotations_names_all_four(self):
        del self.pod_template(REDIS_AUTH)["metadata"]["annotations"]
        self.assert_fails(
            f"{REDIS_AUTH}: pod annotation prometheus.io/scrape is None, want 'true'",
            f"{REDIS_AUTH}: pod annotation prometheus.io/path is None, want '/metrics'",
            f"{REDIS_AUTH}: pod annotation prometheus.io/port is None, want '9121'",
            f"{REDIS_AUTH}: pod annotation prometheus.io/job is None, want 'redis-auth'",
        )

    def test_the_app_label_of_each_exporter_is_compared(self):
        for subject in (NODE, MYSQLD, REDIS_AUTH, REDIS_RATELIMIT):
            with self.subTest(subject=subject):
                self.setUp()
                del self.pod_template(subject)["metadata"]["labels"]["app"]
                self.assert_fails(f"{subject}: pod label app is None, want '{subject.split()[1]}'")

    def test_an_added_node_arg_fails(self):
        self.first_container(NODE)["args"].append("--collector.textfile")
        self.assert_fails(f"{NODE}: unexpected arg --collector.textfile")

    def test_a_missing_node_arg_fails(self):
        self.first_container(NODE)["args"].remove("--collector.pressure")
        self.assert_fails(f"{NODE}: missing arg --collector.pressure")

    def test_a_repeated_node_arg_fails(self):
        self.first_container(NODE)["args"].append("--collector.netdev")
        self.assert_fails(f"{NODE}: repeated arg --collector.netdev")

    def test_the_order_of_node_args_does_not_matter(self):
        self.first_container(NODE)["args"].reverse()
        self.assertEqual(self.run_rule(), (0, []))

    def test_a_volume_of_the_hosts_root_fails(self):
        self.pod_template(NODE)["spec"]["volumes"].append({"name": "root", "hostPath": {"path": "/"}})
        self.assert_fails(f"{NODE}: volume root is the host's root")

    def test_a_mount_of_the_hosts_root_fails(self):
        self.pod_template(NODE)["spec"]["volumes"].append({"name": "root", "hostPath": {"path": "/"}})
        self.first_container(NODE)["volumeMounts"].append({"name": "root", "mountPath": "/host/root"})
        self.assert_fails(
            f"{NODE}: volume root is the host's root",
            f"{NODE}: container node-exporter mounts the host's root at /host/root",
        )

    def test_an_added_mysqld_arg_fails(self):
        self.first_container(MYSQLD)["args"].append("--collect.info_schema.tables")
        self.assert_fails(f"{MYSQLD}: unexpected arg --collect.info_schema.tables")

    def test_each_missing_mysqld_arg_fails(self):
        for mysqld_arg in (
            "--mysqld.address=$(MYSQLD_ADDRESS)",
            "--mysqld.username=$(MYSQL_EXPORTER_USER)",
            "--collect.perf_schema.eventsstatements",
            "--collect.perf_schema.tableiowaits",
        ):
            with self.subTest(arg=mysqld_arg):
                self.setUp()
                self.first_container(MYSQLD)["args"].remove(mysqld_arg)
                self.assert_fails(f"{MYSQLD}: missing arg {mysqld_arg}")

    def test_a_repeated_mysqld_arg_fails(self):
        self.first_container(MYSQLD)["args"].append("--collect.perf_schema.tableiowaits")
        self.assert_fails(f"{MYSQLD}: repeated arg --collect.perf_schema.tableiowaits")

    def test_each_mysqld_env_comes_from_its_key(self):
        for env_name, secret_key in (
            ("MYSQLD_ADDRESS", "endpoint"),
            ("MYSQL_EXPORTER_USER", "username"),
            ("MYSQLD_EXPORTER_PASSWORD", "password"),
        ):
            with self.subTest(env=env_name):
                self.setUp()
                self.mysqld_env(env_name)["valueFrom"]["secretKeyRef"]["key"] = "other"
                self.assert_fails(env_problem(env_name, secret_key))

    def test_a_mysqld_env_from_another_secret_fails(self):
        self.mysqld_env("MYSQLD_ADDRESS")["valueFrom"]["secretKeyRef"]["name"] = "other"
        self.assert_fails(env_problem("MYSQLD_ADDRESS", "endpoint"))

    def test_a_literal_mysqld_env_fails(self):
        password_env = self.mysqld_env("MYSQLD_EXPORTER_PASSWORD")
        del password_env["valueFrom"]
        password_env["value"] = "literal"
        self.assert_fails(env_problem("MYSQLD_EXPORTER_PASSWORD", "password"))

    def test_an_absent_mysqld_env_fails(self):
        self.first_container(MYSQLD)["env"].remove(self.mysqld_env("MYSQL_EXPORTER_USER"))
        self.assert_fails(env_problem("MYSQL_EXPORTER_USER", "username"))

    def test_a_missing_cnf_arg_fails(self):
        self.first_container(MYSQLD)["args"].remove("--config.my-cnf=/etc/cnf/my.cnf")
        self.assert_fails(f"{MYSQLD}: missing arg --config.my-cnf=/etc/cnf/my.cnf", CNF_PROBLEM)

    def test_a_cnf_arg_outside_the_mount_fails(self):
        self.mysqld_mount("/etc/cnf")["mountPath"] = "/etc/other"
        self.assert_fails(CNF_PROBLEM)

    def test_a_cnf_arg_that_names_another_key_fails(self):
        mysqld_args = self.first_container(MYSQLD)["args"]
        mysqld_args[mysqld_args.index("--config.my-cnf=/etc/cnf/my.cnf")] = "--config.my-cnf=/etc/cnf/password"
        self.assert_fails(
            f"{MYSQLD}: unexpected arg --config.my-cnf=/etc/cnf/password",
            f"{MYSQLD}: missing arg --config.my-cnf=/etc/cnf/my.cnf",
            CNF_PROBLEM,
        )

    def test_a_cnf_from_another_secret_fails(self):
        self.mysqld_volume("secret-config")["secret"]["secretName"] = "other"
        self.assert_fails(CNF_PROBLEM)

    def test_a_cnf_volume_that_leaves_the_key_out_fails(self):
        self.mysqld_volume("secret-config")["secret"]["items"] = [{"key": "username", "path": "my.cnf"}]
        self.assert_fails(CNF_PROBLEM)

    def test_a_missing_ca_mount_fails(self):
        self.first_container(MYSQLD)["volumeMounts"].remove(self.mysqld_mount("/etc/mysqld-certs"))
        self.assert_fails(CA_PROBLEM)

    def test_a_ca_from_another_config_map_fails(self):
        self.mysqld_volume("rds-ca")["configMap"]["name"] = "other"
        self.assert_fails(CA_PROBLEM)

    def test_a_ca_mounted_by_sub_path_fails(self):
        self.mysqld_mount("/etc/mysqld-certs")["subPath"] = "rds-global-bundle.pem"
        self.assert_fails(CA_PROBLEM)

    def test_each_redis_address_is_compared(self):
        for subject, address_text in (
            (REDIS_AUTH, "redis://redis-auth:6379"),
            (REDIS_RATELIMIT, "redis://redis-ratelimit:6379"),
        ):
            with self.subTest(subject=subject):
                self.setUp()
                self.env_entry(subject, "REDIS_ADDR")["value"] = "redis://myredis:6379"
                self.assert_fails(f"{subject}: env REDIS_ADDR is 'redis://myredis:6379', want '{address_text}'")

    def test_an_absent_redis_password_fails_on_auth(self):
        self.first_container(REDIS_AUTH)["env"].remove(self.env_entry(REDIS_AUTH, "REDIS_PASSWORD"))
        self.assert_fails(REDIS_PASSWORD_PROBLEM)

    def test_a_redis_password_from_another_secret_or_key_fails_on_auth(self):
        for ref_field in ("name", "key"):
            with self.subTest(field=ref_field):
                self.setUp()
                self.env_entry(REDIS_AUTH, "REDIS_PASSWORD")["valueFrom"]["secretKeyRef"][ref_field] = "other"
                self.assert_fails(REDIS_PASSWORD_PROBLEM)

    def test_a_redis_password_on_ratelimit_fails(self):
        self.first_container(REDIS_RATELIMIT)["env"].append(self.env_entry(REDIS_AUTH, "REDIS_PASSWORD"))
        self.assert_fails(f"{REDIS_RATELIMIT}: unexpected env REDIS_PASSWORD")

    def test_a_redis_arg_fails(self):
        for subject in (REDIS_AUTH, REDIS_RATELIMIT):
            with self.subTest(subject=subject):
                self.setUp()
                self.first_container(subject)["args"] = ["--is-tile38"]
                self.assert_fails(f"{subject}: unexpected arg --is-tile38")

    def test_an_added_redis_env_fails(self):
        for subject in (REDIS_AUTH, REDIS_RATELIMIT):
            with self.subTest(subject=subject):
                self.setUp()
                self.first_container(subject)["env"].append({"name": "REDIS_EXPORTER_NAMESPACE", "value": "other"})
                self.assert_fails(f"{subject}: unexpected env REDIS_EXPORTER_NAMESPACE")

    def test_each_secret_key_of_the_external_secret_is_required(self):
        for secret_key in ("endpoint", "username", "password"):
            with self.subTest(key=secret_key):
                self.setUp()
                data_entries = self.seoul_object("ExternalSecret")["spec"]["data"]
                only_match(entry for entry in data_entries if entry["secretKey"] == secret_key)["secretKey"] = "other"
                self.assert_fails(f"seoul.yaml: ExternalSecret mysqld-exporter-dsn yields no key {secret_key}")

    def test_a_template_that_does_not_merge_fails(self):
        del self.seoul_template()["mergePolicy"]
        self.assert_fails(SEOUL_MERGE_PROBLEM)

    def test_a_template_without_my_cnf_fails(self):
        self.seoul_template()["data"] = {"other.cnf": self.seoul_template()["data"]["my.cnf"]}
        self.assert_fails(SEOUL_CNF_PROBLEM)

    def test_an_external_secret_without_a_template_fails(self):
        del self.seoul_object("ExternalSecret")["spec"]["target"]["template"]
        self.assert_fails(SEOUL_MERGE_PROBLEM, SEOUL_CNF_PROBLEM)

    def test_an_absent_external_secret_fails(self):
        self.regions["seoul"].remove(self.seoul_object("ExternalSecret"))
        self.assert_fails(
            "seoul.yaml: ExternalSecret mysqld-exporter-dsn yields no key endpoint",
            "seoul.yaml: ExternalSecret mysqld-exporter-dsn yields no key password",
            "seoul.yaml: ExternalSecret mysqld-exporter-dsn yields no key username",
            SEOUL_MERGE_PROBLEM,
            SEOUL_CNF_PROBLEM,
        )

    def test_a_my_cnf_without_ssl_ca_fails(self):
        self.seoul_template()["data"]["my.cnf"] = "[client]\n"
        self.assert_fails("seoul.yaml: my.cnf of ExternalSecret mysqld-exporter-dsn names no ssl-ca")

    def test_a_ca_config_map_without_the_file_fails(self):
        ca_config_map = self.seoul_object("ConfigMap")
        ca_config_map["data"] = {"other.pem": ca_config_map["data"]["rds-global-bundle.pem"]}
        self.assert_fails(SEOUL_CA_PROBLEM)

    def test_an_absent_ca_config_map_fails(self):
        self.regions["seoul"].remove(self.seoul_object("ConfigMap"))
        self.assert_fails(SEOUL_CA_PROBLEM)

    def test_the_ca_mount_follows_the_rendered_my_cnf(self):
        self.seoul_template()["data"]["my.cnf"] = "[client]\nssl-ca=/etc/moved/rds-global-bundle.pem\n"
        self.assert_fails(
            f"{MYSQLD}: /etc/moved is not a mount of ConfigMap rds-ca-bundle, "
            "so ssl-ca /etc/moved/rds-global-bundle.pem is not its file"
        )

    def test_a_sidecar_before_the_exporter_is_not_read_as_the_exporter(self):
        for subject in (NODE, MYSQLD, REDIS_AUTH, REDIS_RATELIMIT):
            self.pod_template(subject)["spec"]["containers"].insert(0, {"name": "sidecar", "image": "probe"})
        self.assertEqual(self.run_rule(), (0, []))

    def test_a_renamed_container_fails(self):
        for subject, container_name in (
            (NODE, "node-exporter"),
            (MYSQLD, "prometheus-mysql-exporter"),
            (REDIS_AUTH, "prometheus-redis-exporter"),
            (REDIS_RATELIMIT, "prometheus-redis-exporter"),
        ):
            with self.subTest(subject=subject):
                self.setUp()
                self.first_container(subject)["name"] = "other"
                self.assert_fails(f"{subject}: no container {container_name}")

    def test_no_files_is_a_usage_error(self):
        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as usage_error:
                RENDERED_EXPORTERS.main([])
        self.assertEqual(usage_error.exception.code, 2)


if __name__ == "__main__":
    unittest.main()
