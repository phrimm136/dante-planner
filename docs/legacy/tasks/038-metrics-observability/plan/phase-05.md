# Phase 05: Deploy-marker annotations

- Kind: live-only (verification is a real production rollout)
  - Spec-defect footnote: Done When item 7 tags this `live-only`, yet the deliverable is a workflow
    CODE edit to `deploy-fleet.yml`. Honored as live-only because the only proof is a live rollout;
    the edit itself has no local render/red-green home. Recorded as a derivation, not invented scope.
- Files:
  - `.github/workflows/deploy-fleet.yml` (modify: add a marker POST step after successful rollout)
- Tests: none local; verification is the next production rollout
- External contract: after a successful rollout the workflow POSTs to the Grafana annotations HTTP
  API, tagged with region + image SHA; the Grafana API token comes from a GitHub Actions secret
  (never in-repo, INV11).
- Mechanics sections: `mechanics.md` §4 (Deploy markers row); `mechanics.md` §5 (token provisioning).
- Implementation methods:
  - The POST is the last step of the `settle-down` job, after `rollout status` succeeds for both
    clusters — exemplar rollout-completion point `deploy-fleet.yml:251-357` (`settle-down`).
  - Do not interpolate untrusted PR input into the `run:` block; use `github.sha` / `github.ref_name`
    contexts only (`.github/workflows/CLAUDE.md`).
- Considerations:
  - `requirements.md` Decisions (deploy markers): both motivating postmortems were deploy-window
    incidents — the marker correlates the alert timeline with the rollout that caused it.
  - `mechanics.md` §5: the Grafana Cloud API token (owner: user) must exist as a GH Actions secret
    BEFORE this workflow change is exercised.
- Depends on: none
- Verify: a deploy-marker annotation appears in Grafana on the next production rollout (Done When
  item 7); record in the phase verification doc.
