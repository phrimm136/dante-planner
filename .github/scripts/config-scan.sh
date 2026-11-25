#!/usr/bin/env bash
set -Eeuo pipefail
shopt -s nullglob
trap 'echo "config-scan: line $LINENO exited $?" >&2; exit 2' ERR

usage() {
  echo "usage: $0 [--update] [repo-dir]   (default: the repository this script lives in; needs docker, git and python3 with PyYAML)" >&2
  exit 2
}

update=false
if [ "${1:-}" = "--update" ]; then
  update=true
  shift
fi
[ $# -le 1 ] || usage
case "${1:-}" in -*) usage ;; esac

fail_tool() {
  echo "config-scan: $1" >&2
  [ ! -s "${2:-/dev/null}" ] || cat "$2" >&2
  exit 2
}

script_dir=$(cd "$(dirname "$0")" && pwd)
. "$script_dir/images.sh"
CHECKOV_IMAGE=${CONFIG_SCAN_CHECKOV_IMAGE:-$CHECKOV_IMAGE}
repo=$(cd "${1:-$script_dir/../..}" 2>/dev/null && pwd) || fail_tool "repo-dir ${1:-} is not a directory"
baselines=$repo/.github/config-scan
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
trap 'exit 130' INT

regions=()
for values_file in "$repo"/deploy/chart/values-*.yaml; do
  region=$(basename "$values_file" .yaml)
  regions+=("${region#values-}")
done
[ "${#regions[@]}" -gt 0 ] || fail_tool "no values-<region>.yaml under $repo/deploy/chart"

mkdir -p "$work/terraform" "$work/kubernetes"
tracked=$(git -C "$repo" ls-files terraform) || fail_tool "git ls-files failed in $repo; the terraform input needs a git checkout"
[ -n "$tracked" ] || fail_tool "git ls-files terraform in $repo listed nothing"
git -C "$repo" ls-files -z terraform | tar -C "$repo" --null -T - -cf - | tar -C "$work/terraform" --strip-components=1 -xf - \
  || fail_tool "copying the tracked terraform files out of $repo failed"

rendered=()
for region in "${regions[@]}"; do
  if ! docker run --rm --network none -v "$repo/deploy/chart:/chart:ro" "$HELM_IMAGE" template "danteplanner-$region" /chart \
    -f "/chart/values-$region.yaml" >"$work/kubernetes/$region.yaml" 2>"$work/helm.err"; then
    fail_tool "helm template deploy/chart with values-$region.yaml failed" "$work/helm.err"
  fi
  [ -s "$work/kubernetes/$region.yaml" ] || fail_tool "helm template deploy/chart with values-$region.yaml printed nothing" "$work/helm.err"
  rendered+=("$work/kubernetes/$region.yaml")
done
"$script_dir/render-charts.sh" "$work/kubernetes" "${rendered[@]}" >/dev/null 2>"$work/charts.err" \
  || fail_tool "rendering the charts of the rendered Applications failed" "$work/charts.err"

scan() {
  local input=$1 status=0
  docker run --rm --network none --user "$(id -u):$(id -g)" -e HOME=/tmp \
    -v "$work/$input:/scan:ro" "$CHECKOV_IMAGE" \
    -d /scan --framework "$input" --skip-download --quiet --compact -o json \
    >"$work/$input.json" 2>"$work/$input.err" || status=$?
  if [ "$status" -ne 0 ] && [ "$status" -ne 1 ]; then
    fail_tool "checkov on the $input input exited $status" "$work/$input.err"
  fi
}
scan terraform
scan kubernetes

compare_status=0
python3 -I - "$update" \
  terraform "$work/terraform.json" "$work/terraform.err" "$baselines/terraform.baseline.json" "$work/terraform" \
  kubernetes "$work/kubernetes.json" "$work/kubernetes.err" "$baselines/kubernetes.baseline.json" "$work/kubernetes" <<'PY' || compare_status=$?
import json
import pathlib
import sys

import yaml

VERSION_LABELS = ("helm.sh/chart", "app.kubernetes.io/version")


class ToolFailure(Exception):
    pass


def pod_template(workload):
    spec = workload.get("spec") or {}
    if "jobTemplate" in spec:
        spec = spec["jobTemplate"].get("spec") or {}
    return spec.get("template")


def version_free_pod_ids(input_dir):
    pod_ids = {}
    for manifest in sorted(pathlib.Path(input_dir).glob("*.yaml")):
        for workload in yaml.safe_load_all(manifest.read_text(encoding="utf-8")):
            template = pod_template(workload) if isinstance(workload, dict) else None
            if not template:
                continue
            labels = (template.get("metadata") or {}).get("labels") or {}
            if not any(key in labels for key in VERSION_LABELS):
                continue
            metadata = workload["metadata"]
            prefix = f"Pod.{metadata.get('namespace', 'default')}.{metadata['name']}"
            checkov_id = prefix + "".join(f".{key}-{value}" for key, value in labels.items())
            version_free = prefix + "".join(f".{key}-{value}" for key, value in labels.items() if key not in VERSION_LABELS)
            pod_ids[(f"/{manifest.name}", checkov_id)] = version_free
    return pod_ids


def baseline_key(pod_ids, file_path, resource):
    if (file_path, resource) in pod_ids:
        return pod_ids[(file_path, resource)]
    if resource.startswith("Pod.") and any(f".{key}-" in resource for key in VERSION_LABELS):
        raise ToolFailure(
            f"{file_path} {resource} carries a version label but matches no pod template rebuilt from {file_path}; "
            "Checkov's Pod id format changed, so fix version_free_pod_ids in .github/scripts/config-scan.sh"
        )
    return resource


def read_scan(scan_path, input_dir):
    try:
        with open(scan_path, encoding="utf-8") as scan_file:
            report = json.load(scan_file)
    except (OSError, ValueError) as parse_error:
        raise ToolFailure(f"checkov output {scan_path} is not JSON ({parse_error})") from parse_error
    if not isinstance(report, dict) or not isinstance(report.get("results"), dict):
        raise ToolFailure(f"checkov scanned no resources: {json.dumps(report)}")
    summary = report.get("summary", {})
    if summary.get("parsing_errors", 0) != 0 or summary.get("resource_count", 0) == 0:
        raise ToolFailure(f"checkov summary is not a clean scan: {json.dumps(summary)}")
    pod_ids = version_free_pod_ids(input_dir)
    return {
        (finding["file_path"], baseline_key(pod_ids, finding["file_path"], finding["resource"]), finding["check_id"])
        for finding in report["results"].get("failed_checks", [])
    }


def read_baseline(baseline_path):
    try:
        with open(baseline_path, encoding="utf-8") as baseline_file:
            baseline = json.load(baseline_file)
    except (OSError, ValueError) as read_error:
        raise ToolFailure(f"baseline {baseline_path} is unreadable ({read_error})") from read_error
    try:
        return {
            (file_entry["file"], finding["resource"], check_id)
            for file_entry in baseline["failed_checks"]
            for finding in file_entry["findings"]
            for check_id in finding["check_ids"]
        }
    except (KeyError, TypeError, AttributeError) as shape_error:
        raise ToolFailure(f"baseline {baseline_path} is not in Checkov's baseline shape (missing or wrong {shape_error})") from shape_error


def render_baseline(entries):
    by_file = {}
    for file_path, resource, check_id in entries:
        by_file.setdefault(file_path, {}).setdefault(resource, []).append(check_id)
    baseline = {
        "failed_checks": [
            {
                "file": file_path,
                "findings": [
                    {"check_ids": sorted(check_ids), "resource": resource}
                    for resource, check_ids in sorted(by_file[file_path].items())
                ],
            }
            for file_path in sorted(by_file)
        ]
    }
    return json.dumps(baseline, indent=2, sort_keys=True) + "\n"


def entry_line(state, entry):
    file_path, resource, check_id = entry
    return f"{state} {check_id} {file_path} {resource}"


def main(arguments):
    update_mode = arguments[0] == "true"
    inputs = [arguments[index:index + 5] for index in range(1, len(arguments), 5)]
    compared = []
    try:
        for input_name, scan_path, error_path, baseline_path, input_dir in inputs:
            try:
                scanned = read_scan(scan_path, input_dir)
            except ToolFailure:
                with open(error_path, encoding="utf-8", errors="replace") as error_file:
                    sys.stderr.write(error_file.read())
                raise
            compared.append((input_name, baseline_path, scanned, read_baseline(baseline_path)))
    except ToolFailure as failure:
        print(f"config-scan: {failure}", file=sys.stderr)
        return 2

    new_lines = [
        entry_line("NEW", entry)
        for _, _, scanned, baselined in compared
        for entry in sorted(scanned - baselined, key=lambda item: (item[2], item[0], item[1]))
    ]
    fixed_lines = [
        entry_line("FIXED", entry)
        for _, _, scanned, baselined in compared
        for entry in sorted(baselined - scanned, key=lambda item: (item[2], item[0], item[1]))
    ]
    new_advice = (
        "NEW: fix the finding, or record it where it lives: a '# checkov:skip=<id>:<reason>' comment "
        "inside the terraform resource, or the annotation 'checkov.io/skip1: <id>=<reason>' on the "
        "Kubernetes object"
    )

    if update_mode:
        if new_lines:
            print("\n".join(new_lines))
            print(new_advice)
            print("--update changed nothing: it only removes entries")
            return 1
        for input_name, baseline_path, scanned, _ in compared:
            rendered = render_baseline(scanned)
            with open(baseline_path, encoding="utf-8") as baseline_file:
                current_text = baseline_file.read()
            if rendered != current_text:
                with open(baseline_path, "w", encoding="utf-8") as baseline_file:
                    baseline_file.write(rendered)
            print(f"{input_name}: ok - {len(scanned)} baselined findings")
        return 0

    if not new_lines and not fixed_lines:
        for input_name, _, scanned, _ in compared:
            print(f"{input_name}: ok - {len(scanned)} baselined findings")
        return 0
    print("\n".join(new_lines + fixed_lines))
    if new_lines:
        print(new_advice)
    if fixed_lines:
        print("FIXED: run .github/scripts/config-scan.sh --update to drop the fixed entries from the baselines")
    if new_lines and fixed_lines:
        print(
            "MOVED: a renamed or moved resource shows as NEW plus FIXED; "
            "rename its entries in the baseline file by hand in the same change"
        )
    return 1


def run_guarded(arguments):
    try:
        return main(arguments)
    except Exception as failure:
        print(f"config-scan: {type(failure).__name__}: {failure}", file=sys.stderr)
        return 2


sys.exit(run_guarded(sys.argv[1:]))
PY
exit "$compare_status"
