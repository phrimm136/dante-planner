#!/usr/bin/env bash
# SSM port-forward tunnel to RDS — no bastion, no open ports, CloudTrail-audited.
# Usage: rds-tunnel.sh start|stop|status [oregon|seoul]
#   oregon (default) -> primary danteplanner-mysql; seoul -> read replica.
# Local port: 3306, override with RDS_TUNNEL_PORT.
#
# Every check here reads the process that OWNS the local socket, never the pid this script
# recorded. `aws ssm start-session` spawns session-manager-plugin as a child and that child
# holds the listener, so killing the recorded pid leaves the port served by an orphan the
# next `start` then mistakes for its own. Accounts also share site names and database
# identifiers, so only the endpoint hostname distinguishes one account's tunnel from another's.
set -euo pipefail
MODE=${1:?usage: rds-tunnel.sh start|stop|status [oregon|seoul]}
SITE=${2:-oregon}
LOCAL_PORT=${RDS_TUNNEL_PORT:-3306}
OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/constants.sh
source "$OPS_DIR/../lib/constants.sh"
# stop/status inspect local sockets only — no session needed there.
if [ "$MODE" = start ]; then require_aws_session; fi

case "$SITE" in
  oregon) REGION="us-west-2";      DB_ID="danteplanner-mysql" ;;
  seoul)  REGION="ap-northeast-2"; DB_ID="danteplanner-mysql-seoul" ;;
  *) echo "site must be oregon|seoul"; exit 2 ;;
esac
STATE_DIR="${XDG_RUNTIME_DIR:-/tmp/rds-tunnel-$(id -u)}"
(umask 077; mkdir -p "$STATE_DIR")
# mkdir -p is a no-op on a pre-existing dir, so umask 077 alone does not protect
# an attacker-planted /tmp fallback — verify we own it and force the mode.
[ -O "$STATE_DIR" ] || { echo "refusing: $STATE_DIR is not owned by us"; exit 1; }
chmod 700 "$STATE_DIR"
LOG_FILE="$STATE_DIR/rds-tunnel-${SITE}-${LOCAL_PORT}.log"

app_instance() {
  aws ec2 describe-instances --region "$REGION" \
    --filters "Name=tag:Name,Values=danteplanner-${SITE}-app" \
              "Name=instance-state-name,Values=running" \
    --query 'Reservations[0].Instances[0].InstanceId' --output text
}

rds_endpoint() {
  aws rds describe-db-instances --region "$REGION" \
    --db-instance-identifier "$DB_ID" \
    --query 'DBInstances[0].Endpoint.Address' --output text
}

# Pid holding the local listener, empty if the port is free. Always succeeds: a free port is
# an answer, not an error, and under `set -e` a failing grep here would abort the caller.
port_owner() {
  ss -tlnpH "sport = :${LOCAL_PORT}" 2>/dev/null \
    | grep -oP 'pid=\K[0-9]+' | sort -u | head -1 || true
}

# The RDS endpoint a given forwarder was started against, read from its own argv. SSM sets
# the far end from these parameters, so this is the tunnel's real target, not an intention.
owner_endpoint() {
  local pid=$1
  [ -n "$pid" ] || return 1
  tr '\0' ' ' <"/proc/$pid/cmdline" 2>/dev/null \
    | grep -oE '[a-z0-9-]+\.[a-z0-9]+\.[a-z0-9-]+\.rds\.amazonaws\.com' | head -1
}

case "$MODE" in
  start)
    endpoint=$(rds_endpoint)
    [ -n "$endpoint" ] && [ "$endpoint" != "None" ] || { echo "cannot resolve ${DB_ID} in ${REGION}"; exit 1; }

    owner=$(port_owner)
    if [ -n "$owner" ]; then
      held=$(owner_endpoint "$owner" || true)
      if [ "$held" = "$endpoint" ]; then
        echo "already running (pid $owner, 127.0.0.1:${LOCAL_PORT} -> ${endpoint})"; exit 0
      fi
      echo "refusing: 127.0.0.1:${LOCAL_PORT} is already served by pid $owner" >&2
      echo "  that tunnel reaches ${held:-<unknown endpoint>}" >&2
      echo "  this request wants  ${endpoint}" >&2
      echo "  stop it first, or pick another port with RDS_TUNNEL_PORT" >&2
      exit 1
    fi

    instance=$(app_instance)
    [ "$instance" != "None" ] || { echo "no running ${SITE} app instance found"; exit 1; }
    nohup aws ssm start-session --region "$REGION" --target "$instance" \
      --document-name AWS-StartPortForwardingSessionToRemoteHost \
      --parameters "{\"host\":[\"${endpoint}\"],\"portNumber\":[\"3306\"],\"localPortNumber\":[\"${LOCAL_PORT}\"]}" \
      >"$LOG_FILE" 2>&1 &
    launched=$!

    # Readiness is "the port is served by a forwarder aimed at OUR endpoint", not "the port
    # accepts a connection" — the latter is equally true of someone else's tunnel.
    for _ in $(seq 1 20); do
      owner=$(port_owner)
      if [ -n "$owner" ] && [ "$(owner_endpoint "$owner" || true)" = "$endpoint" ]; then
        echo "tunnel up: 127.0.0.1:${LOCAL_PORT} -> ${endpoint} (${DB_ID} via ${instance}, pid $owner)"
        exit 0
      fi
      sleep 1
    done
    echo "tunnel did not come up in 20s — see $LOG_FILE" >&2
    kill "$launched" 2>/dev/null || true
    exit 1
    ;;
  stop)
    owner=$(port_owner)
    [ -n "$owner" ] || { echo "not running (nothing listening on 127.0.0.1:${LOCAL_PORT})"; exit 0; }
    held=$(owner_endpoint "$owner" || true)
    kill -TERM "$owner" 2>/dev/null || true
    # Killing the socket owner orphans its parent wrapper; take that too so no session lingers.
    parent=$(awk '{print $4}' "/proc/$owner/stat" 2>/dev/null || true)
    [ -n "${parent:-}" ] && [ "$parent" != 1 ] && kill -TERM "$parent" 2>/dev/null || true
    for _ in $(seq 1 10); do
      [ -z "$(port_owner)" ] && { echo "tunnel stopped (was -> ${held:-<unknown>})"; exit 0; }
      sleep 1
    done
    echo "port ${LOCAL_PORT} still held by $(port_owner) after SIGTERM" >&2
    exit 1
    ;;
  status)
    owner=$(port_owner)
    if [ -z "$owner" ]; then echo "down"; exit 1; fi
    held=$(owner_endpoint "$owner" || true)
    echo "running (pid $owner, 127.0.0.1:${LOCAL_PORT} -> ${held:-<unknown endpoint>})"
    # A tunnel to a different account's same-named database is the failure this reports on.
    [ "$MODE" = status ] && [ -n "$held" ] && echo "  site ${SITE} expects ${DB_ID} in ${REGION}"
    ;;
  *) echo "mode must be start|stop|status"; exit 2 ;;
esac
