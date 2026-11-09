#!/bin/bash
ops_cli="$(dirname "${BASH_SOURCE[0]}")/../skills/ops/bin/ops"
[[ -f "$ops_cli" ]] || exit 0
exec python3 -I "$ops_cli" hook
