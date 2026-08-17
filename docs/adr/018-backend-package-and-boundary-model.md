# 018 backend-package-and-boundary-model
epic: none · pr: none

## Decisions
- @packages @feature — Package by feature, with layer subpackages *inside* each feature rather than at the top. REJECTED: flat top-level layer packages — they scatter a feature across the tree. The layers survive internally on purpose: the existing ArchUnit layer matchers keep working unchanged, so the repackaging does not have to rewrite the rules that police it.
- @ownership @shared (taste) — Code with one owner lives in that owner and is consumed across features through declared edges; only genuinely co-owned, stable code enters the shared package, which is a dependency sink that never imports feature code, enforced by ArchUnit. REJECTED: usage-count-driven placement — fan-in is evidence for domain code and noise for infrastructure, and a bucket named for reuse rather than for subject always becomes a landfill. The enforcement is what physically prevents that here.
- @cross-feature @archunit @allowlist — Cross-feature repository access is kept and frozen rather than banned: the current edge set is encoded as an explicit allowlist. REJECTED: banning cross-feature repository access outright — it forces service-to-service calls that reintroduce cycles. The allowlist makes a new edge a declared change rather than something that accretes unnoticed.
- @tests @tiers — Controller tests keep full context plus MockMvc. REJECTED: converting them to sliced web and persistence test annotations — the chartered tier policy assigns bean wiring and JSON wire contracts to exactly this tier, and slicing them away leaves nothing asserting the wiring.

## Takeaway
- takeaway: a boundary that is not executed by a test is a naming convention. What makes the shared package stay a sink, rather than drifting into a junk drawer, is that an import in the wrong direction fails the build.
