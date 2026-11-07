#!/bin/sh
set -eu

repo=$(cd "$(dirname "$0")/../.." && pwd)
tf="$repo/scripts/ops/terraform-run.sh"
terraform_dir=$(cd "${1:-$repo/terraform}" && pwd)

fail=0

if ! "$tf" fmt -check -recursive -no-color "$terraform_dir"; then
  echo "terraform fmt: the files above are unformatted; run scripts/ops/terraform-run.sh fmt -recursive terraform" >&2
  fail=1
fi

stacks=0
for dir in "$terraform_dir"/*/; do
  stack=$(basename "$dir")
  [ "$stack" = modules ] && continue
  stacks=$((stacks + 1))
  echo "== $stack"
  if "$tf" -chdir="$terraform_dir/$stack" init -backend=false -input=false -lockfile=readonly -no-color \
    && "$tf" -chdir="$terraform_dir/$stack" validate -no-color; then
    echo "ok: $stack"
  else
    echo "failed: $stack (init -backend=false or validate; the error is above)" >&2
    fail=1
  fi
done

if [ "$stacks" -eq 0 ]; then
  echo "no root stacks found under $terraform_dir" >&2
  exit 1
fi

echo "terraform-validate: $stacks stacks, $([ "$fail" -eq 0 ] && echo pass || echo FAIL)"
exit "$fail"
