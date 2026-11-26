#!/usr/bin/env bash
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
region=us-west-2
stacks=(oregon seoul)

usage() {
  echo "usage: seed-tf-inputs.sh --env <prod|staging> [--apply]" >&2
  exit 2
}

env=""
apply=false
while [ $# -gt 0 ]; do
  case "$1" in
    --env)
      [ $# -ge 2 ] || usage
      env=$2
      shift 2
      ;;
    --apply)
      apply=true
      shift
      ;;
    *) usage ;;
  esac
done
case "$env" in
  prod | staging) ;;
  *) usage ;;
esac

die() {
  echo "seed-tf-inputs: $1" >&2
  exit 1
}

assignment() {
  local file=$1 key=$2 stripped count
  stripped=$(sed 's/#.*//' "$file")
  count=$(printf '%s\n' "$stripped" | grep -Ec "^[[:space:]]*${key}[[:space:]]*=" || true)
  [ "$count" -eq 1 ] || die "$file assigns $key $count times; expected once"
  printf '%s\n' "$stripped" | sed -n "/^[[:space:]]*${key}[[:space:]]*=/,\$p" | tr '\n' ' ' |
    sed -E "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*//"
}

string_value() {
  assignment "$1" "$2" | sed -nE 's/^"([^"]*)".*/\1/p'
}

list_value() {
  assignment "$1" "$2" | sed -nE 's/^\[([^]]*)\].*/\1/p' | tr ',' '\n' |
    sed -nE 's/^[[:space:]]*"([^"]*)"[[:space:]]*$/\1/p' | paste -sd, -
}

names=()
values=()
account=""
for stack in "${stacks[@]}"; do
  file="$repo_root/terraform/$stack/$env.tfvars"
  [ -f "$file" ] || die "no $file; copy it from a checkout that has it, or write it with aws_account_id and ingress_allowed_cidrs, then run again"

  file_account=$(string_value "$file" aws_account_id)
  [[ "$file_account" =~ ^[0-9]{12}$ ]] || die "$file: aws_account_id is not a 12-digit account id"
  if [ -z "$account" ]; then
    account=$file_account
  elif [ "$account" != "$file_account" ]; then
    die "$file names a different account than terraform/${stacks[0]}/$env.tfvars"
  fi

  cidrs=$(list_value "$file" ingress_allowed_cidrs)
  [ -n "$cidrs" ] || die "$file: ingress_allowed_cidrs is empty or not a list of strings"
  IFS=, read -ra cidr_items <<<"$cidrs"
  for cidr in "${cidr_items[@]}"; do
    [[ "$cidr" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}/[0-9]{1,2}$ ]] || die "$file: an ingress_allowed_cidrs entry is not an IPv4 CIDR"
  done

  names+=("/danteplanner/$env/$stack/ingress_allowed_cidrs")
  values+=("$cidrs")
done

for i in "${!names[@]}"; do
  echo "  ${names[$i]}  StringList  $(tr -cd , <<<"${values[$i]}" | wc -c | awk '{print $1 + 1}') entries"
done

if ! $apply; then
  echo "dry run, region $region: nothing written; run again with --apply to write these ${#names[@]} parameters"
  exit 0
fi

caller=$(aws sts get-caller-identity --query Account --output text) ||
  die "the profile's account is unreadable; run aws sso login for the $env profile and run again"
[ "$caller" = "$account" ] || die "the profile's account is not the one the $env tfvars name; set AWS_PROFILE to the $env profile"

for i in "${!names[@]}"; do
  aws ssm put-parameter --region "$region" --name "${names[$i]}" --type StringList \
    --value "${values[$i]}" --overwrite >/dev/null
  echo "  wrote ${names[$i]}"
done
echo "done: ${#names[@]} parameters written in $region"
