#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<EOF
usage: $0 <clean|all|check|PATCH> [--seeds K] [--out FILE]
  PATCH  a planted bug: a file name under backend/src/test/resources/consistency/planted/ without .patch
  check  git apply --check every planted bug against the checkout
  all    clean, then every planted bug
  --seeds K   seeds per variant (default 20)
  --out FILE  markdown table the row is appended to (default backend/build/consistency/planted-bugs.md)
Exits 0 when ConsistencyRunIT passed on clean and every planted bug run was detected by the checker.
EOF
  exit 2
}

script_dir=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
repo=$(git -C "$script_dir" rev-parse --show-toplevel)
planted="$repo/backend/src/test/resources/consistency/planted"
integration="$repo/backend/src/test/java/org/danteplanner/backend/integration"
results="$repo/backend/build/test-results/test"
package=org.danteplanner.backend.integration
run_class=ConsistencyRunIT
harness=CausalHarnessSupport

[ $# -ge 1 ] || usage
variant=$1
shift
seeds=20
out="$repo/backend/build/consistency/planted-bugs.md"
while [ $# -gt 0 ]; do
  case $1 in
    --seeds)
      [ $# -ge 2 ] || usage
      seeds=$2
      shift 2
      ;;
    --out)
      [ $# -ge 2 ] || usage
      out=$2
      shift 2
      ;;
    *) usage ;;
  esac
done
[[ $seeds =~ ^[1-9][0-9]*$ ]] || usage

applied_patch=""
applied_file=""
gradle_pid=""

patch_names() {
  local patch
  for patch in "$planted"/*.patch; do
    [ -e "$patch" ] || continue
    basename "$patch" .patch
  done
}

patch_target() {
  local targets
  targets=$(git -C "$repo" apply --numstat "$1" | cut -f3)
  if [ "$(printf '%s\n' "$targets" | wc -l)" -ne 1 ]; then
    echo "$1 must touch exactly one file, touches: $targets" >&2
    return 1
  fi
  printf '%s\n' "$targets"
}

check_patches() {
  local name
  for name in $(patch_names); do
    if ! git -C "$repo" apply --check "$planted/$name.patch"; then
      echo "planted bug does not apply to HEAD: $name" >&2
      return 1
    fi
    echo "applies: $name"
  done
}

baseline_classes() {
  python3 - "$1" "$harness" "$run_class" <<'PY'
import pathlib
import re
import sys

directory, harness, run_class = sys.argv[1:]
declaration = re.compile(r"^(?:public )?(abstract )?(?:final )?class (\w+) extends (\w+)", re.M)
parents, abstract = {}, set()
for source in sorted(pathlib.Path(directory).glob("*.java")):
    for match in declaration.finditer(source.read_text(encoding="utf-8")):
        parents[match[2]] = match[3]
        if match[1]:
            abstract.add(match[2])
inherits = {harness}
changed = True
while changed:
    changed = False
    for cls, parent in parents.items():
        if cls not in inherits and parent in inherits:
            inherits.add(cls)
            changed = True
found = sorted(cls for cls in inherits - {harness, run_class} if cls not in abstract)
if not found:
    sys.exit(f"no concrete class under {directory} extends {harness}")
print("\n".join(found))
PY
}

stop_gradle() {
  if [ -n "$gradle_pid" ] && kill -0 "$gradle_pid" 2>/dev/null; then
    echo "stopping Gradle (pid $gradle_pid)" >&2
    kill -TERM "$gradle_pid" 2>/dev/null || true
    wait "$gradle_pid" 2>/dev/null || true
  fi
  gradle_pid=""
}

restore() {
  [ -n "$applied_patch" ] || return 0
  local patch=$applied_patch file=$applied_file
  applied_patch=""
  applied_file=""
  if git -C "$repo" apply -R --check "$patch" 2>/dev/null; then
    if ! git -C "$repo" apply -R "$patch"; then
      echo "git apply -R failed for $patch" >&2
    fi
  else
    echo "$patch is not applied; nothing to reverse"
  fi
  if ! git -C "$repo" diff --quiet HEAD -- "$file"; then
    echo "the tree was not restored: $file still differs from HEAD" >&2
    return 1
  fi
  echo "restored $file"
}

on_exit() {
  local rc=$?
  stop_gradle
  restore || rc=1
  exit "$rc"
}
trap on_exit EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

apply_patch() {
  local patch="$planted/$1.patch" file
  if [ ! -f "$patch" ]; then
    echo "unknown variant: $1 (variants: clean all check $(patch_names | tr '\n' ' '))" >&2
    return 1
  fi
  file=$(patch_target "$patch") || return 1
  if ! git -C "$repo" apply --check "$patch"; then
    echo "planted bug does not apply to HEAD: $1" >&2
    return 1
  fi
  applied_file=$file
  applied_patch=$patch
  git -C "$repo" apply "$patch" || return 1
  echo "applied $1 to $file"
}

require_targets_at_head() {
  local name target dirty=""
  for name in $(patch_names); do
    target=$(patch_target "$planted/$name.patch") || return 1
    if ! git -C "$repo" diff --quiet HEAD -- "$target"; then
      case " $dirty " in
        *" $target "*) ;;
        *) dirty="$dirty $target" ;;
      esac
    fi
  done
  if [ -n "$dirty" ]; then
    echo "planted-bug target files differ from HEAD:$dirty" >&2
    echo "restore them (git checkout -- <file>) before measuring any variant" >&2
    return 1
  fi
}

judge() {
  python3 - "$results" "$package" "$run_class" "$@" <<'PY'
import os
import re
import sys
import xml.etree.ElementTree as ET

results, package, run_class, variant, *baseline = sys.argv[1:]
GUARD = re.compile(r"operations have an unknown outcome|has no Found or NotFound read of a key")


def suite(cls):
    path = os.path.join(results, f"TEST-{package}.{cls}.xml")
    return ET.parse(path).getroot() if os.path.exists(path) else None


def failures(root):
    return [child for case in root.iter("testcase") for child in case if child.tag in ("failure", "error")]


failed = []
for cls in baseline:
    root = suite(cls)
    if root is None:
        failed.append(f"{cls} (no result)")
    elif failures(root):
        failed.append(cls)

root = suite(run_class)
first, by_rule, passed = "-", "", "no"
if root is None:
    detected = "no result"
else:
    output = "".join(node.text or "" for node in root.iter("system-out"))
    seeds = [(int(m[1]), int(m[2])) for m in re.finditer(r"^consistency seed (\d+): (\d+) violations", output, re.M)]
    messages = [node.get("message") or node.text or "" for node in failures(root)]
    guard = any(GUARD.search((node.get("message") or "") + (node.text or "")) for node in failures(root))
    detecting = [seed for seed, violations in seeds if violations > 0]
    if detecting:
        detected, first = "yes", str(detecting[0])
        rules = re.findall(rf"seed {first} \(.*?\): Violation\[rule=([\w-]+)", messages[0] if messages else "")
        counts = {rule: rules.count(rule) for rule in sorted(set(rules))}
        by_rule = ", ".join(f"{rule} {count}" for rule, count in counts.items()) or "not in failure message"
        if guard:
            by_rule += "; guard failed"
    elif messages:
        detected = "failed run (guard failed)" if guard else "failed run"
    elif not seeds:
        detected = "no result"
    else:
        detected = "no"
        by_rule = f"0 violations in {len(seeds)} seeds"
    if variant == "clean":
        passed = "yes" if not messages and seeds and not detecting else "no"
    else:
        passed = "yes" if detecting else "no"

print("\t".join([", ".join(failed) or "none", detected, first, passed, by_rule]))
PY
}

append_row() {
  mkdir -p "$(dirname "$out")"
  if [ ! -s "$out" ]; then
    printf '%s\n' \
      "| variant | baseline ITs failed | checker detected | first detecting seed | violations by rule | wall time (s) |" \
      "|---|---|---|---|---|---|" > "$out"
  fi
  printf '| %s | %s | %s | %s | %s | %s |\n' "$@" >> "$out"
  printf '| %s | %s | %s | %s | %s | %s |\n' "$@"
}

abort_variant() {
  echo "variant $1 aborted: $2" >&2
  restore || true
}

run_variant() {
  local name=$1 stop=true started elapsed gradle_rc verdict discovered baseline_failed detected first passed by_rule
  local -a baseline gradle_args
  if ! require_targets_at_head; then
    echo "variant $name refused: the checkout does not match HEAD at a planted-bug target" >&2
    return 1
  fi
  if [ "$name" = clean ]; then
    stop=false
  elif ! apply_patch "$name"; then
    abort_variant "$name" "the planted bug was not applied"
    return 1
  fi

  if ! discovered=$(baseline_classes "$integration"); then
    abort_variant "$name" "baseline class discovery failed"
    return 1
  fi
  mapfile -t baseline <<< "$discovered"
  echo "baseline classes (${#baseline[@]}): ${baseline[*]}"

  gradle_args=(-p "$repo/backend" test --rerun --tests "$package.$run_class")
  local cls
  for cls in "${baseline[@]}"; do
    gradle_args+=(--tests "$package.$cls")
  done
  gradle_args+=("-Dconsistency.seedCount=$seeds" "-Dconsistency.stopAtFirstViolation=$stop")

  if ! rm -f -- "$repo"/backend/build/test-results/test/TEST-*.xml; then
    abort_variant "$name" "stale test results could not be deleted"
    return 1
  fi
  echo "variant $name: gradlew ${gradle_args[*]}"
  started=$SECONDS
  "$repo/backend/gradlew" "${gradle_args[@]}" &
  gradle_pid=$!
  set +e
  wait "$gradle_pid"
  gradle_rc=$?
  set -e
  gradle_pid=""
  elapsed=$((SECONDS - started))
  echo "variant $name: Gradle exited $gradle_rc after ${elapsed}s"

  if ! restore; then
    echo "variant $name aborted: the planted bug was not reverted" >&2
    exit 1
  fi

  if ! verdict=$(judge "$name" "${baseline[@]}"); then
    echo "variant $name aborted: judging the test results failed" >&2
    return 1
  fi
  IFS=$'\t' read -r baseline_failed detected first passed by_rule <<< "$verdict"
  if [ -z "$baseline_failed" ] || [ -z "$detected" ] || [ -z "$first" ] \
      || { [ "$passed" != yes ] && [ "$passed" != no ]; }; then
    echo "variant $name aborted: malformed verdict: $verdict" >&2
    return 1
  fi
  if ! append_row "$name" "$baseline_failed" "$detected" "$first" "$by_rule" "$elapsed"; then
    echo "variant $name aborted: the row could not be written to $out" >&2
    return 1
  fi

  [ "$passed" = yes ]
}

case $variant in
  check)
    check_patches
    ;;
  all)
    check_patches
    status=0
    for name in clean $(patch_names); do
      run_variant "$name" || status=1
    done
    exit "$status"
    ;;
  *)
    run_variant "$variant" || exit 1
    ;;
esac
