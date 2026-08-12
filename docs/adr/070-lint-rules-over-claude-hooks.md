# 070 lint-rules-over-claude-hooks

## Decisions

- @lint @conventions — A rule that guards the codebase itself — a banned construct, a
  required idiom — is declared in the build's linter, checkstyle for the backend and oxlint
  for the frontend, and never in the forbidden-patterns hook; that hook keeps only rules
  about Claude's own working process, which no human contributor executes. A hook binds
  Claude sessions alone, so a codebase ban placed there silently stops guarding the moment a
  human edits the file, and CI never sees it at all. The raw-Redis-construction ban is the
  first rule placed under this shape.
  REJECTED: leaving codebase bans in the hook — the ban is invisible to human contributors
  and to CI, so it holds exactly as long as nobody but Claude touches the code, and the gap
  opens without a signal.
  REJECTED: declaring the rule in both the hook and the linter — two declarations of one ban
  drift, and the hook buys nothing the linter does not already deliver before a commit
  lands, for Claude as much as for anyone else.
- @lint @conventions — Corollary: a ban is worth stating only with its exceptions written as
  id-scoped suppressions that name the sources they excuse, so the ban keeps full width
  everywhere else. What the hook still declares over backend sources is therefore
  outstanding against this rule rather than exempt from it.

## Takeaway

- takeaway: an enforcement point that binds one contributor is not enforcement. A rule
  belongs on the path every change takes to the branch, and the cost of moving it there —
  config plus a suppression list — is the price of it applying to everyone.
