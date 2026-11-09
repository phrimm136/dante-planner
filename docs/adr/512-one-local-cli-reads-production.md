# 512 one-local-cli-reads-production
epic: none · pr: none

## Decisions
- @tooling @ops — Every repeated workstation read of production (Mimir metrics, Loki logs, read-only SQL, the RDS tunnel, the DB and Oregon verification checks) and every terraform plan runs through one CLI, `ops`, whose commands change no cloud state; the Bash hook refuses direct calls to the scripts it wraps and names the `ops` command. The four access scripts carry most production commands, each hand-prefixed with `AWS_PROFILE=prod`, because the shared constants defaulted to a profile `danteplanner` that `~/.aws/config` does not define. Re-check: `.claude/skills/ops/bin/ops list` names no command outside the read and plan set, and the hook refuses `scripts/ops/access/logs-query.sh '{app="backend"}'`.
  REJECTED: write commands (Redis restart, secret writes, provisioning) in the first version — they run rarely and from runbooks, and with them inside, one allow rule for `ops` would admit a production write; each would need its own confirmation design.
  REJECTED: having CI call `ops` — CI authenticates differently and keeps calling the scripts by path; the scripts stay the implementation.
  INVARIANT: no `ops` command changes cloud state — enforced by a test that pins the CLI's dispatch table to the read and plan commands.
  NON-GOAL: terraform apply — the plan prints the user's `!` apply command and the user runs it.
- @ops — The AWS profile is chosen explicitly per command (`--env prod|staging`, default prod) and the shared shell constants set no profile. CI authenticates through OIDC environment credentials, which a set but undefined profile would override, and the staging rehearsal sources those constants. Re-check: `rg -n 'AWS_PROFILE=|AWS_PROFILE:-' scripts/ops/lib` finds no assignment.
  REJECTED: fixing the default to `prod` — a script run without thought would reach production silently, and CI would still inherit a profile it does not have.

## Takeaway
- takeaway: a default that names nothing real teaches every caller a prefix; make the choice explicit at the one entry point instead.
