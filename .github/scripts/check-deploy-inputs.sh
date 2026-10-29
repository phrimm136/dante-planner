#!/bin/sh
set -euf

dockerfile=${1:-backend/Dockerfile}
frontend_dir=${2:-frontend}
backend_dir=${3:-backend}
inputs=$("$(dirname "$0")/deploy-inputs-changed.sh" --list-inputs)

sources=$(awk '
  /^[[:space:]]*#/ { next }
  { line = line $0 }
  /\\[[:space:]]*$/ { sub(/\\[[:space:]]*$/, " ", line); next }
  {
    n = split(line, t, /[[:space:]]+/); line = ""
    i = 1; while (i <= n && t[i] == "") i++
    op = toupper(t[i])
    if (op != "COPY" && op != "ADD") next
    srcs = ""; count = 0
    for (j = i + 1; j <= n; j++) {
      if (t[j] == "") continue
      if (t[j] ~ /^--from=/) next
      if (t[j] ~ /^--/) continue
      if (t[j] ~ /^\[/ || t[j] ~ /^<</) { print "!" NR; next }
      args[++count] = t[j]
    }
    for (k = 1; k < count; k++) if (args[k] !~ /^https?:\/\//) print args[k]
  }
' "$dockerfile")

if [ -z "$sources" ]; then
  echo "no repo-relative COPY/ADD sources found in $dockerfile: fix the parser in $0" >&2
  exit 1
fi

fail=0
for src in $sources; do
  case "$src" in
    !*)
      echo "$dockerfile line ${src#!}: COPY/ADD form this check cannot read; use the plain 'COPY <src>... <dest>' form" >&2
      fail=1
      continue
      ;;
  esac
  path=${src#./}
  covered=""
  for input in $inputs; do
    case "$path" in
      "$input" | "$input"/*) covered=$input ;;
    esac
  done
  if [ -n "$covered" ]; then
    echo "covered: $path (by $covered)"
  else
    echo "uncovered image input: $path is copied by $dockerfile but missing from the image input list in .github/scripts/deploy-inputs-changed.sh; add it there" >&2
    fail=1
  fi
done

relative_refs() {
  find "$@" -type f -exec awk '
    {
      s = $0
      while (match(s, /["'\''`]\.\.\/[^"'\''`]*/)) {
        print FILENAME ":" FNR " " substr(s, RSTART + 1, RLENGTH - 1)
        s = substr(s, RSTART + RLENGTH)
      }
    }
  ' {} +
}

pinned_by() {
  case "$1" in
    static | static/*) echo static.lock ;;
    frontend/node_modules/*) echo frontend/yarn.lock ;;
  esac
}

check_refs() {
  lane=$1 list_script=$2 refs=$3
  lane_inputs=$("$(dirname "$0")/$list_script" --list-inputs)
  if [ -z "$refs" ]; then
    echo "no ../ references found for the $lane lane: fix the scan in $0" >&2
    fail=1
    return
  fi
  while IFS=' ' read -r where ref; do
    path=${ref#../}
    case "$path" in
      ../*)
        echo "$where: $ref points outside the repository; $lane inputs must live in it" >&2
        fail=1
        continue
        ;;
    esac
    pin=$(pinned_by "$path")
    covered=""
    for input in $lane_inputs; do
      case "$path" in
        "$input" | "$input"/*) covered=$input ;;
      esac
      if [ -n "$pin" ] && [ "$input" = "$pin" ]; then
        covered=$pin
      fi
    done
    if [ -n "$covered" ]; then
      echo "covered: $where $path (by $covered)"
    else
      echo "uncovered $lane input: $where references $path but it is missing from .github/scripts/$list_script; add it there" >&2
      fail=1
    fi
  done <<REFS
$refs
REFS
}

check_refs frontend frontend-inputs.sh "$(relative_refs "$frontend_dir" -maxdepth 1 \
  \( -name 'vite.config.*' -o -name 'vitest.*' -o -name 'tsconfig*.json' \))"
check_refs backend backend-test-inputs.sh "$(relative_refs "$backend_dir/build.gradle.kts" "$backend_dir/src")"
exit "$fail"
