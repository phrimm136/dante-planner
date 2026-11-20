#!/bin/bash
# PreToolUse hook (Bash): refuses a whole local run started by a session, `checks ... --full` or
# `planted-bugs.sh all`. The user's own `!` command does not pass through PreToolUse hooks.

input=$(cat)
[ "$(jq -r '.tool_name // empty' <<<"$input" 2>/dev/null)" = Bash ] || exit 0
command=$(jq -r '.tool_input.command // empty' <<<"$input" 2>/dev/null)
[ -n "$command" ] || exit 0

verdict=$(awk '
  function flush() {
    if (started && dropnext) dropnext = 0
    else if (started) w[++nw] = word
    word = ""; started = 0
  }
  function judge(   k, m, printing) {
    flush()
    printing = 0
    for (k = 1; k <= nw; k++) if (w[k] == "--print") printing = 1
    for (k = 1; k <= nw; k++) {
      if (w[k] ~ /\.claude\/skills\/checks\/bin\/checks$/ && !printing)
        for (m = k + 1; m <= nw; m++) if (w[m] == "--full") { print "W1 " w[k]; found = 1; return }
      if (w[k] ~ /scripts\/consistency\/planted-bugs\.sh$/ && k < nw && w[k + 1] == "all") { print "W2"; found = 1; return }
    }
    nw = 0
  }
  { text = text $0 "\n" }
  END {
    n = length(text); nheredoc = 0; i = 1; nw = 0; word = ""; started = 0; dropnext = 0; found = 0
    while (i <= n && !found) {
      c = substr(text, i, 1)
      if (c == "\\") { word = word substr(text, i, 2); started = 1; i += 2; continue }
      if (c == "\047") {
        j = index(substr(text, i + 1), "\047"); if (!j) j = n - i + 1
        word = word substr(text, i + 1, j - 1); started = 1; i += j + 1; continue
      }
      if (c == "\"") {
        started = 1; i++
        while (i <= n && substr(text, i, 1) != "\"") {
          if (substr(text, i, 1) == "\\") { word = word substr(text, i + 1, 1); i += 2 }
          else { word = word substr(text, i, 1); i++ }
        }
        i++; continue
      }
      if (substr(text, i, 3) == "<<<") { flush(); dropnext = 1; i += 3; continue }
      if (substr(text, i, 2) == "<<") {
        flush(); i += 2; strip = 0
        if (substr(text, i, 1) == "-") { strip = 1; i++ }
        while (substr(text, i, 1) ~ /[ \t]/) i++
        rest = substr(text, i)
        if (match(rest, /^(\047[^\047\n]*\047|"[^"\n]*"|[A-Za-z0-9_.-]+)/)) {
          d = substr(rest, 1, RLENGTH); i += RLENGTH
          gsub(/[\047"]/, "", d)
          nheredoc++; delim[nheredoc] = d; tabs[nheredoc] = strip
        }
        continue
      }
      if (c == "\n") {
        judge(); i++
        for (h = 1; h <= nheredoc; h++) {
          while (i <= n) {
            e = index(substr(text, i), "\n"); line = e ? substr(text, i, e - 1) : substr(text, i)
            i = e ? i + e : n + 1
            if (tabs[h]) sub(/^\t+/, "", line)
            if (line == delim[h]) break
          }
        }
        nheredoc = 0; continue
      }
      if (c ~ /[;&|]/) { judge(); i++; continue }
      if (c ~ /[ \t]/) { flush(); i++; continue }
      word = word c; started = 1; i++
    }
    if (!found) judge()
  }
' <<<"$command")

case "$verdict" in
  "W1 "*) cli=${verdict#W1 } ;;
  W2) cli=.claude/skills/checks/bin/checks ;;
  *) exit 0 ;;
esac
echo "full-run-guard: a whole local run is CI's. Run '$cli changed' for the checks this change reaches, or name the tests ('checks be test --tests PATTERN', 'planted-bugs.sh <variant>'). The user starts a whole run from their own prompt with '!'." >&2
exit 2
