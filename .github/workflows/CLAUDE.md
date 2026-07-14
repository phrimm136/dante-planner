# Workflows — Constraints

- `deploy-fleet.yml` is the ONLY live production deploy (multi-region k3s GitOps: arm64
  build → both regional ECRs → kustomize tag bump → ArgoCD sync).
- `deploy.yml` is SUPERSEDED break-glass for the retired single-EC2 host —
  `workflow_dispatch` only; never re-add a push trigger; delete once the EC2 is gone.
- The frontend ships via Cloudflare Pages from a plain `vite build` — there are no
  Workers and no wrangler configs in this repo; do not introduce them for deploy tasks.
- Workflow `run:` blocks must not interpolate untrusted PR input (title/body/branch
  names); use `github.sha` / `github.ref_name` style contexts only.
