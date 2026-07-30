#!/bin/bash
# Pre-flight gate for creating the AWS Organization. Creating it converts the CALLING
# account into the management account permanently: AWS offers no path from management
# back to member, so this is the one step in RFC 0001 that cannot be undone. Exit 0 =
# safe to create; non-zero = a prerequisite is missing (see the failing check).
#
# Codifies what must hold before the irreversible call:
#   1. Credentials resolve, and the operator is told which account is about to convert
#   2. That account belongs to no organization yet — neither as management nor as member
#   3. No identity-center instance already exists (one per account; a leftover blocks setup)
#   4. The account carries the contact details a management account is required to have
#   5. The operator has typed the account id back, so conversion is never a reflex
#
# Usage:
#   org-preflight.sh                      # prompts for the account id
#   org-preflight.sh --confirm <acct-id>  # non-interactive; must match the caller
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/common.sh
source "$SCRIPT_DIR/../lib/common.sh"

AWS_REGION="${AWS_REGION:-us-west-2}"
fatal=0
confirm_arg=""

if [[ "${1:-}" == "--confirm" ]]; then
    confirm_arg="${2:-}"
    [[ -n "$confirm_arg" ]] || { log_error "--confirm needs an account id"; exit 1; }
fi

# 1. Whose credentials are these, and what is the principal? This is the only check whose
# failure is fatal rather than a branch, so it reports what the CLI actually said.
if ! caller=$(aws sts get-caller-identity --output text --query '[Account,Arn]' 2>&1); then
    log_error "cannot resolve an identity — export AWS_PROFILE or run 'aws sso login' first"
    printf '%s\n' "$caller" | sed 's/^/  /' >&2
    exit 1
fi
ACCOUNT_ID=$(echo "$caller" | cut -f1)
CALLER_ARN=$(echo "$caller" | cut -f2)
log_info "caller  $CALLER_ARN"
log_info "account $ACCOUNT_ID  (region $AWS_REGION)"

# 2. Already in an organization? describe-organization answers from BOTH sides, so the
# master id distinguishes "we are already management" from "we are someone's member".
if org=$(aws organizations describe-organization \
        --output text --query 'Organization.[MasterAccountId,FeatureSet]' 2>/dev/null); then
    master=$(echo "$org" | cut -f1)
    features=$(echo "$org" | cut -f2)
    if [[ "$master" == "$ACCOUNT_ID" ]]; then
        log_error "this account is ALREADY the management account (feature set: $features)"
        log_error "  nothing to create — run org-verify.sh instead"
    else
        log_error "this account is already a MEMBER of another organization"
        log_error "  it must leave that organization before it can create one"
    fi
    fatal=1
else
    log_info "no organization on this account — creation is available"
fi

# 3. Identity Center is one-per-account and cannot be enabled by Terraform. A leftover
# instance from an earlier experiment has to be deleted before the console will set one up.
if inst=$(aws sso-admin list-instances --output text \
        --query 'Instances[0].IdentityStoreId' 2>/dev/null) && [[ -n "$inst" && "$inst" != "None" ]]; then
    log_warn "an identity-center instance already exists ($inst)"
    log_warn "  reuse it, or delete it in the console before enabling a fresh one"
else
    log_info "no identity-center instance yet"
fi

# 4. A management account pays the organization's consolidated bill, so AWS requires
# complete contact details on it. Missing details surface later as a vending failure.
if aws account get-contact-information >/dev/null 2>&1; then
    log_info "account contact information is present"
else
    log_warn "could not read contact information — confirm it in the billing console"
    log_warn "  member-account creation fails later if the management account is incomplete"
fi

# 5. Type the account id back. Everything above is reversible; the next command is not.
echo
log_warn "creating the organization converts account $ACCOUNT_ID into the MANAGEMENT account."
log_warn "there is no supported path from management back to member. Workloads running here"
log_warn "will sit outside every service control policy until they move to a member account."
echo
if [[ -n "$confirm_arg" ]]; then
    typed="$confirm_arg"
else
    read -rp "type the account id to confirm: " typed
fi
if [[ "$typed" != "$ACCOUNT_ID" ]]; then
    log_error "confirmation '$typed' does not match the caller's account"
    fatal=1
else
    log_info "confirmed"
fi

echo
if [[ $fatal -ne 0 ]]; then
    log_error "PRE-FLIGHT FAILED — do not create the organization"
    exit 1
fi
log_info "PRE-FLIGHT PASSED"
echo "done. Next:"
echo "  1. terraform -chdir=terraform/organization apply   # organization, units, policy types"
echo "  2. enable IAM Identity Center in the console (no API enables it)"
echo "  3. scripts/ops/provision/org-verify.sh             # assert the RFC 0001 invariants"
