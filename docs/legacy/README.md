# Legacy

Retired work, frozen. Nothing here is maintained, and nothing outside this directory should point
into it.

| Directory | Holds |
|---|---|
| `tasks/` | The task directories of the retired process |
| `decisions/` | The decision index generated from them |
| `runbooks/` | Procedures whose operation no longer exists |

## The retired process

### What it was

Work was organised as numbered task directories, each carrying some combination of `requirements.md`,
`research.md`, `plan.md`, `spec.md`, `findings.md`, `review.md` and `results.md`. A generated index
collected the decisions those documents recorded. Skills drove the sequence: one to transcribe a
design into a spec, one to execute it phase by phase, one to close it out and regenerate the index.

### Why it was retired

The process kept design, execution tracking, and the decision record in one place, so none of the
three could be found by someone looking for that kind of thing. Design moved to `../rfcs/`, execution
to GitHub issues, and decisions to `../adr/`, where each has a lifecycle of its own.

### What was taken out

- **Decisions** worth keeping were harvested into `../adr/`, filtered by an admission test: hard to
  reverse, unintuitive, and a genuine trade-off. Roughly half of what was recorded here failed it,
  most commonly because the code already showed it or a later task had reversed it.
- **Live procedures** were promoted to `../runbooks/`.

Everything else stays for the record git already keeps. A decision found here and not in `../adr/`
was judged not worth carrying, or has been superseded — check `../adr/` before acting on anything in
this directory.

## Retired runbooks

A runbook lands here when the operation it describes can no longer be performed — not when it merely
goes unused. It is frozen on arrival and never corrected, so read it as a description of a machine
that is gone.

- `runbooks/oregon-cutover.md` — cutting production from the single hand-provisioned EC2 to the
  Oregon k3s fleet. The `terraform/oregon-edge` stack it allocates the ingress EIP from no longer
  exists, and the Cloudflare `api` A-record it swings is no longer the entry plane.
- `runbooks/prod-account-cutover.md` — moving production from the management account onto the
  fleet standing in the vended prod account. Production now runs in that account, so there is no
  management-account production left to move.
