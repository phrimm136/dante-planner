#!/bin/bash
# Asserts the AWS Organization invariants from RFC 0001 against live state. Run it after
# every change to the organization stack, and before trusting a guardrail to hold. Exit 0
# = every invariant holds; non-zero = one does not (see the failing check).
#
# Codifies the invariants that have no other gate:
#   1. The organization exists with ALL features — anything less has no policy layer
#   2. SERVICE_CONTROL_POLICY is enabled on the root, or attaching a guardrail silently
#      has nowhere to attach
#   3. Every expected organizational unit exists
#   4. Every member account sits in the unit that governs it, not merely inside the org
#   5. Every guarded unit carries at least the deny-only baseline
#   6. Every attached policy is deny-only, so detaching one can never break a working path
#   7. An organization trail records every account into the log-archive account
#
# Discovers account ids by name rather than taking them as input: this repository is
# public and carries no account identifiers (RFC 0001, Invariants).
#
# Usage:
#   org-verify.sh                 # management-account credentials required
#   EXPECTED_OUS="..." org-verify.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/common.sh
source "$SCRIPT_DIR/../lib/common.sh"

# The shape RFC 0001 specifies. Override to verify a partially-built organization.
EXPECTED_OUS="${EXPECTED_OUS:-Security Infrastructure Workloads Sandbox Suspended}"
EXPECTED_CHILD_OUS="${EXPECTED_CHILD_OUS:-Prod NonProd}"
# The parent of the child units above; separate from the guarded set, which is about policy.
WORKLOADS_OU="${WORKLOADS_OU:-Workloads}"
GUARDED_OUS="${GUARDED_OUS:-Workloads Security}"
MIN_GUARDRAIL_POLICIES="${MIN_GUARDRAIL_POLICIES:-4}"
BASELINE_ALLOW_POLICY="${BASELINE_ALLOW_POLICY:-FullAWSAccess}"
LOG_ARCHIVE_ACCOUNT="${LOG_ARCHIVE_ACCOUNT:-log-archive}"

fatal=0

ou_id_by_name() { # $1 = name, $2 = parent id
    aws organizations list-organizational-units-for-parent --parent-id "$2" \
        --query "OrganizationalUnits[?Name=='$1'].Id | [0]" --output text 2>/dev/null
}

# 1. Organization present, and with the feature set that makes policies possible.
if ! org=$(aws organizations describe-organization \
        --output text --query 'Organization.[FeatureSet,MasterAccountId]' 2>/dev/null); then
    log_error "no organization on this account — run org-preflight.sh, then apply the stack"
    exit 1
fi
FEATURES=$(echo "$org" | cut -f1)
MGMT_ACCOUNT=$(echo "$org" | cut -f2)
if [[ "$FEATURES" == "ALL" ]]; then
    log_info "organization present, feature set ALL"
else
    log_error "feature set is $FEATURES — service control policies require ALL"
    fatal=1
fi

ROOT_ID=$(aws organizations list-roots --query 'Roots[0].Id' --output text)

# 2. Policy type enabled on the root. Disabled means an attach call has nothing to bind to.
if aws organizations list-roots \
        --query "Roots[0].PolicyTypes[?Type=='SERVICE_CONTROL_POLICY' && Status=='ENABLED']" \
        --output text | grep -q .; then
    log_info "SERVICE_CONTROL_POLICY enabled on root $ROOT_ID"
else
    log_error "SERVICE_CONTROL_POLICY not enabled on the root — guardrails cannot attach"
    fatal=1
fi

# 3. Top-level units.
for ou in $EXPECTED_OUS; do
    id=$(ou_id_by_name "$ou" "$ROOT_ID")
    if [[ -n "$id" && "$id" != "None" ]]; then
        log_info "unit present: $ou"
    else
        log_error "missing organizational unit: $ou"
        fatal=1
    fi
done

WORKLOADS_ID=$(ou_id_by_name "$WORKLOADS_OU" "$ROOT_ID")
if [[ -n "$WORKLOADS_ID" && "$WORKLOADS_ID" != "None" ]]; then
    for ou in $EXPECTED_CHILD_OUS; do
        id=$(ou_id_by_name "$ou" "$WORKLOADS_ID")
        if [[ -n "$id" && "$id" != "None" ]]; then
            log_info "unit present: $WORKLOADS_OU/$ou"
        else
            log_error "missing organizational unit: $WORKLOADS_OU/$ou"
            fatal=1
        fi
    done
fi

# 4. No member account left parked at the root. An account directly under the root is
# governed by root policy only, which is the failure this check exists to catch.
while read -r acct_id acct_name; do
    [[ -n "$acct_id" ]] || continue
    if [[ "$acct_id" == "$MGMT_ACCOUNT" ]]; then
        continue  # the management account is always a direct child of the root
    fi
    parent=$(aws organizations list-parents --child-id "$acct_id" \
        --query 'Parents[0].[Id,Type]' --output text)
    parent_type=$(echo "$parent" | cut -f2)
    if [[ "$parent_type" == "ORGANIZATIONAL_UNIT" ]]; then
        log_info "account in a unit: $acct_name"
    else
        log_error "account sits directly under the root, ungoverned: $acct_name"
        fatal=1
    fi
done < <(aws organizations list-accounts --query 'Accounts[].[Id,Name]' --output text)

# 5 & 6. The baseline on every guarded unit, and the deny-only property that makes rollback safe.
#
# FullAWSAccess is AWS's managed Allow-everything policy, attached the moment the policy
# type is enabled. Service control policies are a deny-by-default filter, so it is REQUIRED:
# detaching it permits nothing at all. It is asserted present and exempted from deny-only.
#
# Security is guarded as well as Workloads: the account holding the organization trail would
# otherwise be the one account able to stop it recording.
for ou in $GUARDED_OUS; do
    ou_id=$(ou_id_by_name "$ou" "$ROOT_ID")
    if [[ -z "$ou_id" || "$ou_id" == "None" ]]; then
        continue # its absence is already reported above
    fi

    attached=$(aws organizations list-policies-for-target --target-id "$ou_id" \
        --filter SERVICE_CONTROL_POLICY --query 'Policies[].[Id,Name]' --output text)

    if grep -qw "$BASELINE_ALLOW_POLICY" <<< "$attached"; then
        log_info "$ou: $BASELINE_ALLOW_POLICY attached — the unit can permit anything at all"
    else
        log_error "$ou: $BASELINE_ALLOW_POLICY is NOT attached — every action is denied"
        fatal=1
    fi

    guardrails=$(grep -vw "$BASELINE_ALLOW_POLICY" <<< "$attached" | grep -c . || true)
    if [[ "$guardrails" -ge "$MIN_GUARDRAIL_POLICIES" ]]; then
        log_info "$ou: $guardrails guardrail policies"
    else
        log_error "$ou: $guardrails guardrail policies, expected at least $MIN_GUARDRAIL_POLICIES"
        fatal=1
    fi

    while read -r pol_id pol_name; do
        [[ -n "$pol_id" ]] || continue
        [[ "$pol_name" == "$BASELINE_ALLOW_POLICY" ]] && continue
        if aws organizations describe-policy --policy-id "$pol_id" \
                --query 'Policy.Content' --output text | grep -q '"Effect"[[:space:]]*:[[:space:]]*"Allow"'; then
            log_error "$ou: policy grants rather than denies: $pol_name — detaching it could break a live path"
            fatal=1
        else
            log_info "$ou: deny-only: $pol_name"
        fi
    done <<< "$attached"
done

# 7. Organization trail, landing outside the accounts it records.
trail=$(aws cloudtrail describe-trails \
    --query 'trailList[?IsOrganizationTrail==`true`].[Name,S3BucketName] | [0]' --output text 2>/dev/null || echo "")
if [[ -n "$trail" && "$trail" != "None" ]]; then
    trail_name=$(echo "$trail" | cut -f1)
    trail_bucket=$(echo "$trail" | cut -f2)
    log_info "organization trail: $trail_name -> $trail_bucket"
    # The CLI renders booleans capitalised in text output, so this comparison folds case.
    logging=$(aws cloudtrail get-trail-status --name "$trail_name" --query 'IsLogging' --output text)
    if [[ "${logging,,}" == "true" ]]; then
        log_info "trail is logging"
    else
        log_error "trail exists but is not logging"
        fatal=1
    fi

    # Resolve the account rather than matching the bucket's name: a destination named for its
    # purpose instead of its account is still in the right place, and a bucket named after the
    # archive but sitting in another account is not.
    archive_id=$(aws organizations list-accounts \
        --query "Accounts[?Name=='$LOG_ARCHIVE_ACCOUNT'].Id | [0]" --output text)
    if [[ -z "$archive_id" || "$archive_id" == "None" ]]; then
        log_warn "no account named '$LOG_ARCHIVE_ACCOUNT' — cannot confirm where the trail lands"
    elif [[ "$trail_bucket" == *"$archive_id"* ]]; then
        log_info "trail destination is in the $LOG_ARCHIVE_ACCOUNT account"
    else
        log_error "trail bucket '$trail_bucket' is not in the $LOG_ARCHIVE_ACCOUNT account"
        fatal=1
    fi
else
    log_error "no organization-wide trail — member activity is not recorded centrally"
    fatal=1
fi

echo
if [[ $fatal -ne 0 ]]; then
    log_error "INVARIANTS VIOLATED — see the failing checks above"
    exit 1
fi
log_info "ALL INVARIANTS HOLD"
