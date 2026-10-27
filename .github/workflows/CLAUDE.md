# Workflows — Constraints

- `deploy-fleet.yml` is the ONLY live production deploy (multi-region k3s GitOps: arm64
  build → both regional ECRs → kustomize tag bump → ArgoCD sync).
- `deploy.yml` is SUPERSEDED break-glass for the retired single-EC2 host —
  `workflow_dispatch` only; never re-add a push trigger; delete once the EC2 is gone.
- The frontend ships via Cloudflare Pages from a plain `vite build` — there are no
  Workers and no wrangler configs in this repo; do not introduce them for deploy tasks.
  The Pages build command is `sh frontend/scripts/fetch-static.sh && yarn --cwd frontend build`,
  and `STATIC_TOKEN` is a Pages environment variable that holds a fine-grained PAT with read-only
  Contents on phrimm136/danteplanner-static only.
- `grafana-provision.yml` runs the three import scripts (`import-alert-rules.sh`,
  `import-app-alert-rules.sh`, `import-fleet-dashboard.sh`) on every push to `main` that touches
  `deploy/grafana/`; a change to those scripts or to `fleet-dashboard.json` lands by merging it,
  never by a hand-run. Every script it runs must be idempotent (upsert or overwrite), so
  `create-staleness-rules.sh` stays out until it upserts, and a change to it still needs a hand-run.
- Workflow `run:` blocks must not interpolate untrusted PR input (title/body/branch
  names); use `github.sha` / `github.ref_name` style contexts only.
