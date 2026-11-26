#!/usr/bin/env bash
set -euo pipefail

repo=$(cd "$(dirname "$0")/../.." && pwd)
scan=$repo/.github/scripts/config-scan.sh
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
trap 'exit 130' INT

fail() {
  echo "FAIL $1: $2"
  [ ! -s "${3:-/dev/null}" ] || cat "$3"
  exit 1
}

copy_tree() {
  mkdir -p "$1/.github/config-scan"
  git -C "$repo" ls-files -z terraform deploy | tar -C "$repo" --null -T - -cf - | tar -C "$1" -xf -
  cp "$repo/.github/config-scan/terraform.baseline.json" "$repo/.github/config-scan/kubernetes.baseline.json" "$1/.github/config-scan/"
}

track() {
  git -C "$1" init -q
  git -C "$1" add -A
}

run_scan() {
  local out=$1
  shift
  status=0
  "$scan" "$@" >"$out" 2>&1 || status=$?
}

has_line() {
  grep -qxF -- "$2" "$1"
}

entries_of() {
  python3 -I - "$@" <<'PY'
import json
import sys

for baseline_path in sys.argv[1:]:
    with open(baseline_path, encoding="utf-8") as baseline_file:
        baseline = json.load(baseline_file)
    for file_entry in baseline["failed_checks"]:
        for finding in file_entry["findings"]:
            for check_id in finding["check_ids"]:
                print(f"{check_id} {file_entry['file']} {finding['resource']}")
PY
}

add_probe_pod() {
  cat >"$1/deploy/chart/templates/scan-probe.yaml" <<EOF
apiVersion: v1
kind: Pod
metadata:
  name: scan-probe
  namespace: danteplanner
$2
spec:
  hostNetwork: true
  containers:
    - name: probe
      image: busybox
EOF
}

add_version_probe() {
  cat >"$1/deploy/chart/templates/version-probe.yaml" <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: version-probe
  namespace: danteplanner
spec:
  selector:
    matchLabels:
      app: version-probe
  template:
    metadata:
      labels:
        app: version-probe
        helm.sh/chart: version-probe-$2
        app.kubernetes.io/version: "$2"
    spec:
      containers:
        - name: probe
          image: busybox
EOF
}

version_probe_key() {
  sed -n 's|^NEW CKV2_K8S_6 /oregon\.yaml \(Pod\.danteplanner\.version-probe.*\)$|\1|p' "$1"
}

name="the tree matches its baselines"
run_scan "$work/plain.out"
[ "$status" -eq 0 ] || fail "$name" "exit $status" "$work/plain.out"
[ "$(wc -l <"$work/plain.out")" -eq 2 ] && grep -qx 'terraform: ok - [0-9]* baselined findings' "$work/plain.out" \
  && grep -qx 'kubernetes: ok - [0-9]* baselined findings' "$work/plain.out" || fail "$name" "expected two ok lines" "$work/plain.out"
echo "PASS $name"

name="a new violation fails"
pod=$work/pod
copy_tree "$pod"
add_probe_pod "$pod" ""
track "$pod"
run_scan "$work/pod.out" "$pod"
[ "$status" -eq 1 ] || fail "$name" "exit $status, expected 1" "$work/pod.out"
for region in oregon seoul; do
  has_line "$work/pod.out" "NEW CKV_K8S_19 /$region.yaml Pod.danteplanner.scan-probe" \
    || fail "$name" "no NEW CKV_K8S_19 line for Pod.danteplanner.scan-probe in /$region.yaml" "$work/pod.out"
done
echo "PASS $name"

name="a new terraform violation fails"
bucket=$work/bucket
copy_tree "$bucket"
printf 'resource "aws_s3_bucket" "scan_probe" {\n  bucket = "scan-probe"\n}\n' >"$bucket/terraform/secrets/scan-probe.tf"
track "$bucket"
run_scan "$work/bucket.out" "$bucket"
[ "$status" -eq 1 ] || fail "$name" "exit $status, expected 1" "$work/bucket.out"
grep -qE '^NEW [A-Z0-9_]+ /secrets/scan-probe\.tf aws_s3_bucket\.scan_probe$' "$work/bucket.out" \
  || fail "$name" "no NEW line naming aws_s3_bucket.scan_probe" "$work/bucket.out"
echo "PASS $name"

name="a fixed finding must leave the baseline"
fixed=$work/fixed
copy_tree "$fixed"
awk '{ print } /^    spec:$/ { print "      automountServiceAccountToken: false" }' \
  "$fixed/deploy/chart/templates/redis-ratelimit.yaml" >"$work/redis-ratelimit.yaml"
mv "$work/redis-ratelimit.yaml" "$fixed/deploy/chart/templates/redis-ratelimit.yaml"
track "$fixed"
run_scan "$work/fixed.out" "$fixed"
[ "$status" -eq 1 ] || fail "$name" "exit $status, expected 1" "$work/fixed.out"
for region in oregon seoul; do
  has_line "$work/fixed.out" "FIXED CKV_K8S_38 /$region.yaml Deployment.danteplanner.redis-ratelimit" \
    || fail "$name" "no FIXED CKV_K8S_38 line for redis-ratelimit in /$region.yaml" "$work/fixed.out"
done
grep -q -- '--update' "$work/fixed.out" || fail "$name" "the output does not name --update" "$work/fixed.out"
! grep -q '^NEW ' "$work/fixed.out" || fail "$name" "turning off the token mount added a NEW finding" "$work/fixed.out"
echo "PASS $name"

name="a chart value that weakens its security context is a new finding"
weakened=$work/weakened
copy_tree "$weakened"
awk '{ print } /^      valuesObject:$/ {
  print "        containerSecurityContext:"; print "          allowPrivilegeEscalation: true"
}' "$weakened/deploy/chart/templates/kube-state-metrics.yaml" >"$work/weakened.yaml"
mv "$work/weakened.yaml" "$weakened/deploy/chart/templates/kube-state-metrics.yaml"
grep -qx '          allowPrivilegeEscalation: true' "$weakened/deploy/chart/templates/kube-state-metrics.yaml" || fail "$name" "the values rewrite matched nothing"
track "$weakened"
run_scan "$work/weakened.out" "$weakened"
[ "$status" -eq 1 ] || fail "$name" "exit $status, expected 1" "$work/weakened.out"
has_line "$work/weakened.out" "NEW CKV_K8S_20 /chart-kube-state-metrics.yaml Deployment.danteplanner.kube-state-metrics" \
  || fail "$name" "no NEW CKV_K8S_20 line for the chart's Deployment in /chart-kube-state-metrics.yaml" "$work/weakened.out"
! grep -q '^NEW .* /\(oregon\|seoul\)\.yaml ' "$work/weakened.out" || fail "$name" "a chart value changed a finding of a region" "$work/weakened.out"
echo "PASS $name"

name="a chart version bump leaves the baseline unchanged"
pod_key="Pod.danteplanner.kube-state-metrics.app.kubernetes.io/managed-by-Helm.app.kubernetes.io/component-metrics.app.kubernetes.io/part-of-kube-state-metrics.app.kubernetes.io/name-kube-state-metrics.app.kubernetes.io/instance-kube-state-metrics"
entries_of "$repo/.github/config-scan/kubernetes.baseline.json" >"$work/committed.txt"
has_line "$work/committed.txt" "CKV2_K8S_6 /chart-kube-state-metrics.yaml $pod_key" \
  || fail "$name" "the committed baseline holds no version-free Pod key for the chart" "$work/committed.txt"
! grep -qE 'helm\.sh/chart-|app\.kubernetes\.io/version-' "$work/committed.txt" \
  || fail "$name" "the committed baseline still holds a version label part" "$work/committed.txt"
bumped=$work/bumped
copy_tree "$bumped"
sed -i 's/^    targetRevision: 8\.3\.1$/    targetRevision: 8.3.0/' "$bumped/deploy/chart/templates/kube-state-metrics.yaml"
grep -qx '    targetRevision: 8.3.0' "$bumped/deploy/chart/templates/kube-state-metrics.yaml" || fail "$name" "the targetRevision rewrite matched nothing"
track "$bumped"
run_scan "$work/bumped.out" "$bumped"
[ "$status" -eq 0 ] || fail "$name" "chart 8.3.0 against the committed baseline exited $status, expected 0" "$work/bumped.out"
! grep -qE '^(NEW|FIXED) ' "$work/bumped.out" || fail "$name" "a version bump printed NEW or FIXED lines" "$work/bumped.out"
echo "PASS $name"

name="a baseline entry that carries a version label is stale: FIXED plus NEW"
versioned_key="${pod_key/kube-state-metrics.app.kubernetes.io\/managed-by/kube-state-metrics.helm.sh/chart-kube-state-metrics-8.3.1.app.kubernetes.io/managed-by}.app.kubernetes.io/version-2.19.1"
versioned=$work/versioned
copy_tree "$versioned"
sed -i "s|\"$pod_key\"|\"$versioned_key\"|" "$versioned/.github/config-scan/kubernetes.baseline.json"
grep -qF "$versioned_key" "$versioned/.github/config-scan/kubernetes.baseline.json" || fail "$name" "the baseline rewrite matched nothing"
track "$versioned"
run_scan "$work/versioned.out" "$versioned"
[ "$status" -eq 1 ] || fail "$name" "exit $status, expected 1" "$work/versioned.out"
has_line "$work/versioned.out" "FIXED CKV2_K8S_6 /chart-kube-state-metrics.yaml $versioned_key" \
  && has_line "$work/versioned.out" "NEW CKV2_K8S_6 /chart-kube-state-metrics.yaml $pod_key" \
  || fail "$name" "no FIXED line for the versioned key plus NEW line for the version-free key" "$work/versioned.out"
echo "PASS $name"

name="a pre-release version label yields the same key as a release"
for version in 8.3.1 2.20.0-rc.alpha; do
  probed=$work/probe-$version
  copy_tree "$probed"
  add_version_probe "$probed" "$version"
  track "$probed"
  run_scan "$work/probe-$version.out" "$probed"
  [ "$status" -eq 1 ] || fail "$name" "version $version: exit $status, expected 1" "$work/probe-$version.out"
done
[ "$(version_probe_key "$work/probe-8.3.1.out")" = "Pod.danteplanner.version-probe.app-version-probe" ] \
  || fail "$name" "version 8.3.1 did not yield the key Pod.danteplanner.version-probe.app-version-probe" "$work/probe-8.3.1.out"
[ "$(version_probe_key "$work/probe-2.20.0-rc.alpha.out")" = "Pod.danteplanner.version-probe.app-version-probe" ] \
  || fail "$name" "version 2.20.0-rc.alpha yielded another key than 8.3.1" "$work/probe-2.20.0-rc.alpha.out"
echo "PASS $name"

name="a label key that starts with a digit after the version label stays in the key"
digit=$work/probe-digit
copy_tree "$digit"
awk '{ print } /^      valuesObject:$/ { print "        podLabels:"; print "          1password.com/item: probe" }' \
  "$digit/deploy/chart/templates/kube-state-metrics.yaml" >"$work/digit.yaml"
mv "$work/digit.yaml" "$digit/deploy/chart/templates/kube-state-metrics.yaml"
grep -qx '          1password.com/item: probe' "$digit/deploy/chart/templates/kube-state-metrics.yaml" || fail "$name" "the podLabels rewrite matched nothing"
track "$digit"
run_scan "$work/probe-digit.out" "$digit"
[ "$status" -eq 1 ] || fail "$name" "exit $status, expected 1" "$work/probe-digit.out"
has_line "$work/probe-digit.out" "NEW CKV2_K8S_6 /chart-kube-state-metrics.yaml $pod_key.1password.com/item-probe" \
  && has_line "$work/probe-digit.out" "FIXED CKV2_K8S_6 /chart-kube-state-metrics.yaml $pod_key" \
  || fail "$name" "the chart Pod key lost the 1password.com/item label that follows its version label, or kept a version part" "$work/probe-digit.out"
echo "PASS $name"

name="a Pod key that differs in another label part is still new"
relabeled=$work/relabeled
copy_tree "$relabeled"
sed -i 's|app.kubernetes.io/component-metrics.app.kubernetes.io/part-of|app.kubernetes.io/component-other.app.kubernetes.io/part-of|' \
  "$relabeled/.github/config-scan/kubernetes.baseline.json"
grep -q 'component-other' "$relabeled/.github/config-scan/kubernetes.baseline.json" || fail "$name" "the baseline rewrite matched nothing"
track "$relabeled"
run_scan "$work/relabeled.out" "$relabeled"
[ "$status" -eq 1 ] || fail "$name" "exit $status, expected 1" "$work/relabeled.out"
has_line "$work/relabeled.out" "NEW CKV2_K8S_6 /chart-kube-state-metrics.yaml $pod_key" \
  && has_line "$work/relabeled.out" "FIXED CKV2_K8S_6 /chart-kube-state-metrics.yaml ${pod_key/component-metrics/component-other}" \
  || fail "$name" "no NEW plus FIXED pair for the relabeled Pod" "$work/relabeled.out"
echo "PASS $name"

name="update shrinks and never grows"
entries_of "$fixed/.github/config-scan/kubernetes.baseline.json" | sort >"$work/before.txt"
run_scan "$work/update-fixed.out" --update "$fixed"
[ "$status" -eq 0 ] || fail "$name" "--update on the fixed copy exited $status" "$work/update-fixed.out"
entries_of "$fixed/.github/config-scan/kubernetes.baseline.json" | sort >"$work/after.txt"
printf '%s\n' "CKV_K8S_38 /oregon.yaml Deployment.danteplanner.redis-ratelimit" \
  "CKV_K8S_38 /seoul.yaml Deployment.danteplanner.redis-ratelimit" >"$work/expected-removed.txt"
comm -23 "$work/before.txt" "$work/after.txt" >"$work/removed.txt"
cmp -s "$work/removed.txt" "$work/expected-removed.txt" || fail "$name" "--update removed other entries than the fixed ones" "$work/removed.txt"
[ -z "$(comm -13 "$work/before.txt" "$work/after.txt")" ] || fail "$name" "--update added entries"
cmp -s "$repo/.github/config-scan/terraform.baseline.json" "$fixed/.github/config-scan/terraform.baseline.json" \
  || fail "$name" "--update changed the terraform baseline although terraform did not change"
run_scan "$work/after-update.out" "$fixed"
[ "$status" -eq 0 ] || fail "$name" "the plain run after --update exited $status" "$work/after-update.out"
run_scan "$work/update-pod.out" --update "$pod"
[ "$status" -eq 1 ] || fail "$name" "--update on the new-violation copy exited $status, expected 1" "$work/update-pod.out"
has_line "$work/update-pod.out" "NEW CKV_K8S_19 /oregon.yaml Pod.danteplanner.scan-probe" \
  || fail "$name" "--update did not print the NEW lines" "$work/update-pod.out"
for input in terraform kubernetes; do
  cmp -s "$repo/.github/config-scan/$input.baseline.json" "$pod/.github/config-scan/$input.baseline.json" \
    || fail "$name" "--update on the new-violation copy changed $input.baseline.json"
done
echo "PASS $name"

name="local state is not scanned"
local_state=$work/local-state
copy_tree "$local_state"
track "$local_state"
mkdir -p "$local_state/terraform/scratch-stack"
printf 'resource "aws_s3_bucket" "scratch_probe" {\n  bucket = "scratch-probe"\n}\n' >"$local_state/terraform/scratch-stack/main.tf"
printf 'resource "aws_s3_bucket" "untracked_probe" {\n  bucket = "untracked-probe"\n}\n' >"$local_state/terraform/secrets/untracked-probe.tf"
run_scan "$work/local-state.out" "$local_state"
[ "$status" -eq 0 ] || fail "$name" "exit $status" "$work/local-state.out"
cmp -s "$work/plain.out" "$work/local-state.out" || fail "$name" "the counts differ from the unchanged tree" "$work/local-state.out"
git -C "$local_state" add -A
run_scan "$work/local-state-added.out" "$local_state"
[ "$status" -eq 1 ] || fail "$name" "with the probes added to the index the scan exited $status, expected 1" "$work/local-state-added.out"
grep -qE '^NEW [A-Z0-9_]+ /scratch-stack/main\.tf aws_s3_bucket\.scratch_probe$' "$work/local-state-added.out" \
  && grep -qE '^NEW [A-Z0-9_]+ /secrets/untracked-probe\.tf aws_s3_bucket\.untracked_probe$' "$work/local-state-added.out" \
  || fail "$name" "the added probes are not both reported as NEW" "$work/local-state-added.out"
echo "PASS $name"

name="a new image tag and a no-op --update leave the baselines byte-identical"
retagged=$work/retagged
copy_tree "$retagged"
for region in oregon seoul; do
  sed -i 's/^\(  tag: \).*/\1"0123456789abcdef0123456789abcdef01234567"/' "$retagged/deploy/chart/values-$region.yaml"
  has_line "$retagged/deploy/chart/values-$region.yaml" '  tag: "0123456789abcdef0123456789abcdef01234567"' \
    || fail "$name" "the tag rewrite matched nothing in values-$region.yaml"
done
track "$retagged"
run_scan "$work/retagged.out" --update "$retagged"
[ "$status" -eq 0 ] || fail "$name" "exit $status" "$work/retagged.out"
cmp -s "$work/plain.out" "$work/retagged.out" || fail "$name" "the counts differ from the unchanged tree" "$work/retagged.out"
for input in terraform kubernetes; do
  cmp -s "$repo/.github/config-scan/$input.baseline.json" "$retagged/.github/config-scan/$input.baseline.json" \
    || fail "$name" "$input.baseline.json changed"
done
echo "PASS $name"

name="a broken tool is not a pass"
status=0
CONFIG_SCAN_CHECKOV_IMAGE=bridgecrew/checkov:0.0.0-config-scan-missing "$scan" >"$work/broken.out" 2>&1 || status=$?
[ "$status" -eq 2 ] || fail "$name" "exit $status, expected 2" "$work/broken.out"
echo "PASS $name"

name="an accepted finding is recorded where it lives"
skipped=$work/skipped
copy_tree "$skipped"
add_probe_pod "$skipped" "  annotations:
    checkov.io/skip1: CKV_K8S_19=scan probe needs the host network"
track "$skipped"
run_scan "$work/skipped.out" "$skipped"
grep -q '^NEW .* Pod\.danteplanner\.scan-probe$' "$work/skipped.out" || fail "$name" "the scan-probe Pod was not scanned" "$work/skipped.out"
! grep -q '^NEW CKV_K8S_19 .* Pod\.danteplanner\.scan-probe$' "$work/skipped.out" \
  || fail "$name" "CKV_K8S_19 is still reported for the annotated Pod" "$work/skipped.out"
echo "PASS $name"

name="the lane runs when either input changes"
awk '/^  scan-config:$/ { inside = 1; next } inside && /^  [^ ]/ { inside = 0 } inside' \
  "$repo/.github/workflows/pr-gate.yml" >"$work/job.yml"
grep -qx '    needs: changes' "$work/job.yml" \
  && grep -qxF "    if: needs.changes.outputs.deploy == 'true' || needs.changes.outputs.terraform == 'true'" "$work/job.yml" \
  || fail "$name" "pr-gate.yml has no scan-config job needing changes with the deploy-or-terraform condition"
for lister in deploy-test-inputs.sh terraform-inputs.sh; do
  "$repo/.github/scripts/$lister" --list-inputs >"$work/inputs.txt"
  for input in .github/scripts/config-scan.sh .github/scripts/render-charts.sh .github/scripts/images.sh .github/config-scan; do
    has_line "$work/inputs.txt" "$input" || fail "$name" "$lister --list-inputs does not list $input"
  done
done
echo "PASS $name"

name="a terraform file that does not parse is not a pass"
unparsable=$work/unparsable
copy_tree "$unparsable"
printf 'resource "aws_s3_bucket" "broken_probe" {\n  bucket =\n' >"$unparsable/terraform/secrets/broken-probe.tf"
track "$unparsable"
run_scan "$work/unparsable.out" "$unparsable"
[ "$status" -eq 2 ] || fail "$name" "exit $status, expected 2" "$work/unparsable.out"
echo "PASS $name"

name="a region that does not render is not a pass"
unrendered=$work/unrendered
copy_tree "$unrendered"
printf '{{ if eq .Values.region "seoul" }}{{ fail "render probe" }}{{ end }}\n' >"$unrendered/deploy/chart/templates/render-probe.yaml"
track "$unrendered"
run_scan "$work/unrendered.out" "$unrendered"
[ "$status" -eq 2 ] || fail "$name" "exit $status, expected 2" "$work/unrendered.out"
grep -qF "values-seoul.yaml failed" "$work/unrendered.out" && grep -qF "render probe" "$work/unrendered.out" \
  || fail "$name" "the output does not name seoul and the render failure" "$work/unrendered.out"
echo "PASS $name"

name="an unknown region is not a pass"
third=$work/third-region
copy_tree "$third"
sed 's/^region: seoul$/region: probe/' "$third/deploy/chart/values-seoul.yaml" >"$third/deploy/chart/values-probe.yaml"
has_line "$third/deploy/chart/values-probe.yaml" "region: probe" || fail "$name" "the region rewrite matched nothing"
track "$third"
run_scan "$work/third-region.out" "$third"
[ "$status" -eq 2 ] || fail "$name" "exit $status, expected 2" "$work/third-region.out"
grep -qF "values-probe.yaml failed" "$work/third-region.out" \
  && grep -qF "don't meet the specifications of the schema" "$work/third-region.out" \
  || fail "$name" "the output does not name values-probe.yaml and the schema" "$work/third-region.out"
echo "PASS $name"

name="a wrong argument or a malformed baseline is exit 2"
run_scan "$work/no-repo.out" "$work/does-not-exist"
[ "$status" -eq 2 ] || fail "$name" "a missing repo-dir exited $status, expected 2" "$work/no-repo.out"
malformed=$work/malformed
copy_tree "$malformed"
printf '{}\n' >"$malformed/.github/config-scan/kubernetes.baseline.json"
track "$malformed"
run_scan "$work/malformed.out" "$malformed"
[ "$status" -eq 2 ] || fail "$name" "a {} baseline exited $status, expected 2" "$work/malformed.out"
grep -qF "$malformed/.github/config-scan/kubernetes.baseline.json" "$work/malformed.out" \
  || fail "$name" "the output does not name the malformed baseline" "$work/malformed.out"
echo "PASS $name"

name="a renamed resource has a stated way forward"
renamed=$work/renamed
copy_tree "$renamed"
awk 'previous_kind == "kind: Deployment" && /^  name: redis-ratelimit$/ { $0 = "  name: redis-ratelimit-renamed" }
  /^kind: / { previous_kind = $0 } { print }' "$renamed/deploy/chart/templates/redis-ratelimit.yaml" >"$work/renamed.yaml"
mv "$work/renamed.yaml" "$renamed/deploy/chart/templates/redis-ratelimit.yaml"
grep -qx '  name: redis-ratelimit-renamed' "$renamed/deploy/chart/templates/redis-ratelimit.yaml" || fail "$name" "the rename matched nothing"
track "$renamed"
run_scan "$work/renamed.out" "$renamed"
[ "$status" -eq 1 ] || fail "$name" "exit $status, expected 1" "$work/renamed.out"
grep -q '^NEW .* Deployment\.danteplanner\.redis-ratelimit-renamed$' "$work/renamed.out" \
  && grep -q '^FIXED .* Deployment\.danteplanner\.redis-ratelimit$' "$work/renamed.out" \
  || fail "$name" "no NEW and FIXED lines for the renamed Deployment" "$work/renamed.out"
has_line "$work/renamed.out" "MOVED: a renamed or moved resource shows as NEW plus FIXED; rename its entries in the baseline file by hand in the same change" \
  || fail "$name" "no MOVED line" "$work/renamed.out"
echo "PASS $name"
