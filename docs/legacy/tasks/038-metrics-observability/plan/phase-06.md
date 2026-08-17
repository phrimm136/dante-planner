# Phase 06: Handoff amendment

- Kind: local-tdd (grep-verifiable doc edit)
  - Spec-defect footnote: Done When item 8 tags this `local-tdd` (red/green), but a doc amendment has
    no test pipeline. Honored as local-tdd with a grep-based Verify; recorded as a derivation.
- Files:
  - `docs/tasks/038-metrics-observability/handoff.md` (modify)
- Tests: grep assertions on the amended sections
- External contract: `handoff.md` reflects the amended design — KSM object-state layer, Prometheus
  prerequisites, gap-cluster coverage, and the §G mysqld_exporter refinement folded into the
  selected-metrics section; alerts #7–9 + the staleness meta + the Seoul replica rule folded into the
  alerts section; and the "deploys ride dev" line corrected to `main`.
- Mechanics sections: none (this phase carries no new mechanics; it transcribes settled design).
- Implementation methods: none.
- Considerations:
  - `requirements.md` Decisions (branch correction): the live GitOps branch is `main`; the handoff's
    dev claim is wrong and must be corrected (`deploy/CLAUDE.md` already states ArgoCD syncs from
    `main` — the handoff is the sole stale copy). Amend `handoff.md:36-38` and the alerts list `:137`.
  - `requirements.md` Description item 6 scopes exactly which sections change; do not restate
    `mechanics.md` prose — reference it.
- Depends on: none
- Verify: grep `handoff.md` shows `main` (not `dev`) for the GitOps branch, the new selected-metrics
  subsections present, and alerts #7–9 / meta / Seoul listed.
