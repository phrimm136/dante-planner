import dataclasses
import importlib.machinery
import importlib.util
import sys
from collections import Counter

from opscli.paths import (
    METRICS_SCRIPT, OpsError, PRESENCE_LABEL, READER_RANGE, RULES_LOADER, SITE_NAMES, log_stem)
from opscli.redaction import redacted_log
from opscli.session import child_environment, require_session, write_header

CHECKED_KIND = "prometheus"
ABSENT_FIELD = "absent_until_event"
STATUS_OK = "ok"
STATUS_ABSENT = "ok (absent until event)"
STATUS_MISSING = "NO SERIES"
STATUS_UNREADABLE = "unreadable"
STATUS_WIDTH = len(STATUS_ABSENT) + 1
EXIT_MISSING = 1
EXIT_UNREADABLE = 2


@dataclasses.dataclass(frozen=True)
class MetricRead:
    metric_name: str
    site_names: tuple
    absent_until_event: bool = False


@dataclasses.dataclass(frozen=True)
class RuleReads:
    rule_name: str
    metric_reads: tuple
    read_problem: str = ""


@dataclasses.dataclass(frozen=True)
class DeclaredRules:
    rule_reads: tuple
    skipped_kinds: tuple


@dataclasses.dataclass(frozen=True)
class SeriesPresence:
    metric_sites: dict
    read_problem: str = ""


def selector_sites(one_selector):
    pinned_names = {match_value for label_name, match_op, match_value in one_selector.label_matchers
                    if label_name == "cluster" and match_op == "="}
    return pinned_names or set(SITE_NAMES)


def metric_reads(series_selectors, absent_names=()):
    found_sites = {}
    for one_selector in series_selectors:
        found_sites.setdefault(one_selector.metric_name, set()).update(selector_sites(one_selector))
    return tuple(MetricRead(metric_name, tuple(sorted(site_set)), metric_name in absent_names)
                 for metric_name, site_set in sorted(found_sites.items()))


def rule_loader(repo_root):
    loader_file = repo_root / RULES_LOADER
    if not loader_file.is_file():
        raise OpsError(f"{loader_file} is missing; restore it with git checkout -- {RULES_LOADER}")
    module_loader = importlib.machinery.SourceFileLoader("grafana_rules", str(loader_file))
    module_spec = importlib.util.spec_from_loader("grafana_rules", module_loader)
    rules_module = importlib.util.module_from_spec(module_spec)
    module_loader.exec_module(rules_module)
    return rules_module


def declared_rules(repo_root):
    rules_module = rule_loader(repo_root)
    try:
        loaded_rules = rules_module.load_rules(rules_module.DEFAULT_FILES)
    except rules_module.RuleError as rule_problem:
        raise OpsError(f"the alert rules do not load ({rule_problem}); fix the rule file, then check it with "
                       f"python3 {RULES_LOADER} loki-cases") from None
    rule_reads = []
    for rule_entry in loaded_rules:
        if rule_entry.rule_kind != CHECKED_KIND:
            continue
        try:
            series_selectors = rules_module.expression_selectors(rule_entry.test_expr())
        except rules_module.ExprError as expr_problem:
            rule_reads.append(RuleReads(rule_entry.rule_name, (), f"expr not read: {expr_problem}"))
            continue
        if not series_selectors:
            rule_reads.append(RuleReads(rule_entry.rule_name, (), "expr not read: the expression names no metric"))
            continue
        rule_reads.append(RuleReads(rule_entry.rule_name, metric_reads(
            series_selectors, rule_entry.rule_fields.get(ABSENT_FIELD) or ())))
    skipped_kinds = Counter(rule_entry.rule_kind for rule_entry in loaded_rules if rule_entry.rule_kind != CHECKED_KIND)
    return DeclaredRules(tuple(rule_reads), tuple(sorted(skipped_kinds.items())))


def checked_metrics(rule_reads):
    return sorted({metric_read.metric_name for one_rule in rule_reads for metric_read in one_rule.metric_reads})


def presence_query(metric_names, window_text):
    return " or ".join(
        f'label_replace(count by (cluster) (last_over_time({metric_name}[{window_text}])), '
        f'"{PRESENCE_LABEL}", "{metric_name}", "", "")'
        for metric_name in metric_names)


def parse_presence(reader_text):
    metric_sites = {}
    for line_number, reader_line in enumerate(reader_text.splitlines(), 1):
        if not reader_line.strip():
            continue
        line_fields = reader_line.split()
        label_pairs = dict(label_pair.split("=", 1) for label_pair in line_fields[2].split(",")
                           if "=" in label_pair) if len(line_fields) == 3 else {}
        if PRESENCE_LABEL not in label_pairs:
            return SeriesPresence({}, f"reader line {line_number} is not <time> <value> <labels> with "
                                f"{PRESENCE_LABEL}=: {reader_line[:120]}")
        metric_sites.setdefault(label_pairs[PRESENCE_LABEL], set()).add(label_pairs.get("cluster", ""))
    return SeriesPresence(metric_sites)


def read_presence(reader_run):
    if reader_run.returncode != 0:
        problem_lines = [one_line for one_line in (reader_run.stderr or "").splitlines() if one_line.strip()]
        return SeriesPresence({}, f"the Mimir read exited {reader_run.returncode}"
                            + (f": {problem_lines[-1].strip()}" if problem_lines else ""))
    return parse_presence(reader_run.stdout)


def missing_reads(one_rule, presence_read, absent_declared):
    missing_parts = []
    for metric_read in one_rule.metric_reads:
        absent_sites = [site_name for site_name in metric_read.site_names
                        if site_name not in presence_read.metric_sites.get(metric_read.metric_name, set())]
        if absent_sites and metric_read.absent_until_event == absent_declared:
            missing_parts.append(f"{metric_read.metric_name} ({', '.join(absent_sites)})")
    return missing_parts


def verdict_line(status_text, rule_name, detail_text=""):
    return f"{status_text.ljust(STATUS_WIDTH)}{rule_name}" + (f"  {detail_text}" if detail_text else "")


def verdict_report(declared_set, presence_read, window_text):
    out_lines = []
    status_counts = Counter()
    for one_rule in declared_set.rule_reads:
        read_problem = one_rule.read_problem or presence_read.read_problem
        missing_parts = [] if read_problem else missing_reads(one_rule, presence_read, False)
        awaited_parts = [] if read_problem else missing_reads(one_rule, presence_read, True)
        if read_problem:
            out_lines.append(verdict_line(STATUS_UNREADABLE, one_rule.rule_name, read_problem))
            status_counts[STATUS_UNREADABLE] += 1
        elif missing_parts:
            out_lines.append(verdict_line(STATUS_MISSING, one_rule.rule_name, ", ".join(missing_parts)))
            status_counts[STATUS_MISSING] += 1
        elif awaited_parts:
            out_lines.append(verdict_line(STATUS_ABSENT, one_rule.rule_name, ", ".join(awaited_parts)))
            status_counts[STATUS_ABSENT] += 1
        else:
            out_lines.append(verdict_line(STATUS_OK, one_rule.rule_name))
            status_counts[STATUS_OK] += 1
    skipped_text = ", ".join(f"{kind_count} {kind_name}" for kind_name, kind_count in declared_set.skipped_kinds)
    out_lines.append(
        f"{len(declared_set.rule_reads)} {CHECKED_KIND} rules over {len(checked_metrics(declared_set.rule_reads))} metrics "
        f"in {', '.join(SITE_NAMES)}, window {window_text}: {status_counts[STATUS_OK]} ok, "
        f"{status_counts[STATUS_ABSENT]} absent until event, {status_counts[STATUS_MISSING]} with no series, {status_counts[STATUS_UNREADABLE]} unreadable"
        + (f"; not checked, they read no Mimir metric: {skipped_text}" if skipped_text else ""))
    if status_counts[STATUS_UNREADABLE]:
        return out_lines, EXIT_UNREADABLE
    return out_lines, EXIT_MISSING if status_counts[STATUS_MISSING] else 0


def run_series_check(repo_paths, window_text, profile_name, system_effects, run_moment):
    declared_set = declared_rules(repo_paths.repo_root)
    metric_names = checked_metrics(declared_set.rule_reads)
    require_session(repo_paths, profile_name, system_effects)
    command_argv = (str(repo_paths.repo_root / METRICS_SCRIPT), presence_query(metric_names, window_text),
                    READER_RANGE, READER_RANGE)
    repo_paths.log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_stem(repo_paths.log_dir, ("verify", "rules"), run_moment).with_suffix(".log")
    with redacted_log(log_path, "w") as log_file:
        write_header(log_file, command_argv, profile_name, repo_paths.repo_root)
        if metric_names:
            reader_run = system_effects.run_split(command_argv, child_environment(profile_name),
                                                  repo_paths.repo_root)
            log_file.write(reader_run.stderr + reader_run.stdout)
            presence_read = read_presence(reader_run)
        else:
            presence_read = SeriesPresence({})
        log_file.end_output()
        out_lines, exit_code = verdict_report(declared_set, presence_read, window_text)
        verdict_text = "".join(f"{one_line}\n" for one_line in out_lines)
        log_file.write(f"\n{verdict_text}")
        log_file.end_output()
        log_file.write(f"\n# ops: exit {exit_code}\n")
    sys.stdout.write(verdict_text)
    print(f"ops: exit {exit_code}, log {log_path}", file=sys.stderr)
    return exit_code
