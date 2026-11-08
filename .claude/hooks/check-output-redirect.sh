#!/bin/bash
checks_cli="$(dirname "${BASH_SOURCE[0]}")/../skills/checks/bin/checks"
[[ -f "$checks_cli" ]] || exit 0
exec python3 -I "$checks_cli" hook
