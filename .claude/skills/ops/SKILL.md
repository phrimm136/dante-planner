---
name: ops
description: Read production (Mimir metrics, Loki logs, read-only SQL, the RDS tunnel, the DB and Oregon verification checks) and run terraform plans through one local CLI that names the AWS profile, checks the SSO session, writes a dated log and changes cloud state only through `tf apply`, which the user runs and the hook refuses in every form it can see in a Bash-tool command. Use for any production read or terraform plan, when the Bash hook refuses a direct call to scripts/ops/access/*, oregon-verify.sh or terraform-run.sh plan, or when an AWS session, Docker or a tunnel looks wrong.
---

# ops

Every repeated workstation read of production and every terraform plan runs as
`ops <command> [args] [--env prod|staging|mgmt]` (ADR 512). `--env` sets `AWS_PROFILE` for the
wrapped script (`prod`, `staging` or `mgmt`, the profiles in `~/.aws/config`; default `prod`), and the
CLI checks that profile's SSO session before it runs anything. The script's output goes to
stdout and to `.claude/reports/logs/ops-<command>-<YYYYMMDD-HHMMSS>.log`; the exit code is the
script's. Only `tf apply <plan file>` writes to the cloud: it applies a plan `tf plan` cleared and the user runs it with `!`; the hook refuses its direct forms and any command whose text names the CLI or `terraform-run.sh` together with the word `apply` or `destroy`, which guards ordinary commands but is no sandbox against a process sharing the user's cloud session. The
`ops-redirect.sh` hook refuses direct calls to the scripts it wraps and names the `ops` command.
CI does not call this CLI. Every line it prints or logs passes one redaction: each `sso_account_id` in
`~/.aws/config` (or `$AWS_CONFIG_FILE`) becomes `<account:PROFILE>` and any other account number inside
an ARN becomes `<account>`; `whoami` and `doctor` name the profile and the caller, never the number.

The CLI runs the scripts of the checkout it lives in: from a worktree run
`<worktree>/.claude/skills/ops/bin/ops`. `tf plan <stack>` and `verify oregon` (unless
`CP_INSTANCE_ID` is set) need `terraform/<stack>/.terraform` in that checkout; when it is missing
they stop before terraform runs and print the `init` command, with absolute paths and the
stack's backend file (`backend.<env>.hcl` from `--env`, or the fixed file a fixed-account stack
names in its header). Worktrees start uninitialized; plan from the main checkout or run that init.

| Command | Use |
|---------|-----|
| `doctor` | First, when anything fails: SSO session per profile with the login command, `session-manager-plugin`, Docker and the pinned mysql client image, `jq`, open tunnels |
| `list` | Every command with its purpose |
| `whoami` | The account the selected profile resolves to |
| `metrics '<promql>' [--since D] [--step S]` | Mimir range query |
| `logs '<logql>' [--since D] [--limit N]` / `logs --day YYYY-MM-DD '<logql>'` | Loki range query |
| `sql "<SELECT …>" [--site oregon\|seoul]` / `sql -f FILE` | Read-only SQL; starts the tunnel when down |
| `tunnel start\|stop\|status [--site …]` | The SSM port-forward to RDS |
| `verify db` / `verify oregon` | The DB observability check / the Oregon fleet check |
| `tf plan <stack> [--expect <address>]… [-- terraform args]` | Plan file and full text to the log; prints one line per changed resource (destroy, replace, update, create, import, move), the counts, and, when every replace, destroy and update of an `aws_instance`, `aws_autoscaling_group` or `aws_db_instance` is named with `--expect <address>`, the `! … ops tf apply <plan file>` command for the user to run. A stack is any `terraform/` directory with `*.tf` files except `modules`. A stack whose backend is the management account (`registry`, `organization`) defaults to `mgmt`; `log-archive`, whose account has no ops profile, is planned directly with `scripts/ops/terraform-run.sh`. A stack with an `envs/` directory (`rds`) is planned with `-var-file=envs/<env>.tfvars` and, when it declares `aws_account_id`, the selected profile's `sso_account_id`; after `--` it refuses `-var-file`, and `-var` for a variable the settings file assigns or for `aws_account_id` (a per-run `-var` such as `apply_immediately` passes), and a missing settings file or account id is an ops error |

The CLI's own tests: `python3 -m unittest discover -s .claude/skills/ops/tests`.

Flags, log location and messages: `ops help`.
