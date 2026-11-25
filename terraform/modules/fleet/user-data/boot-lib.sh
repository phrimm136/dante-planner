# Retry a command up to 5 times (10s apart) before giving up. Every network step
# of the boot scripts runs through it: single-shot, one transient failure under
# `set -e` aborts the whole unattended boot and leaves a dead node. The messages
# name only the command word, because arguments can hold secrets.
retry() {
  local n=0
  until "$@"; do
    n=$((n + 1))
    echo "retry: $1 failed (attempt $n of 5)" >&2
    if [ "$n" -ge 5 ]; then
      return 1
    fi
    sleep 10
  done
}

install_awscli() {
  dnf install -y awscli-2 || dnf install -y awscli
}
