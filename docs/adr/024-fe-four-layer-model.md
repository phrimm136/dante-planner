# 024 fe-four-layer-model
epic: none · pr: none

## Decisions
- @layers @naming (taste) — Four layers: `pages/<slice>` for routed domains, `shared/<concept>` for co-owned domain concepts, `components/` for the domain-free React kit, and `lib/` for domain-free non-React infrastructure. Directories are named for what code is *about*, never for how much it is reused. REJECTED: reuse-degree names such as common, util or misc — reuse changes silently while subject changes only when the domain does, so a bucket named for reuse always converges into a landfill.
- @ownership @fan-in (taste) — Ownership decides placement, not usage count: domain code with one owner lives in that owner, domain code with no single owner earns a *named* shared concept, and infrastructure stays in the domain-free layers regardless of how many consumers it has. REJECTED: fan-in-driven placement — fan-in is good evidence for domain code and pure noise for infrastructure, where a single-consumer utility still belongs in the infrastructure layer.
- @shared @headless (taste) — When every consumer has an equal claim, the component splits into a rendering core owned by the concept and a data adapter owned by each page. Zero feature imports in the core is the observable invariant of healthy sharing, and a variant flag appearing inside the core is the signal that it has become a chimera and should split back.
- @abstraction @refusal (taste) — Structure-varying code is copied; only data-varying code is parameterized. REJECTED: a generic page shell for the filter pages — a single-use abstraction buys indirection without reuse, and duplicated leaves are cheaper to read and delete than misdirection is to unwind.
- @naming @churn — The React kit keeps its directory name despite also containing hooks, because the path is pinned by third-party component configuration and by well over a hundred import sites. Nominal purity loses to three-digit-file churn.
- @boundaries @lint @gate — The structural rules run as a boundary-only flat lint configuration wired into CI. REJECTED: using the full type-checked configuration as the gate — it throws at load without project service configuration and surfaces four digits of latent findings, and a gate that does not execute is a comment rather than an architecture.

## Takeaway
- takeaway: the durable axis for a directory name is subject, because subject changes only when the domain does. Every name describing a property that drifts on its own — how shared, how generic, how common — schedules its own decay.
