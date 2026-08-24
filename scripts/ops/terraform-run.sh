#!/usr/bin/env bash
# Run Terraform behind the repo's guard.
# Usage: terraform-run.sh <terraform args...>
#   scripts/ops/terraform-run.sh -chdir=terraform/oregon plan -var-file=prod.tfvars
#
# The guard refuses a command whose credentials belong to a different account than the state
# bucket it would write, and one whose directory holds an auto-loaded terraform.tfvars. Both
# are inputs that reach a plan without appearing in the command; see lib/terraform-guard.sh.
#
# Everything else passes through untouched — arguments, stdin, exit status — so this is a
# drop-in for `terraform` in scripts and runbooks alike.
#
# GUARD=0 skips the check for one invocation. A guard with no escape hatch gets bypassed
# permanently the first time it is wrong, which costs more than it saves.
set -euo pipefail

OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source-path=SCRIPTDIR source=lib/terraform-guard.sh
source "$OPS_DIR/lib/terraform-guard.sh"

command -v terraform >/dev/null || {
    echo "terraform-run: no terraform binary on PATH" >&2
    exit 127
}

if [ "${GUARD:-1}" != "0" ]; then
    terraform_guard "$@" || exit $?
fi

exec terraform "$@"
