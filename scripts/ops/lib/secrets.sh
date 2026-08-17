# shellcheck shell=bash
# AWS Secrets Manager plumbing.
#
# Contract for every function here: the region is always the first argument, so
# no call depends on ambient AWS_REGION/AWS_DEFAULT_REGION state. Values travel
# on stdin (`file:///dev/stdin`) or as a file reference — never on argv, because
# /proc/PID/cmdline is world-readable. All of them return the underlying aws
# CLI's status, so they are safe to test in an `if`.

# secret_exists REGION NAME
secret_exists() {
    aws secretsmanager describe-secret --region "$1" --secret-id "$2" >/dev/null 2>&1
}

# create_secret REGION NAME [aws create-secret args...]
create_secret() {
    local region=$1 name=$2
    shift 2
    aws secretsmanager create-secret --region "$region" --name "$name" "$@" >/dev/null
}

# ensure_secret REGION NAME [aws create-secret args...]
# Creates the container only when it is absent. Never writes a value.
ensure_secret() {
    secret_exists "$1" "$2" || create_secret "$@"
}

# put_secret REGION NAME  — value read from stdin
put_secret() {
    aws secretsmanager put-secret-value --region "$1" --secret-id "$2" \
        --secret-string file:///dev/stdin >/dev/null
}

# put_secret_file REGION NAME FILE
put_secret_file() {
    aws secretsmanager put-secret-value --region "$1" --secret-id "$2" \
        --secret-string "file://$3" >/dev/null
}
