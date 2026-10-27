#!/usr/bin/env python3
"""Fail when a Grafana alert rule or fleet-dashboard panel queries a metric the
remote_write keep regex in deploy/base/prometheus.yaml does not admit, or selects
only series its label drop rules remove."""

import json
import re
import sys
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]
PROMETHEUS_CONFIG = REPO_ROOT / "deploy/base/prometheus.yaml"
GRAFANA_DIR = REPO_ROOT / "deploy/grafana"
DASHBOARD_FILE = GRAFANA_DIR / "fleet-dashboard.json"

PROMQL_KEYWORDS = {
    "and", "or", "unless", "by", "without", "on", "ignoring",
    "group_left", "group_right", "bool", "offset", "inf", "nan", "atan2",
}

SHELL_STRING = r"""'([^']*)'|"((?:[^"\\]|\\.)*)\""""
POST_RULE_CALL = re.compile(
    r'post_rule\s+"[^"]*"\s+"[^"]*"\s*(?:\\\n\s*)?(?:' + SHELL_STRING + r")"
    r'(?:(?:[ \t]*\\\n\s*|[ \t]+)("[^"]*"|\'[^\']*\'|[^\s\\]+))?'
)
LOKI_DATASOURCE_ARGS = {'"$LOKI_DS_UID"', '"${LOKI_DS_UID}"', "$LOKI_DS_UID", "${LOKI_DS_UID}"}
POST_RULE_SITE = re.compile(r'^\s*post_rule\s+"', re.MULTILINE)
SHELL_ASSIGNMENT = r"^\s*{name}=(?:" + SHELL_STRING + r")"
SHELL_VARIABLE = re.compile(r'\$\{?([A-Za-z_][A-Za-z0-9_]*)\}?')
JQ_EXPR_LITERAL = re.compile(r'expr:\s*"((?:[^"\\]|\\.)*)"')
JQ_EXPR_CONCAT = re.compile(r'expr:\s*\(((?:"(?:[^"\\]|\\.)*"|[^()"])*)\)')
JQ_STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')
SERIES_SELECTOR = re.compile(r"(?<![\w:])([A-Za-z_:][A-Za-z0-9_:]*)\s*\{([^}]*)\}")
LABEL_MATCHER = re.compile(r'([A-Za-z_][A-Za-z0-9_]*)\s*(=~|!=|!~|=)\s*"((?:[^"\\]|\\.)*)"')
REGEX_METACHARACTERS = set(".^$*+?()[]{}\\")


def relabel_configs_from_config(config_text):
    config_docs = [
        config_doc
        for manifest_doc in yaml.safe_load_all(config_text)
        if manifest_doc and manifest_doc.get("kind") == "ConfigMap"
        for config_doc in (yaml.safe_load(data_value) for data_value in manifest_doc.get("data", {}).values())
        if isinstance(config_doc, dict) and "remote_write" in config_doc
    ]
    if len(config_docs) != 1:
        sys.exit(f"expected one ConfigMap entry holding remote_write in {PROMETHEUS_CONFIG}, found {len(config_docs)}")
    relabel_configs = [
        relabel_config
        for remote_write in config_docs[0]["remote_write"]
        for relabel_config in remote_write.get("write_relabel_configs") or []
    ]
    if not relabel_configs:
        sys.exit(f"no write_relabel_configs in {PROMETHEUS_CONFIG}")
    return relabel_configs


def keep_regex_from_config(config_text):
    keep_regexes = [
        str(relabel_config.get("regex", "(.*)"))
        for relabel_config in relabel_configs_from_config(config_text)
        if relabel_config.get("action") == "keep" and relabel_config.get("source_labels") == ["__name__"]
    ]
    if len(keep_regexes) != 1:
        sys.exit(f"expected one __name__ keep rule in {PROMETHEUS_CONFIG}, found {len(keep_regexes)}")
    return re.compile(keep_regexes[0])


def drop_rules_from_config(config_text):
    drop_rules = []
    for relabel_config in relabel_configs_from_config(config_text):
        if relabel_config.get("action") != "drop":
            continue
        source_labels = relabel_config.get("source_labels") or []
        drop_regex = str(relabel_config.get("regex", "(.*)"))
        if len(source_labels) != 2 or source_labels[0] != "__name__":
            sys.exit(f"drop rule in {PROMETHEUS_CONFIG} is not on [__name__, <label>]: {relabel_config}")
        if relabel_config.get("separator", ";") != ";":
            sys.exit(f"drop rule in {PROMETHEUS_CONFIG} sets a non-default separator: {relabel_config}")
        if ";" not in drop_regex:
            sys.exit(f"drop rule in {PROMETHEUS_CONFIG} is not <name_re>;<value_re>: {relabel_config}")
        name_regex, value_regex = drop_regex.split(";", 1)
        drop_rules.append((source_labels[1], re.compile(name_regex), re.compile(value_regex)))
    return drop_rules


def matched_values(match_op, match_value):
    if match_op == "=":
        return [match_value]
    if match_op == "=~" and not REGEX_METACHARACTERS.intersection(match_value.replace("|", "")):
        return match_value.split("|")
    return None


def dropped_selections(promql_expr, drop_rules):
    selection_hits = []
    for selector_match in SERIES_SELECTOR.finditer(promql_expr):
        metric_name = selector_match.group(1)
        for label_matcher in LABEL_MATCHER.finditer(selector_match.group(2)):
            match_label, match_op, match_value = label_matcher.groups()
            match_value = unescape_double(match_value)
            selected_values = matched_values(match_op, match_value)
            if selected_values is None:
                continue
            for drop_label, name_regex, value_regex in drop_rules:
                if (
                    drop_label == match_label
                    and name_regex.fullmatch(metric_name)
                    and all(value_regex.fullmatch(selected_value) for selected_value in selected_values)
                ):
                    selection_hits.append((metric_name, match_label, match_op, match_value))
    return selection_hits


def unescape_double(quoted_body):
    return re.sub(r'\\(.)', r"\1", quoted_body)


def resolve_shell_string(single_body, double_body, script_text):
    if single_body is not None:
        return single_body
    expanded_body = unescape_double(double_body)
    whole_variable = SHELL_VARIABLE.fullmatch(expanded_body)
    if whole_variable:
        assignment_match = re.search(
            SHELL_ASSIGNMENT.format(name=whole_variable.group(1)), script_text, re.MULTILINE
        )
        if assignment_match:
            return resolve_shell_string(assignment_match.group(1), assignment_match.group(2), script_text)
    return SHELL_VARIABLE.sub("0", expanded_body)


def exprs_from_script(script_path):
    script_text = script_path.read_text()
    found_exprs = []
    rule_calls = list(POST_RULE_CALL.finditer(script_text))
    rule_sites = POST_RULE_SITE.findall(script_text)
    if len(rule_calls) != len(rule_sites):
        sys.exit(f"{script_path}: {len(rule_sites)} post_rule calls, only {len(rule_calls)} parsed")
    for rule_call in rule_calls:
        if rule_call.group(3) in LOKI_DATASOURCE_ARGS:
            continue
        rule_expr = resolve_shell_string(rule_call.group(1), rule_call.group(2), script_text)
        if not rule_expr.strip():
            sys.exit(f"{script_path}: empty expression in {rule_call.group(0)!r}")
        found_exprs.append(rule_expr)
    for literal_body in JQ_EXPR_LITERAL.findall(script_text):
        found_exprs.append(unescape_double(literal_body))
    for concat_body in JQ_EXPR_CONCAT.findall(script_text):
        found_exprs.append("".join(unescape_double(string_piece) for string_piece in JQ_STRING.findall(concat_body)))
    return found_exprs


def exprs_from_dashboard(dashboard_path):
    dashboard_doc = json.loads(dashboard_path.read_text())
    found_exprs = []

    def collect_panels(panel_list):
        for panel_item in panel_list:
            panel_source = panel_item.get("datasource") or {}
            for query_target in panel_item.get("targets", []):
                target_source = query_target.get("datasource") or panel_source
                if target_source.get("type") == "prometheus":
                    found_exprs.append(query_target["expr"])
            collect_panels(panel_item.get("panels", []))

    collect_panels(dashboard_doc.get("panels", []))
    return found_exprs


def metric_names(promql_expr):
    stripped_expr = re.sub(r'"(?:[^"\\]|\\.)*"|\'(?:[^\'\\]|\\.)*\'|`[^`]*`', "", promql_expr)
    stripped_expr = re.sub(r"\{[^}]*\}|\[[^\]]*\]", " ", stripped_expr)
    stripped_expr = re.sub(
        r"\b(?:by|without|on|ignoring|group_left|group_right)\s*\([^)]*\)", " ", stripped_expr
    )
    stripped_expr = re.sub(r"\$[A-Za-z_][A-Za-z0-9_]*", " ", stripped_expr)
    stripped_expr = re.sub(r"(?<![\w:.])\d*\.?\d+(?:[eE][+-]?\d+)?[a-z]*", " ", stripped_expr)
    name_hits = re.finditer(r"[A-Za-z_:][A-Za-z0-9_:]*", stripped_expr)
    return {
        name_hit.group(0)
        for name_hit in name_hits
        if name_hit.group(0) not in PROMQL_KEYWORDS
        and not re.match(r"\s*\(", stripped_expr[name_hit.end():])
    }


def main():
    config_text = PROMETHEUS_CONFIG.read_text()
    keep_regex = keep_regex_from_config(config_text)
    drop_rules = drop_rules_from_config(config_text)
    expr_sources = [(script_path, exprs_from_script(script_path)) for script_path in sorted(GRAFANA_DIR.glob("*.sh"))]
    expr_sources.append((DASHBOARD_FILE, exprs_from_dashboard(DASHBOARD_FILE)))

    missing_names = {}
    dropped_hits = {}
    checked_names = set()
    for source_path, source_exprs in expr_sources:
        for promql_expr in source_exprs:
            for selection_hit in dropped_selections(promql_expr, drop_rules):
                dropped_hits.setdefault(selection_hit, set()).add(source_path.relative_to(REPO_ROOT))
            for metric_name in metric_names(promql_expr):
                checked_names.add(metric_name)
                if not keep_regex.fullmatch(metric_name):
                    missing_names.setdefault(metric_name, set()).add(source_path.relative_to(REPO_ROOT))

    expr_count = sum(len(source_exprs) for source_path, source_exprs in expr_sources)
    for metric_name in sorted(missing_names):
        source_list = ", ".join(sorted(str(source_path) for source_path in missing_names[metric_name]))
        print(f"NOT KEPT {metric_name}  ({source_list})")
    for metric_name, match_label, match_op, match_value in sorted(dropped_hits):
        source_list = ", ".join(
            sorted(str(source_path) for source_path in dropped_hits[(metric_name, match_label, match_op, match_value)])
        )
        print(f'DROPPED {metric_name}{{{match_label}{match_op}"{match_value}"}}  ({source_list})')
    print(
        f"{len(checked_names)} metrics from {expr_count} expressions checked, "
        f"{len(missing_names)} not admitted by the remote_write keep regex, "
        f"{len(dropped_hits)} selecting only series its drop rules remove"
    )
    return 1 if missing_names or dropped_hits else 0


if __name__ == "__main__":
    sys.exit(main())
