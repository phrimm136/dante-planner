# 549 ops-applies-only-a-plan-it-cleared-and-only-for-the-user
epic: none · pr: none · supersedes: 512 @tooling @ops

## Decisions
- @tooling @ops — Every repeated workstation read of production and every terraform plan runs through one CLI, `ops`, and the Bash hook refuses direct calls to the scripts it wraps and names the `ops` command. One `ops` command changes cloud state: `tf apply`, which applies a plan file that `tf plan` produced and cleared. The plan used to end in a line that ran the terraform runner directly, so an apply was neither logged nor redacted, and its output printed an account id; and nothing but reading the summary stood between a plan and an apply that replaced something nobody meant. Re-check: `.claude/skills/ops/bin/ops list` names `tf apply` as its only command that changes cloud state.
  REJECTED: keeping apply outside the CLI — the guard's verdict would then be advice, since the command that acts never reads it.
  REJECTED: write commands other than apply (Redis restart, secret writes, provisioning) — they run rarely and from runbooks, and each would need its own confirmation design.
  REJECTED: having CI call `ops` — CI authenticates differently and keeps calling the scripts by path.
  INVARIANT: `tf apply` is the only `ops` command that changes cloud state — a test pins every command name to read, plan or apply, so a new command fails it until it is classified.
  INVARIANT: a Bash-tool command that names the CLI or the terraform runner together with the standalone word `apply` or `destroy` is refused, whether or not the shell parser can read it — the hook searches the raw command text before it parses, and tests run the hook script on direct, indirect and unreadable spellings; the user applies by typing the printed line with `!`, which the tool hook does not see.
  NON-GOAL: a sandbox — an agent runs with the user's cloud session and could call the cloud API itself; the hook stops an ordinary or careless command, and a real barrier would be a separate, short-lived credential for apply that the everyday session does not hold.
  NON-GOAL: a stack whose account has no `ops` profile, and the bootstrap steps that run before a profile exists — they are planned and applied through the runner by hand, outside the guard.
- @ops @guard — `tf plan` names every change that can take something away: a replace, a destroy or a release from the state of any resource, and an update of an instance, an autoscaling group or a database instance, which can restart or roll what it manages. Each must be named in advance with `--expect <address>`, and each expected address must be such a change; otherwise the plan is blocked, no apply line is printed, and the verdict stored beside the plan file says so. Both region plans once carried the replacement of three instances beside an intended policy change, and the defence was a reader noticing three lines.
  REJECTED: blocking only on a replace or destroy of listed resource types — the list is the thing that goes stale; naming the intent per plan cannot.
  REJECTED: a policy engine over the plan JSON — a second language and a second tool for one rule a single maintainer applies.
  REJECTED: letting an expectation the plan does not carry pass — a stale `--expect` copied from an earlier command would then pre-approve that change the next time it appears.
  INVARIANT: `tf apply` starts terraform only for a plan file whose verdict file says clear and whose content hash matches the one judged — tests for a missing verdict file, a blocked verdict and a changed plan file, none of which start the runner.
  REJECTED: listing a release from the state without guarding it, because it takes nothing away in the account — a release takes the resource out of the stack's care together with the protection against deletion the stack declared for it, and the next plan of any stack that still names the resource can delete it.
  INVARIANT: a plan with a release that was not named is blocked and prints no apply line — tests run a recorded plan with five releases and an update with no names, with all five, and with a sixth that the plan does not hold.
  NON-GOAL: tracking that a plan was applied — terraform refuses a plan made against an older state.
- @ops @checks — A failed check block of a plan is printed in the summary as a warning line and changes neither the verdict nor the exit code. A check that could block an apply would have to be right every time; a warning that only the full log carried was read by no one.

## Takeaway
- takeaway: a guard binds only when the command that acts is the one that reads its verdict; and an intent stated before the plan turns reading a diff into comparing two lists.
