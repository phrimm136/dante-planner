#!/usr/bin/env bash
set -uo pipefail
shopt -s nullglob

repo_root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$repo_root" || exit 2

suites=()
for suite_dir in .claude/skills/*/tests/; do suites+=("${suite_dir%/}"); done
for suite_file in .githooks/tests/*.sh .claude/hooks/tests/*.sh; do suites+=("$suite_file"); done

if [ "${#suites[@]}" -eq 0 ]; then
  echo "tooling-tests: no suites found under .claude/skills/*/tests/, .githooks/tests/*.sh or .claude/hooks/tests/*.sh" >&2
  exit 1
fi
for checker_suite in scripts/ops/tests scripts/ops/provision/tests/*.sh .github/scripts/no-real-time-in-tooling-tests.py \
    .github/scripts/tests; do
  if [ -e "$checker_suite" ]; then suites+=("$checker_suite"); fi
done

out_file=$(mktemp)
guard_dir=$(mktemp -d)
trap 'rm -rf "$out_file" "$guard_dir"' EXIT
sleep_calls="$guard_dir/sleep-calls"
unset PYTEST_CURRENT_TEST
printf '%s\n' '#!/bin/sh' "printf '%s%s\\n' \"\$*\" \"\${PYTEST_CURRENT_TEST:+ in \$PYTEST_CURRENT_TEST}\" >>'$sleep_calls'" \
  'printf "a tooling test ran the real sleep: %s\n" "$*" >&2' 'exit 97' >"$guard_dir/sleep"
chmod +x "$guard_dir/sleep"
export TOOLING_SLEEP_GUARD_DIR="$guard_dir"
export PATH="$guard_dir:$PATH"
result_lines=()
failed_suites=()
for suite in "${suites[@]}"; do
  echo "== $suite"
  : >"$sleep_calls"
  if [ -d "$suite" ]; then
    python3 -m pytest -q -p no:cacheprovider "$suite" >"$out_file" 2>&1
  elif [[ "$suite" == *.py ]]; then
    python3 "$suite" >"$out_file" 2>&1
  else
    bash "$suite" >"$out_file" 2>&1
  fi
  suite_rc=$?
  cat "$out_file"
  suite_summary=$(awk 'NF { last = $0 } END { print last }' "$out_file")
  if [ -s "$sleep_calls" ]; then
    suite_rc=97
    suite_summary="a tooling test ran the real sleep: $(head -n 1 "$sleep_calls"); fake sleep earlier on PATH"
  fi
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
