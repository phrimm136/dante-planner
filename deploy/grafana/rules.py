#!/usr/bin/env python3
"""Read the Grafana alert rule files under rules/ and emit provisioning bodies, promtool tests and Loki cases."""

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path

import yaml

RULES_DIR = Path(__file__).resolve().parent / "rules"
DEFAULT_FILES = [RULES_DIR / "cluster-rules.yaml", RULES_DIR / "app-rules.yaml"]

KIND_FIELDS = {
    "prometheus": ({"name", "kind", "for", "expr", "cases"}, {"summary", "params"}),
    "loki": ({"name", "kind", "for", "expr", "cases"}, {"summary"}),
    "cloudwatch": ({"name", "kind", "for", "metric", "db_instance", "region", "threshold"}, set()),
}
CASE_TYPES = ("fires", "quiet")
PLACEHOLDER = re.compile(r"(?<!\{)\{([A-Za-z_][A-Za-z0-9_]*)\}(?!\})")
SUMMARY_DESCRIPTION = "{{ $values.B }} in 5m"
TIME_RANGE = {"from": 600, "to": 0}
EXPR_DATASOURCE = {"type": "__expr__", "uid": "__expr__"}


class RuleError(Exception):
    pass


@dataclass
class AlertRule:
    source_file: Path
    group_name: str
    rule_fields: dict

    @property
    def rule_name(self):
        return self.rule_fields["name"]

    @property
    def rule_kind(self):
        return self.rule_fields["kind"]

    def fail_rule(self, problem_text):
        raise RuleError(f"{self.source_file}: rule {self.rule_name}: {problem_text}")

    def resolved_expr(self, value_of):
        param_specs = self.rule_fields.get("params") or {}
        return PLACEHOLDER.sub(lambda placeholder_match: value_of(placeholder_match.group(1), param_specs[placeholder_match.group(1)]), self.rule_fields["expr"])

    def test_expr(self):
        return self.resolved_expr(lambda param_name, param_spec: str(param_spec["test"]))

    def provisioning_expr(self):
        def env_value(param_name, param_spec):
            env_text = os.environ.get(param_spec["env"], "")
            if not env_text:
                self.fail_rule(f"param {param_name} reads {param_spec['env']}, which is not set")
            return env_text

        return self.resolved_expr(env_value)


def require_type(rule_entry, field_name, wanted_type, type_label):
    if not isinstance(rule_entry.rule_fields[field_name], wanted_type) or isinstance(rule_entry.rule_fields[field_name], bool):
        rule_entry.fail_rule(f"{field_name} must be {type_label}")


def check_mapping(rule_entry, where_text, mapping_value, wanted_keys):
    if not isinstance(mapping_value, dict):
        rule_entry.fail_rule(f"{where_text} must be a mapping with {', '.join(sorted(wanted_keys))}")
    unknown_keys = set(mapping_value) - wanted_keys
    missing_keys = wanted_keys - set(mapping_value)
    if unknown_keys:
        rule_entry.fail_rule(f"{where_text} has unknown field(s) {', '.join(sorted(map(str, unknown_keys)))}")
    if missing_keys:
        rule_entry.fail_rule(f"{where_text} is missing {', '.join(sorted(missing_keys))}")


def check_list(rule_entry, where_text, list_value):
    if not isinstance(list_value, list) or not list_value:
        rule_entry.fail_rule(f"{where_text} must be a list of one or more entries")


def check_string(rule_entry, where_text, string_value):
    if not isinstance(string_value, str) or not string_value:
        rule_entry.fail_rule(f"{where_text} must be a non-empty string")


def check_prometheus_case(rule_entry, where_text, case_entry):
    check_mapping(rule_entry, where_text, case_entry, {"series", "at"})
    check_string(rule_entry, f"{where_text}.at", case_entry["at"])
    check_list(rule_entry, f"{where_text}.series", case_entry["series"])
    for series_index, series_entry in enumerate(case_entry["series"]):
        series_where = f"{where_text}.series[{series_index}]"
        check_mapping(rule_entry, series_where, series_entry, {"series", "values"})
        check_string(rule_entry, f"{series_where}.series", series_entry["series"])
        check_string(rule_entry, f"{series_where}.values", series_entry["values"])


def check_loki_case(rule_entry, where_text, case_entry):
    check_mapping(rule_entry, where_text, case_entry, {"lines"})
    check_list(rule_entry, f"{where_text}.lines", case_entry["lines"])
    for line_index, line_entry in enumerate(case_entry["lines"]):
        line_where = f"{where_text}.lines[{line_index}]"
        check_mapping(rule_entry, line_where, line_entry, {"stream", "line"})
        check_string(rule_entry, f"{line_where}.line", line_entry["line"])
        stream_labels = line_entry["stream"]
        if not isinstance(stream_labels, dict) or not stream_labels or not all(
            isinstance(label_key, str) and isinstance(label_value, str) for label_key, label_value in stream_labels.items()
        ):
            rule_entry.fail_rule(f"{line_where}.stream must be a non-empty mapping of label to string value")


def check_params(rule_entry):
    param_specs = rule_entry.rule_fields.get("params")
    if param_specs is None:
        param_specs = {}
    elif not isinstance(param_specs, dict):
        rule_entry.fail_rule("params must be a mapping of name to {env, test}")
    for param_name, param_spec in param_specs.items():
        check_mapping(rule_entry, f"params.{param_name}", param_spec, {"env", "test"})
        check_string(rule_entry, f"params.{param_name}.env", param_spec["env"])
    for placeholder_name in PLACEHOLDER.findall(rule_entry.rule_fields["expr"]):
        if placeholder_name not in param_specs:
            rule_entry.fail_rule(f"expr uses {{{placeholder_name}}}, which params does not declare")


def check_rule(rule_entry):
    rule_kind = rule_entry.rule_kind
    if rule_kind not in KIND_FIELDS:
        rule_entry.fail_rule(f"unknown kind {rule_kind!r}; kinds are {', '.join(KIND_FIELDS)}")
    required_fields, optional_fields = KIND_FIELDS[rule_kind]
    present_fields = set(rule_entry.rule_fields)
    missing_fields = required_fields - present_fields
    extra_fields = present_fields - required_fields - optional_fields
    known_fields = set().union(*(kind_required | kind_optional for kind_required, kind_optional in KIND_FIELDS.values()))
    unknown_fields = extra_fields - known_fields
    if unknown_fields:
        rule_entry.fail_rule(f"unknown field(s) {', '.join(sorted(map(str, unknown_fields)))}")
    if extra_fields:
        rule_entry.fail_rule(f"field(s) {', '.join(sorted(extra_fields))} not allowed on a {rule_kind} rule")
    if missing_fields:
        rule_entry.fail_rule(f"missing required field(s) {', '.join(sorted(missing_fields))}")
    check_string(rule_entry, "for", rule_entry.rule_fields["for"])
    if rule_kind == "cloudwatch":
        for text_field in ("metric", "db_instance", "region"):
            check_string(rule_entry, text_field, rule_entry.rule_fields[text_field])
        require_type(rule_entry, "threshold", (int, float), "a number")
        return
    check_string(rule_entry, "expr", rule_entry.rule_fields["expr"])
    if "summary" in rule_entry.rule_fields:
        check_string(rule_entry, "summary", rule_entry.rule_fields["summary"])
    if rule_kind == "prometheus":
        check_params(rule_entry)
    elif PLACEHOLDER.search(rule_entry.rule_fields["expr"]):
        rule_entry.fail_rule(f"expr uses {{{PLACEHOLDER.search(rule_entry.rule_fields['expr']).group(1)}}}, but a {rule_kind} rule takes no params")
    rule_cases = rule_entry.rule_fields["cases"]
    if not isinstance(rule_cases, dict):
        rule_entry.fail_rule("cases must be a mapping with fires and quiet")
    unknown_cases = set(rule_cases) - set(CASE_TYPES)
    if unknown_cases:
        rule_entry.fail_rule(f"cases has unknown field(s) {', '.join(sorted(map(str, unknown_cases)))}")
    case_checker = check_prometheus_case if rule_kind == "prometheus" else check_loki_case
    for case_type in CASE_TYPES:
        if case_type not in rule_cases:
            rule_entry.fail_rule(f"missing cases.{case_type}")
        check_list(rule_entry, f"cases.{case_type}", rule_cases[case_type])
        for case_index, case_entry in enumerate(rule_cases[case_type]):
            case_checker(rule_entry, f"cases.{case_type}[{case_index}]", case_entry)


def load_rules(rule_files):
    loaded_rules = []
    seen_names = {}
    for rule_file in rule_files:
        try:
            file_doc = yaml.safe_load(Path(rule_file).read_text())
        except (OSError, yaml.YAMLError) as load_problem:
            raise RuleError(f"{rule_file}: {load_problem}") from load_problem
        if not isinstance(file_doc, list):
            raise RuleError(f"{rule_file}: must be a list of rules")
        for rule_index, rule_fields in enumerate(file_doc):
            if not isinstance(rule_fields, dict) or not isinstance(rule_fields.get("name"), str) or not rule_fields["name"]:
                raise RuleError(f"{rule_file}: rule #{rule_index + 1}: must be a mapping with a string name")
            rule_entry = AlertRule(Path(rule_file), Path(rule_file).stem, rule_fields)
            if "kind" not in rule_fields:
                rule_entry.fail_rule("missing required field(s) kind")
            check_rule(rule_entry)
            if rule_entry.rule_name in seen_names:
                rule_entry.fail_rule(f"duplicate name, also in {seen_names[rule_entry.rule_name]}")
            seen_names[rule_entry.rule_name] = rule_file
            loaded_rules.append(rule_entry)
    return loaded_rules


def required_env(env_name, rule_entry):
    env_text = os.environ.get(env_name, "")
    if not env_text:
        rule_entry.fail_rule(f"{env_name} is not set; the import script resolves it before building payloads")
    return env_text


def threshold_nodes(evaluator_type, evaluator_value):
    return [
        {"refId": "B", "relativeTimeRange": {"from": 0, "to": 0}, "datasourceUid": "__expr__",
         "model": {"refId": "B", "type": "reduce", "reducer": "last", "expression": "A",
                   "datasource": dict(EXPR_DATASOURCE)}},
        {"refId": "C", "relativeTimeRange": {"from": 0, "to": 0}, "datasourceUid": "__expr__",
         "model": {"refId": "C", "type": "threshold", "expression": "B",
                   "conditions": [{"evaluator": {"type": evaluator_type, "params": [evaluator_value]}}],
                   "datasource": dict(EXPR_DATASOURCE)}},
    ]


def provisioning_body(rule_entry):
    rule_fields = rule_entry.rule_fields
    folder_uid = required_env("FOLDER_UID", rule_entry)
    body_head = {
        "title": rule_entry.rule_name, "ruleGroup": rule_entry.group_name, "folderUID": folder_uid,
        "condition": "C", "for": rule_fields["for"],
    }
    if rule_entry.rule_kind == "cloudwatch":
        query_model = {
            "refId": "A", "queryMode": "Metrics", "namespace": "AWS/RDS", "metricName": rule_fields["metric"],
            "statistic": "Maximum", "period": "60", "region": rule_fields["region"], "matchExact": True,
            "dimensions": {"DBInstanceIdentifier": rule_fields["db_instance"]},
            "metricQueryType": 0, "metricEditorMode": 0, "expression": "", "id": "",
        }
        return {
            **body_head, "noDataState": "Alerting", "execErrState": "OK",
            "data": [
                {"refId": "A", "relativeTimeRange": dict(TIME_RANGE),
                 "datasourceUid": required_env("CW_DS_UID", rule_entry), "model": query_model},
                *threshold_nodes("gte", rule_fields["threshold"]),
            ],
        }
    query_model = {"refId": "A", "expr": rule_entry.provisioning_expr(), "instant": True}
    if rule_entry.rule_kind == "loki":
        query_model["queryType"] = "instant"
        datasource_uid = required_env("LOKI_DS_UID", rule_entry)
    else:
        datasource_uid = required_env("DS_UID", rule_entry)
    rule_body = {
        **body_head, "noDataState": "OK", "execErrState": "OK",
        "data": [
            {"refId": "A", "relativeTimeRange": dict(TIME_RANGE), "datasourceUid": datasource_uid, "model": query_model},
            *threshold_nodes("gt", 0),
        ],
    }
    if "summary" in rule_fields:
        rule_body["annotations"] = {"summary": rule_fields["summary"], "description": SUMMARY_DESCRIPTION}
    return rule_body


def emit_payloads(loaded_rules, group_name):
    group_names = sorted({rule_entry.group_name for rule_entry in loaded_rules})
    if group_name not in group_names:
        raise RuleError(f"no rule group {group_name!r}; groups are {', '.join(group_names)}")
    for rule_entry in loaded_rules:
        if rule_entry.group_name == group_name:
            payload_line = {"title": rule_entry.rule_name, "body": provisioning_body(rule_entry)}
            print(json.dumps(payload_line, ensure_ascii=False, separators=(",", ":")))


def wrapped_expr(rule_entry):
    return f"count(({rule_entry.test_expr()}) > 0)"


def emit_promtool(loaded_rules, out_dir):
    prometheus_rules = [rule_entry for rule_entry in loaded_rules if rule_entry.rule_kind == "prometheus"]
    group_order = list(dict.fromkeys(rule_entry.group_name for rule_entry in prometheus_rules))
    rule_doc = {"groups": [
        {"name": group_name, "rules": [
            {"alert": rule_entry.rule_name, "expr": wrapped_expr(rule_entry), "for": rule_entry.rule_fields["for"]}
            for rule_entry in prometheus_rules if rule_entry.group_name == group_name
        ]}
        for group_name in group_order
    ]}
    test_groups = []
    for rule_entry in prometheus_rules:
        for case_type in CASE_TYPES:
            for case_index, case_entry in enumerate(rule_entry.rule_fields["cases"][case_type]):
                test_groups.append({
                    "name": f"{rule_entry.rule_name} {case_type}[{case_index}]",
                    "interval": "1m",
                    "input_series": [
                        {"series": series_entry["series"], "values": series_entry["values"]}
                        for series_entry in case_entry["series"]
                    ],
                    "alert_rule_test": [{
                        "eval_time": case_entry["at"],
                        "alertname": rule_entry.rule_name,
                        "exp_alerts": [{}] if case_type == "fires" else [],
                    }],
                })
    test_doc = {"rule_files": ["rules.yaml"], "evaluation_interval": "1m", "tests": test_groups}
    out_path = Path(out_dir)
    out_path.mkdir(parents=True, exist_ok=True)
    (out_path / "rules.yaml").write_text(yaml.safe_dump(rule_doc, sort_keys=False, width=1000))
    (out_path / "tests.yaml").write_text(yaml.safe_dump(test_doc, sort_keys=False, width=1000))
    print(f"{len(prometheus_rules)} prometheus rules, {len(test_groups)} cases written to {out_path}")


def emit_loki_cases(loaded_rules):
    for rule_entry in loaded_rules:
        if rule_entry.rule_kind != "loki":
            continue
        for case_type in CASE_TYPES:
            for case_index, case_entry in enumerate(rule_entry.rule_fields["cases"][case_type]):
                case_line = {
                    "rule": rule_entry.rule_name, "case": case_type, "index": case_index,
                    "expr": rule_entry.rule_fields["expr"], "lines": case_entry["lines"],
                }
                print(json.dumps(case_line, ensure_ascii=False))


def emit_exprs(loaded_rules):
    for rule_entry in loaded_rules:
        if rule_entry.rule_kind == "prometheus":
            print(rule_entry.test_expr())


def main():
    arg_parser = argparse.ArgumentParser(description=__doc__)
    arg_parser.add_argument("--rules", action="append", type=Path, metavar="FILE",
                            help="a rules file to read instead of rules/*.yaml (repeatable)")
    command_parsers = arg_parser.add_subparsers(dest="command", required=True)
    payload_parser = command_parsers.add_parser("payloads", help="one Grafana provisioning body per line for a group")
    payload_parser.add_argument("group")
    promtool_parser = command_parsers.add_parser("promtool", help="write the wrapped rule file and promtool tests")
    promtool_parser.add_argument("out_dir")
    command_parsers.add_parser("loki-cases", help="one JSON line per Loki case")
    command_parsers.add_parser("exprs", help="every Prometheus expression, params at their test values")
    parsed_args = arg_parser.parse_args()
    try:
        loaded_rules = load_rules(parsed_args.rules or DEFAULT_FILES)
        if parsed_args.command == "payloads":
            emit_payloads(loaded_rules, parsed_args.group)
        elif parsed_args.command == "promtool":
            emit_promtool(loaded_rules, parsed_args.out_dir)
        elif parsed_args.command == "loki-cases":
            emit_loki_cases(loaded_rules)
        else:
            emit_exprs(loaded_rules)
    except RuleError as rule_problem:
        print(f"rules.py: {rule_problem}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
