#!/usr/bin/env bash
# SSM port-forward tunnel to RDS — no bastion, no open ports, CloudTrail-audited.
# Usage: rds-tunnel.sh start|stop|status [oregon|seoul]
#   oregon (default) -> primary danteplanner-mysql; seoul -> read replica.
# Local port: 3306, override with RDS_TUNNEL_PORT. PID file under /tmp.
set -euo pipefail
MODE=${1:?usage: rds-tunnel.sh start|stop|status [oregon|seoul]}
SITE=${2:-oregon}
LOCAL_PORT=${RDS_TUNNEL_PORT:-3306}
OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/constants.sh
source "$OPS_DIR/../lib/constants.sh"
# stop/status only touch the local PID file — no session needed there.
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
PID_FILE="$STATE_DIR/rds-tunnel-${SITE}.pid"
LOG_FILE="$STATE_DIR/rds-tunnel-${SITE}.log"

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

# A live PID alone is not proof of ownership (PID reuse, planted pid file):
# only treat it as ours if its cmdline is an ssm start-session.
is_up() {
  [ -f "$PID_FILE" ] || return 1
  local pid; pid=$(cat "$PID_FILE")
  [[ $pid =~ ^[0-9]+$ ]] && grep -qa "start-session" "/proc/$pid/cmdline" 2>/dev/null
}

case "$MODE" in
  start)
    if is_up; then echo "already running (pid $(cat "$PID_FILE"), local port ${LOCAL_PORT})"; exit 0; fi
    instance=$(app_instance)
    [ "$instance" != "None" ] || { echo "no running ${SITE} app instance found"; exit 1; }
    endpoint=$(rds_endpoint)
    nohup aws ssm start-session --region "$REGION" --target "$instance" \
      --document-name AWS-StartPortForwardingSessionToRemoteHost \
      --parameters "{\"host\":[\"${endpoint}\"],\"portNumber\":[\"3306\"],\"localPortNumber\":[\"${LOCAL_PORT}\"]}" \
      >"$LOG_FILE" 2>&1 &
    echo $! >"$PID_FILE"
    for _ in $(seq 1 20); do
      if (exec 3<>"/dev/tcp/127.0.0.1/${LOCAL_PORT}") 2>/dev/null; then exec 3>&-
        echo "tunnel up: 127.0.0.1:${LOCAL_PORT} -> ${DB_ID} (via ${instance})"; exit 0
      fi
      sleep 1
    done
    echo "tunnel did not come up in 20s — see $LOG_FILE"
    kill "$(cat "$PID_FILE")" 2>/dev/null || true; rm -f "$PID_FILE"; exit 1
    ;;
  stop)
    is_up || { echo "not running"; rm -f "$PID_FILE"; exit 0; }
    kill "$(cat "$PID_FILE")" && rm -f "$PID_FILE" && echo "tunnel stopped"
    ;;
  status)
    if is_up; then echo "running (pid $(cat "$PID_FILE"), local port ${LOCAL_PORT})"; else echo "down"; exit 1; fi
    ;;
  *) echo "mode must be start|stop|status"; exit 2 ;;
esac
