#!/usr/bin/env bash
set -uo pipefail
shopt -s nullglob

repo_root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$repo_root" || exit 2

suites=()
for suite_dir in .claude/skills/*/tests/; do suites+=("${suite_dir%/}"); done
for suite_file in .githooks/tests/*.sh; do suites+=("$suite_file"); done

if [ "${#suites[@]}" -eq 0 ]; then
  echo "tooling-tests: no suites found under .claude/skills/*/tests/ or .githooks/tests/*.sh" >&2
  exit 1
fi

out_file=$(mktemp)
trap 'rm -f "$out_file"' EXIT
result_lines=()
failed_suites=()
for suite in "${suites[@]}"; do
  echo "== $suite"
  if [ -d "$suite" ]; then
    python3 -m pytest -q -p no:cacheprovider "$suite" >"$out_file" 2>&1
  else
    bash "$suite" >"$out_file" 2>&1
  fi
  suite_rc=$?
  cat "$out_file"
  suite_summary=$(awk 'NF { last = $0 } END { print last }' "$out_file")
  if [ "$suite_rc" -eq 0 ]; then
    result_lines+=("PASS $suite  $suite_summary")
  else
    result_lines+=("FAIL $suite  (exit $suite_rc) $suite_summary")
    failed_suites+=("$suite")
  fi
  echo
done

printf '%s\n' "${result_lines[@]}"
if [ "${#failed_suites[@]}" -eq 0 ]; then
  echo "tooling-tests: ${#suites[@]} of ${#suites[@]} suites passed"
  exit 0
fi
failed_text=$(printf '%s, ' "${failed_suites[@]}")
echo "tooling-tests: ${#failed_suites[@]} of ${#suites[@]} suites failed: ${failed_text%, }"
exit 1
