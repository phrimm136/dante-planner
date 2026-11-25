# Workflows — Constraints

- `deploy-fleet.yml` is the ONLY live production deploy (multi-region k3s GitOps: arm64
  build → both regional ECRs → chart values tag bump → ArgoCD sync).
- `deploy.yml` is SUPERSEDED break-glass for the retired single-EC2 host —
  `workflow_dispatch` only; never re-add a push trigger; delete once the EC2 is gone.
- The frontend ships via Cloudflare Pages from a plain `vite build` — there are no
  Workers and no wrangler configs in this repo; do not introduce them for deploy tasks.
  The Pages build command is `sh frontend/scripts/fetch-static.sh && yarn --cwd frontend build`,
  and `STATIC_TOKEN` is a Pages environment variable that holds a fine-grained PAT with read-only
  Contents on phrimm136/danteplanner-static only.
- `grafana-provision.yml` runs the three import scripts (`import-alert-rules.sh`,
  `import-app-alert-rules.sh`, `import-fleet-dashboard.sh`) on every push to `main` that touches
  `deploy/grafana/` or the etcd snapshot interval (`terraform/modules/fleet/variables.tf`,
  `scripts/ops/lib/etcd-interval.sh`), which sets an alert threshold; a change to those scripts or to `fleet-dashboard.json` lands by merging it,
  never by a hand-run. Every script it runs must be idempotent (upsert or overwrite).
  `create-staleness-rules.sh` upserts but stays out, because it refuses to run before both clusters
  send metrics; a change to it needs a hand-run. The last step, `check-alert-rules.sh`, fails the
  run when a declared rule exists twice or a managed folder holds a rule no file declares.
- `dependabot-auto-merge.yml` enables rebase auto-merge on a Dependabot PR unless an update in it
  is a major, and withdraws it when anyone else pushes; `dependency-submission.yml` submits the
  backend's resolved Gradle graph on every push to `main`.
- In that graph a dependency is `runtime` when `runtimeClasspath` or `productionRuntimeClasspath`
  (the boot jar's classpath) resolves it, and `development` when only build tooling or test
  configurations do, so a Dependabot alert's scope tells whether the vulnerable code ships.
- Workflow `run:` blocks must not interpolate untrusted PR input (title/body/branch
  names); use `github.sha` / `github.ref_name` style contexts only.
- PR Gate runs on `pull_request` and `push` to main (ADR 517). A pull request runs the fast
  lanes, a push runs the full lanes (the planted-bug consistency matrix among them), and both skip
  a lane whose inputs did not change. A newer push
  cancels the running push run, and the push diff starts at the last green push run, so a burst of
  merges ends in one run. `deploy-fleet.yml` deploys only after a green push run, and
  `mutation.yml` runs the mutation ratchet cold once a week. A required check must be a job that
  reports on a pull request; a job that runs only on push reports skipped there.
- A `workflow_run` workflow runs with secrets and must gate its first job on
  `github.event.workflow_run.event` and `github.event.workflow_run.head_repository.full_name`:
  its `branches:` filter matches the triggering run's branch name, which a fork pull request can
  also carry, and a fork run of PR Gate ends green.
- The PR Gate e2e job builds the backend boot jar on the runner, and its rig image takes that jar
  through `JAR_STAGE=jar-prebuilt` and the `backend-jar` build context. `deploy-fleet.yml` does the
  same for the release image: its `build-jar` job runs Gradle and hands the boot jar to `build-push`
  as a workflow artifact. The jar has its own job because Gradle plugins and build scripts are
  third-party code, and `build-push` holds `id-token: write`, the production environment,
  `STATIC_DEPLOY_KEY` and an AWS role. `build-jar` only reads a Gradle cache, the one PR Gate's
  `test-backend` saves on a push to `main`: GitHub documents cache access on the default branch as
  read-only for a `workflow_run` run. Every other backend image build (staging-rehearsal,
  window-schema-decomposition, PR Gate `build-backend`, local `stack:up`) passes no `JAR_STAGE`,
  so it compiles from source.
  The e2e job restores the setup-java Gradle cache with `cache-read-only: true`: a cache key
  saves once, so a second writer that runs only bootJar would store a cache without the test
  dependencies that test-backend needs.
