import importlib.machinery
import importlib.util
import json
import os
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml

RULES_PY = Path(__file__).resolve().parents[1] / "rules.py"
CLUSTER_RULES = Path(__file__).resolve().parents[1] / "rules" / "cluster-rules.yaml"
INTERVAL_LIB = Path(__file__).resolve().parents[3] / "scripts" / "ops" / "lib" / "etcd-interval.sh"
RULES_LOADER = importlib.machinery.SourceFileLoader("grafana_rules", str(RULES_PY))
rules_module = importlib.util.module_from_spec(importlib.util.spec_from_loader("grafana_rules", RULES_LOADER))
RULES_LOADER.exec_module(rules_module)
SELECTOR_CASES = (
    ("((time() - max by (cluster) (kube_etcd_snapshot_creation_timestamp_seconds)) > (1.5 * 21600)) or (count by "
     "(cluster) (up{job=\"kube-state-metrics\"} == 1) unless count by (cluster) "
     "(kube_etcd_snapshot_creation_timestamp_seconds))",
     (("kube_etcd_snapshot_creation_timestamp_seconds", ()), ("up", (("job", "=", "kube-state-metrics"),)),
      ("kube_etcd_snapshot_creation_timestamp_seconds", ()))),
    ("max(redis_master_repl_offset{job=\"redis-auth\",cluster=\"seoul\"}) == bool 0",
     (("redis_master_repl_offset", (("job", "=", "redis-auth"), ("cluster", "=", "seoul"))),)),
    ("(1 - node_filesystem_avail_bytes{fstype!~\"tmpfs|overlay\"} / node_filesystem_size_bytes"
     "{fstype!~\"tmpfs|overlay\"}) * 100 > 85",
     (("node_filesystem_avail_bytes", (("fstype", "!~", "tmpfs|overlay"),)),
      ("node_filesystem_size_bytes", (("fstype", "!~", "tmpfs|overlay"),)))),
    ("max by (cluster, name) (resilience4j_circuitbreaker_state{name=~\"read-source-.+\",state=\"open\"}) > 0",
     (("resilience4j_circuitbreaker_state", (("name", "=~", "read-source-.+"), ("state", "=", "open"))),)),
    ("node_memory_MemAvailable_bytes < 209715200", (("node_memory_MemAvailable_bytes", ()),)),
    ("sum without (pod) (rate(aaa_total[5m:1m] offset 1h)) * on (cluster) group_left (region) bbb_info",
     (("aaa_total", ()), ("bbb_info", ()))),
    ("label_replace(up, \"dst\", \"$1 or src\", \"src\", \"(.*)\") > 1e3", (("up", ()),)),
    ("sum by (uri) (avg_over_time(uri:http_server_requests_seconds_count:rate5m[$__range])) * $__range_s > 0",
     (("uri:http_server_requests_seconds_count:rate5m", ()),)),
    ("sum(increase(logback_events_total{level=~\"warn|error\"}[${__interval}])) by (cluster, level)",
     (("logback_events_total", (("level", "=~", "warn|error"),)),)),
    ('up{path="a\\"b"}', (("up", (("path", "=", 'a"b'),)),)),
    ("vector(1)", ()),
    ("a atan2 b", (("a", ()), ("b", ()))),
    ("sum(a) BY (job)", (("a", ()),)),
    ("a > Inf", (("a", ()),)),
)
EXPR_PROBLEMS = (
    ("{__name__=\"up\"} == 0", "a selector without a metric name"),
    ("up{__name__=\"up\"} == 0", "names its metric through __name__"),
    ("up{job=\"x\" == 0", "never closes"),
    ("up{job=~x} == 0", "do not parse"),
)
PAYLOAD_ENV = {"FOLDER_UID": "folder-uid", "DS_UID": "prom-uid", "LOKI_DS_UID": "loki-uid", "CW_DS_UID": "cw-uid",
               "ETCD_INTERVAL": "21600"}


def prometheus_rule(rule_name="probe-rule", **field_overrides):
    rule_fields = {
        "name": rule_name, "kind": "prometheus", "for": "5m", "expr": "up == bool 0",
        "cases": {
            "fires": [{"series": [{"series": 'up{job="probe"}', "values": "0x20"}], "at": "10m"}],
            "quiet": [{"series": [{"series": 'up{job="probe"}', "values": "1x20"}], "at": "10m"}],
        },
    }
    rule_fields.update(field_overrides)
    return rule_fields


def loki_rule(rule_name="probe-logs", **field_overrides):
    rule_fields = {
        "name": rule_name, "kind": "loki", "for": "1m",
        "expr": 'sum(count_over_time({app="backend"} | json | level="ERROR" [5m])) > 0',
        "cases": {
            "fires": [{"lines": [{"stream": {"app": "backend"}, "line": '{"level":"ERROR"}'}]}],
            "quiet": [{"lines": [{"stream": {"app": "backend"}, "line": '{"level":"INFO"}'}]}],
        },
    }
    rule_fields.update(field_overrides)
    return rule_fields


def cloudwatch_rule(rule_name="probe-lag", **field_overrides):
    rule_fields = {
        "name": rule_name, "kind": "cloudwatch", "for": "5m", "metric": "ReplicaLag",
        "db_instance": "probe-db", "region": "ap-northeast-2", "threshold": 60,
    }
    rule_fields.update(field_overrides)
    return rule_fields


class SelectorTest(unittest.TestCase):
    def test_each_expression_yields_its_selectors_in_order(self):
        for expr_text, expected_selectors in SELECTOR_CASES:
            with self.subTest(expr_text=expr_text):
                self.assertEqual(tuple((one_selector.metric_name, one_selector.label_matchers)
                                       for one_selector in rules_module.expression_selectors(expr_text)),
                                 expected_selectors)

    def test_an_expression_it_cannot_read_says_why(self):
        for expr_text, problem_text in EXPR_PROBLEMS:
            with self.subTest(expr_text=expr_text):
                with self.assertRaisesRegex(rules_module.ExprError, re.escape(problem_text)):
                    rules_module.expression_selectors(expr_text)

    def test_every_committed_prometheus_rule_reads_metrics_named_in_its_expr(self):
        prometheus_rules = [rule_entry for rule_entry in rules_module.load_rules(rules_module.DEFAULT_FILES)
                            if rule_entry.rule_kind == "prometheus"]
        self.assertTrue(prometheus_rules)
        for rule_entry in prometheus_rules:
            expr_text = rule_entry.test_expr()
            with self.subTest(rule_name=rule_entry.rule_name):
                read_names = rules_module.metric_names(expr_text)
                self.assertTrue(read_names)
                for metric_name in read_names:
                    self.assertRegex(expr_text, rf"(?<![\w:]){re.escape(metric_name)}(?![\w:])")


class RulesCliTest(unittest.TestCase):
    def setUp(self):
        self.scratch_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.scratch_dir.cleanup)

    def rules_file(self, file_name, rule_list):
        file_path = Path(self.scratch_dir.name) / file_name
        file_path.write_text(yaml.safe_dump(rule_list, sort_keys=False))
        return file_path

    def run_rules(self, rule_files, *command_args, extra_env=None):
        run_env = {**os.environ, **PAYLOAD_ENV, **(extra_env or {})}
        file_args = [file_arg for rule_file in rule_files for file_arg in ("--rules", str(rule_file))]
        return subprocess.run([sys.executable, str(RULES_PY), *file_args, *command_args],
                              capture_output=True, text=True, env=run_env)

    def assert_load_error(self, rule_list, *expected_texts):
        rule_file = self.rules_file("probe-rules.yaml", rule_list)
        out_dir = Path(self.scratch_dir.name) / "promtool"
        for command_args in (("payloads", "probe-rules"), ("promtool", str(out_dir))):
            run_result = self.run_rules([rule_file], *command_args)
            self.assertNotEqual(run_result.returncode, 0, command_args)
            self.assertIn(str(rule_file), run_result.stderr)
            for expected_text in expected_texts:
                self.assertIn(expected_text, run_result.stderr)
            self.assertEqual(run_result.stdout, "")

    def test_prometheus_rule_without_fires_case_cannot_load(self):
        broken_rule = prometheus_rule()
        del broken_rule["cases"]["fires"]
        self.assert_load_error([broken_rule], "rule probe-rule", "cases.fires")

    def test_prometheus_rule_without_quiet_case_cannot_load(self):
        broken_rule = prometheus_rule()
        del broken_rule["cases"]["quiet"]
        self.assert_load_error([broken_rule], "rule probe-rule", "cases.quiet")

    def test_prometheus_rule_with_empty_fires_list_cannot_load(self):
        broken_rule = prometheus_rule()
        broken_rule["cases"]["fires"] = []
        self.assert_load_error([broken_rule], "rule probe-rule", "cases.fires")

    def test_loki_rule_without_fires_case_cannot_load(self):
        broken_rule = loki_rule()
        del broken_rule["cases"]["fires"]
        self.assert_load_error([broken_rule], "rule probe-logs", "cases.fires")

    def test_loki_rule_without_quiet_case_cannot_load(self):
        broken_rule = loki_rule()
        del broken_rule["cases"]["quiet"]
        self.assert_load_error([broken_rule], "rule probe-logs", "cases.quiet")

    def test_rule_without_cases_cannot_load(self):
        broken_rule = prometheus_rule()
        del broken_rule["cases"]
        self.assert_load_error([broken_rule], "rule probe-rule", "cases")

    def test_unknown_field_cannot_load(self):
        self.assert_load_error([prometheus_rule(severity="page")], "rule probe-rule", "unknown field(s) severity")

    def test_missing_required_field_cannot_load(self):
        broken_rule = prometheus_rule()
        del broken_rule["for"]
        self.assert_load_error([broken_rule], "rule probe-rule", "missing required field(s) for")

    def test_forbidden_fields_cannot_load(self):
        self.assert_load_error([cloudwatch_rule(expr="up")], "rule probe-lag", "expr not allowed on a cloudwatch rule")
        self.assert_load_error([cloudwatch_rule(cases=prometheus_rule()["cases"])], "rule probe-lag", "cases not allowed")
        self.assert_load_error([cloudwatch_rule(summary="lag")], "rule probe-lag", "summary not allowed")
        self.assert_load_error([loki_rule(params={"window": {"env": "WINDOW", "test": 5}})], "rule probe-logs",
                               "params not allowed on a loki rule")
        self.assert_load_error([prometheus_rule(threshold=60)], "rule probe-rule", "threshold not allowed")

    def test_absent_until_event_names_only_metrics_the_expr_reads(self):
        self.assert_load_error([prometheus_rule(absent_until_event=["down"])], "rule probe-rule",
                               "absent_until_event names down, which expr does not read")
        self.assert_load_error([prometheus_rule(absent_until_event="up")], "rule probe-rule",
                               "absent_until_event must be a list of one or more metric names")
        self.assert_load_error([prometheus_rule(absent_until_event=[])], "rule probe-rule",
                               "absent_until_event must be a list")
        self.assert_load_error([prometheus_rule(expr="{__name__=\"up\"} == 0", absent_until_event=["up"])],
                               "rule probe-rule", "absent_until_event needs a readable expr")
        self.assert_load_error([loki_rule(absent_until_event=["up"])], "rule probe-logs",
                               "absent_until_event not allowed on a loki rule")

    def test_absent_until_event_stays_out_of_grafana_and_promtool(self):
        rule_file = self.rules_file("probe-rules.yaml", [prometheus_rule(absent_until_event=["up"])])
        payload_result = self.run_rules([rule_file], "payloads", "probe-rules")
        self.assertEqual(payload_result.returncode, 0, payload_result.stderr)
        self.assertNotIn("absent_until_event", payload_result.stdout)
        out_dir = Path(self.scratch_dir.name) / "promtool"
        self.assertEqual(self.run_rules([rule_file], "promtool", str(out_dir)).returncode, 0)
        self.assertNotIn("absent_until_event", (out_dir / "rules.yaml").read_text())

    def test_unknown_kind_cannot_load(self):
        self.assert_load_error([prometheus_rule(kind="graphite")], "rule probe-rule", "unknown kind 'graphite'")

    def test_duplicate_name_across_files_cannot_load(self):
        first_file = self.rules_file("first-rules.yaml", [prometheus_rule("shared-name")])
        second_file = self.rules_file("second-rules.yaml", [loki_rule("shared-name")])
        run_result = self.run_rules([first_file, second_file], "loki-cases")
        self.assertNotEqual(run_result.returncode, 0)
        self.assertIn(f"{second_file}: rule shared-name: duplicate name, also in {first_file}", run_result.stderr)

    def test_undeclared_placeholder_cannot_load(self):
        self.assert_load_error([prometheus_rule(expr="up > {limit}")], "rule probe-rule",
                               "expr uses {limit}, which params does not declare")

    def test_unset_param_env_fails_payloads_only(self):
        param_rule = prometheus_rule(expr="up > {limit}", params={"limit": {"env": "PROBE_LIMIT", "test": 3}})
        rule_file = self.rules_file("probe-rules.yaml", [param_rule])
        unset_result = self.run_rules([rule_file], "payloads", "probe-rules", extra_env={"PROBE_LIMIT": ""})
        self.assertNotEqual(unset_result.returncode, 0)
        self.assertIn("rule probe-rule: param limit reads PROBE_LIMIT, which is not set", unset_result.stderr)
        set_result = self.run_rules([rule_file], "payloads", "probe-rules", extra_env={"PROBE_LIMIT": "7"})
        self.assertEqual(json.loads(set_result.stdout)["body"]["data"][0]["model"]["expr"], "up > 7")
        self.assertEqual(rules_module.load_rules([rule_file])[0].test_expr(), "up > 3")

    def test_substitution_leaves_selector_and_template_braces(self):
        braced_expr = 'max by (cluster) (up{job="probe",cluster=~"a|b"}) > {limit} or vector({limit}) * {{limit}}'
        param_rule = prometheus_rule(expr=braced_expr, params={"limit": {"env": "PROBE_LIMIT", "test": 3}},
                                     summary="{{ $labels.cluster }} {limit}")
        rule_file = self.rules_file("probe-rules.yaml", [param_rule])
        self.assertEqual(rules_module.load_rules([rule_file])[0].test_expr(),
                         'max by (cluster) (up{job="probe",cluster=~"a|b"}) > 3 or vector(3) * {{limit}}')
        payload_result = self.run_rules([rule_file], "payloads", "probe-rules", extra_env={"PROBE_LIMIT": "9"})
        self.assertEqual(json.loads(payload_result.stdout)["body"]["annotations"]["summary"],
                         "{{ $labels.cluster }} {limit}")

    def test_promtool_wraps_whole_expression_and_writes_one_test_per_case(self):
        drift_rule = prometheus_rule("drift-rule", expr='a{x="1"} == 1 or b{y="2"} == 1')
        rule_file = self.rules_file("probe-rules.yaml", [drift_rule, loki_rule(), cloudwatch_rule()])
        out_dir = Path(self.scratch_dir.name) / "promtool"
        run_result = self.run_rules([rule_file], "promtool", str(out_dir))
        self.assertEqual(run_result.returncode, 0, run_result.stderr)
        rule_doc = yaml.safe_load((out_dir / "rules.yaml").read_text())
        self.assertEqual(rule_doc["groups"], [{"name": "probe-rules", "rules": [
            {"alert": "drift-rule", "expr": 'count((a{x="1"} == 1 or b{y="2"} == 1) > 0)', "for": "5m"}]}])
        test_doc = yaml.safe_load((out_dir / "tests.yaml").read_text())
        self.assertEqual([test_group["name"] for test_group in test_doc["tests"]],
                         ["drift-rule fires[0]", "drift-rule quiet[0]"])
        self.assertEqual([test_group["alert_rule_test"][0]["exp_alerts"] for test_group in test_doc["tests"]], [[{}], []])

    def test_loki_cases_lists_each_case(self):
        rule_file = self.rules_file("probe-rules.yaml", [prometheus_rule(), loki_rule()])
        case_lines = [json.loads(case_text) for case_text in self.run_rules([rule_file], "loki-cases").stdout.splitlines()]
        self.assertEqual([(case_line["rule"], case_line["case"]) for case_line in case_lines],
                         [("probe-logs", "fires"), ("probe-logs", "quiet")])
        self.assertEqual(case_lines[0]["lines"], [{"stream": {"app": "backend"}, "line": '{"level":"ERROR"}'}])

    def test_payloads_build_each_kind(self):
        rule_file = self.rules_file("probe-rules.yaml", [prometheus_rule(), loki_rule(summary="{{ $labels.x }}"),
                                                         cloudwatch_rule()])
        payload_lines = [json.loads(payload_text) for payload_text in
                         self.run_rules([rule_file], "payloads", "probe-rules").stdout.splitlines()]
        prom_body, loki_body, cw_body = (payload_line["body"] for payload_line in payload_lines)
        self.assertEqual([payload_line["title"] for payload_line in payload_lines], ["probe-rule", "probe-logs", "probe-lag"])
        self.assertEqual((prom_body["ruleGroup"], prom_body["folderUID"]), ("probe-rules", "folder-uid"))
        self.assertEqual(prom_body["data"][0]["datasourceUid"], "prom-uid")
        self.assertNotIn("annotations", prom_body)
        self.assertEqual(loki_body["data"][0]["model"]["queryType"], "instant")
        self.assertEqual(loki_body["annotations"], {"summary": "{{ $labels.x }}", "description": "{{ $values.B }} in 5m"})
        self.assertEqual((cw_body["noDataState"], cw_body["data"][0]["datasourceUid"]), ("Alerting", "cw-uid"))
        self.assertEqual(cw_body["data"][2]["model"]["conditions"][0]["evaluator"], {"type": "gte", "params": [60]})

    def test_payloads_need_the_datasource_env(self):
        rule_file = self.rules_file("probe-rules.yaml", [loki_rule()])
        run_result = self.run_rules([rule_file], "payloads", "probe-rules", extra_env={"LOKI_DS_UID": ""})
        self.assertNotEqual(run_result.returncode, 0)
        self.assertIn("rule probe-logs: LOKI_DS_UID is not set", run_result.stderr)

    def test_the_deadman_cases_use_the_fleet_snapshot_interval(self):
        interval_run = subprocess.run(["bash", "-c", f'source "{INTERVAL_LIB}"; etcd_snapshot_interval_seconds'],
                                      capture_output=True, text=True, check=True)
        deadman_rule = next(rule_entry for rule_entry in yaml.safe_load(CLUSTER_RULES.read_text())
                            if rule_entry["name"] == "etcd-snapshot-deadman")
        self.assertEqual(deadman_rule["params"]["etcd_interval"]["test"], int(interval_run.stdout))

    def test_the_snapshot_age_panel_turns_red_when_the_deadman_fires(self):
        interval_run = subprocess.run(["bash", "-c", f'source "{INTERVAL_LIB}"; etcd_snapshot_interval_hours'],
                                      capture_output=True, text=True, check=True)
        dashboard_text = (CLUSTER_RULES.parents[1] / "fleet-dashboard.json").read_text()
        age_panel = next(panel_entry for panel_entry in json.loads(dashboard_text)["panels"]
                         if panel_entry["title"] == "etcd snapshot age (hours)")
        red_step = next(step_entry for step_entry in age_panel["fieldConfig"]["defaults"]["thresholds"]["steps"]
                        if step_entry["color"] == "red")
        self.assertEqual(red_step["value"], 1.5 * int(interval_run.stdout))

    def test_committed_rules_load(self):
        for group_name in ("cluster-rules", "app-rules"):
            run_result = subprocess.run([sys.executable, str(RULES_PY), "payloads", group_name], capture_output=True,
                                        text=True, env={**os.environ, **PAYLOAD_ENV})
            self.assertEqual(run_result.returncode, 0, run_result.stderr)
            self.assertTrue(run_result.stdout.strip())

    def run_audit(self, *live_rules):
        rule_file = self.rules_file("probe-rules.yaml", [prometheus_rule()])
        return subprocess.run([sys.executable, str(RULES_PY), "--rules", str(rule_file), "audit", "managed"],
                              input=json.dumps(live_rules), capture_output=True, text=True)

    def live_rule(self, rule_title, rule_uid, folder_uid="managed"):
        return {"title": rule_title, "uid": rule_uid, "folderUID": folder_uid}

    def test_audit_passes_one_copy_of_each_declared_rule_and_the_staleness_rules(self):
        run_result = self.run_audit(self.live_rule("probe-rule", "a"), self.live_rule("staleness-meta-oregon", "b"),
                                    self.live_rule("staleness-meta-seoul", "c"))
        self.assertEqual(run_result.returncode, 0, run_result.stderr)

    def test_audit_fails_on_a_second_copy_and_names_both_uids(self):
        run_result = self.run_audit(self.live_rule("probe-rule", "probe-rule"), self.live_rule("probe-rule", "old-uid"))
        self.assertEqual(run_result.returncode, 1)
        self.assertIn("uid probe-rule in the managed folder managed, uid old-uid in the managed folder managed", run_result.stderr)
        self.assertIn("keep the copy whose uid is the title", run_result.stderr)

    def test_audit_fails_on_a_second_copy_in_another_folder(self):
        run_result = self.run_audit(self.live_rule("probe-rule", "a"), self.live_rule("probe-rule", "b", "stray"))
        self.assertEqual(run_result.returncode, 1)
        self.assertIn("uid a in the managed folder managed, uid b in another folder stray", run_result.stderr)

    def test_audit_fails_on_an_undeclared_rule_in_the_managed_folder(self):
        run_result = self.run_audit(self.live_rule("renamed-away", "a"))
        self.assertEqual(run_result.returncode, 1)
        self.assertIn("undeclared: renamed-away (uid a)", run_result.stderr)

    def test_audit_leaves_an_undeclared_rule_of_another_folder_alone(self):
        run_result = self.run_audit(self.live_rule("probe-rule", "a"), self.live_rule("someone-elses", "b", "other"))
        self.assertEqual(run_result.returncode, 0, run_result.stderr)

    def test_audit_refuses_a_listing_that_holds_no_rules(self):
        self.assertEqual(self.run_audit().returncode, 1)

    def test_audit_refuses_an_error_body(self):
        run_result = subprocess.run([sys.executable, str(RULES_PY), "audit", "managed"], input='{"message":"denied"}',
                                    capture_output=True, text=True)
        self.assertEqual(run_result.returncode, 1)
        self.assertIn("not a list of Grafana rules", run_result.stderr)


if __name__ == "__main__":
    unittest.main()
