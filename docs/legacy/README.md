# Legacy

A retired process, frozen. Nothing here is maintained, and nothing outside this directory should
point into it.

## What it was

Work was organised as numbered task directories, each carrying some combination of `requirements.md`,
`research.md`, `plan.md`, `spec.md`, `findings.md`, `review.md` and `results.md`. A generated index
collected the decisions those documents recorded. Skills drove the sequence: one to transcribe a
design into a spec, one to execute it phase by phase, one to close it out and regenerate the index.

## Why it was retired

The process kept design, execution tracking, and the decision record in one place, so none of the
three could be found by someone looking for that kind of thing. Design moved to `../rfcs/`, execution
to GitHub issues, and decisions to `../adr/`, where each has a lifecycle of its own.

## What was taken out

- **Decisions** worth keeping were harvested into `../adr/`, filtered by an admission test: hard to
  reverse, unintuitive, and a genuine trade-off. Roughly half of what was recorded here failed it,
  most commonly because the code already showed it or a later task had reversed it.
- **Live procedures** were promoted to `../runbooks/`.

Everything else stays for the record git already keeps. A decision found here and not in `../adr/`
was judged not worth carrying, or has been superseded — check `../adr/` before acting on anything in
this directory.
