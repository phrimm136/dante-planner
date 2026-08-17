# 028 toolchain-gates
epic: none · pr: none

## Decisions
- @lint @type-aware — Type-aware linting is adopted from day one rather than as a later upgrade. REJECTED: shipping the shallow gate first — a gate that hides a known-real defect class (unawaited promises in the logout, account-deletion and save flows) defeats the reason for having a gate, and the measured cost of depth turned out to be a fraction of the feared one.
- @lint @scope — Parity with the previous strict preset is an explicit non-goal; the gate runs the default type-aware categories, and individual stylistic rules are promoted later, deliberately, one at a time. REJECTED: chasing wholesale preset parity — the old strict configuration never actually executed, so it is not a baseline being regressed from, and adopting it wholesale re-inflates the debt to four digits of mostly-cosmetic findings.
- @ci @parity @ratchet — CI runs the same command developers run locally, and a gate flips to blocking only once its baseline is at zero. REJECTED: gating on a red baseline — a permanently-failing gate gets muted, after which it reports nothing. REJECTED: a CI-only invocation — local and CI divergence means the gate is discovered rather than enforced.
- @static-analysis @ordering — The static-analysis ratchet lands after the standardizing sweeps and before repackaging, with an empty suppression baseline. That ordering is what makes an empty baseline feasible at all: the sweeps have just made the idioms uniform, so there is nothing left to suppress.
- @bundler @compiler @plugin — The React plugin variant that retains a Babel pipeline is kept, because the compiler is a Babel plugin. REJECTED: the faster native variant — it has no Babel pipeline, so adopting it silently drops the compiler and with it the memoization the codebase relies on.
- @bundler @entry-gate — A bundler major upgrade is gated on proving the build on a constrained-CPU runner before merge, with a documented mitigation ladder if it hangs. This bundler already deadlocked two-core CI runners once and had to be rolled back, so the gate encodes a failure that has actually happened here rather than a hypothetical one.

## Takeaway
- takeaway: a quality gate's value is bounded by the weakest of three things — whether it executes, whether it is deep enough to see the defect class you care about, and whether anyone still reads its output. A red baseline fails the third quietly, which makes it the most expensive of the three to get wrong.
