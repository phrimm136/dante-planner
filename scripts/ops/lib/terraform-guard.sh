# shellcheck shell=bash
# Refusals for Terraform commands whose target directory contradicts the command naming it.
#
# Two inputs reach a plan without appearing on the command line, and each has its own check:
#
#   1. The backend. A stack's account lives in .terraform/terraform.tfstate, which no command
#      names, so a directory initialized against one account's state bucket accepts a
#      -var-file describing another. The provider's allowed_account_ids catches only the
#      reverse case. Terraform would read the wrong account's state, refresh resource ids it
#      does not hold, and drop them from state as deleted. Buckets are named
#      <prefix>-tfstate-<account_id>, which is what makes the comparison possible. Where the
#      backend carries assume_role, that role reaches the bucket and the ambient caller
#      legitimately differs, so only the role's account is compared.
#
#   2. terraform.tfvars. Terraform auto-loads that name on every invocation that evaluates
#      variables, including ones passing a different -var-file, so one environment inherits
#      another's values silently.
#
# init is exempt: it is the command that repairs a mismatch.

# tf_guard_subcommand ARGS...
# Echoes the Terraform subcommand, empty if the args carry none.
tf_guard_subcommand() {
    local a sub=""
    for a in "$@"; do
        case "$a" in
            -*) ;;
            *) [ -z "$sub" ] && sub="$a" ;;
        esac
    done
    printf '%s\n' "$sub"
}

# tf_guard_chdir ARGS...
# Echoes the -chdir target, "." when absent.
tf_guard_chdir() {
    local a
    for a in "$@"; do
        case "$a" in
            -chdir=*) printf '%s\n' "${a#-chdir=}"; return 0 ;;
        esac
    done
    printf '.\n'
}

# tf_guard_backend DIR
# Echoes "BUCKET ROLE_ARN", each "-" when absent. Empty output means no stored backend,
# which is a local-state or uninitialized directory rather than a fault.
tf_guard_backend() {
    local cfg="$1/.terraform/terraform.tfstate"
    [ -f "$cfg" ] || return 1
    python3 -c "
import json
try:
    c = json.load(open('$cfg')).get('backend', {}).get('config', {}) or {}
except Exception:
    c = {}
ar = c.get('assume_role') or {}
print(c.get('bucket') or '-', (ar.get('role_arn') if isinstance(ar, dict) else None) or '-')
" 2>/dev/null
}

# terraform_guard ARGS...
# Returns 0 to allow, 2 to refuse with the reason on stderr. Mutates nothing.
terraform_guard() {
    local sub target bucket role_arn bucket_acct role_acct caller

    # Guarded: anything that refreshes state or acts on infrastructure. `output` and `console`
    # only read what is already stored, are used inside command substitution across the ops
    # scripts, and would gain a credential lookup and a failure mode for no protection.
    sub=$(tf_guard_subcommand "$@")
    case "$sub" in
        plan|apply|destroy|refresh|import|taint|untaint|state) ;;
        *) return 0 ;;
    esac

    target=$(tf_guard_chdir "$@")
    [ -d "$target" ] || return 0

    if [ -f "$target/terraform.tfvars" ]; then
        {
            echo "terraform-guard: $target/terraform.tfvars exists and Terraform auto-loads it."
            echo "Its values would reach this plan without appearing in the command."
            echo "  mv $target/terraform.tfvars $target/<environment>.tfvars"
            echo "  then pass it with -var-file=<environment>.tfvars"
        } >&2
        return 2
    fi

    read -r bucket role_arn < <(tf_guard_backend "$target") || return 0
    [ "${bucket:--}" = "-" ] && return 0

    # Only the <prefix>-tfstate-<account_id> convention is understood; anything else is left
    # alone rather than guessed at.
    bucket_acct=$(grep -oE '[0-9]{12}$' <<<"$bucket" || true)
    [ -z "$bucket_acct" ] && return 0

    if [ "${role_arn:--}" != "-" ]; then
        role_acct=$(grep -oE '::[0-9]{12}:' <<<"$role_arn" | tr -d ':' || true)
        [ -z "$role_acct" ] && return 0
        [ "$role_acct" = "$bucket_acct" ] && return 0
        {
            echo "terraform-guard: the backend assumes a role in $role_acct but the state"
            echo "bucket $bucket belongs to $bucket_acct."
        } >&2
        return 2
    fi

    caller=$(aws sts get-caller-identity --query Account --output text 2>/dev/null || true)
    # An unresolvable identity is the credential layer's problem to report, not this guard's.
    { [ -z "$caller" ] || [ "$caller" = "None" ]; } && return 0
    [ "$caller" = "$bucket_acct" ] && return 0

    {
        echo "terraform-guard: credentials and state bucket disagree on the account."
        echo
        echo "  credentials resolve to  $caller${AWS_PROFILE:+  (AWS_PROFILE=$AWS_PROFILE)}"
        echo "  $target is initialized against $bucket"
        echo "  which belongs to        $bucket_acct"
        echo
        echo "Terraform would read that account's state with these credentials, find none of"
        echo "its resource ids, and drop them from state as deleted. Re-point the directory:"
        echo "  terraform -chdir=$target init -reconfigure -backend-config=../backend.<account>.hcl"
        echo "Never -migrate-state, which copies state between accounts."
    } >&2
    return 2
}
