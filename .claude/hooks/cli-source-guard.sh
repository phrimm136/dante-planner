#!/bin/bash
# PreToolUse hook (Read, Bash): refuses reading a skill CLI's source and names its help command.
# A unit that changes a CLI creates <checkout>/.claude/.cli-edit first, which opens that checkout.

input=$(cat)
tool_name=$(jq -r '.tool_name // empty' <<<"$input")
cli_pattern='([^[:space:]"'"'"'=]*/)?\.claude/skills/[^/[:space:]]+/bin/[A-Za-z0-9_-]+'

case "$tool_name" in
  Read)
    target=$(jq -r '.tool_input.file_path // empty' <<<"$input")
    [[ "$target" =~ ^${cli_pattern}$ ]] || exit 0
    cli_path=$target ;;
  Bash)
    command=$(jq -r '.tool_input.command // empty' <<<"$input")
    reader="(^|[;&|(]|[[:space:]])(cat|bat|head|tail|sed|awk|rg|less|nl|wc)[[:space:]]([^;&|]*[[:space:]])?(${cli_pattern})([[:space:];&|)]|$)"
    [[ "$command" =~ $reader ]] || exit 0
    cli_path=${BASH_REMATCH[4]} ;;
  *) exit 0 ;;
esac

checkout=${cli_path%%.claude/skills/*}
if [[ "$checkout" != /* ]]; then
  checkout="$(jq -r '.cwd // empty' <<<"$input")/$checkout"
fi
[[ -e "${checkout%/}/.claude/.cli-edit" ]] && exit 0

cli_name=${cli_path##*/}
echo "cli-source-guard: run '$cli_path help' instead of reading the source of $cli_name. A unit that changes this CLI creates ${checkout%/}/.claude/.cli-edit first." >&2
exit 2
