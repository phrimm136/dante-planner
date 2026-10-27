#!/bin/sh
set -eu

root=$(cd "$(dirname "$0")/../.." && pwd)
lock_file="$root/static.lock"

if [ -z "${STATIC_TOKEN:-}" ]; then
  echo "fetch-static: STATIC_TOKEN is unset or empty; set it to a read-only token for the data repository" >&2
  exit 1
fi
[ -f "$lock_file" ] || { echo "fetch-static: no static.lock at $lock_file" >&2; exit 1; }
sha=$(tr -d '[:space:]' < "$lock_file")
case $sha in
  *[!0-9a-f]*) valid=no ;;
  *) [ ${#sha} -eq 40 ] && valid=yes || valid=no ;;
esac
[ "$valid" = yes ] || { echo "fetch-static: static.lock is not one 40-hex sha" >&2; exit 1; }

cd "$root"
if .claude/skills/static-checkout/bin/static check; then
  echo "fetch-static: static already at $sha"
  exit 0
fi

git init -q static
git -C static fetch --quiet --depth 1 \
  "https://x-access-token:${STATIC_TOKEN}@github.com/phrimm136/danteplanner-static.git" "$sha"
git -C static checkout -q -f --detach FETCH_HEAD
echo "fetch-static: fetched $sha into static"
