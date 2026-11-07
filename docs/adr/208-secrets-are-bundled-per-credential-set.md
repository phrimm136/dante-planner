# 208 secrets-are-bundled-per-credential-set
epic: none · pr: none

## Decisions
- @secrets @layout — One Secrets Manager container per credential set: the keys that one
  consumer presents together and one script writes together live in one JSON object, and
  consumers extract the key they need (`remoteRef.property` in an ExternalSecret, `jq -r` in a
  script). Secrets Manager bills per container and again per replica, so the nineteen
  single-value containers of the replicated set cost twice nineteen; the credential-set
  boundary brings the set to eight containers, which is the rotation unit (one token change
  rewrites one container and refreshes only its consumers) and the IAM unit (a grant names the
  bundle, not a glob over a prefix). A value stays a plain string when its reader needs a plain
  string: the RDS master password is read by terraform as an ephemeral string at plan time.
  REJECTED: one container for everything — the app-node role would read the master password,
  which the grant enumerates patterns specifically to exclude, and terraform's plan-time read
  would pull the signing key into the provisioner's memory.
  REJECTED: one container per reader (app node, operator, provisioner) — fourteen keys written
  by six scripts in one object, and every writer here is a whole-value `put-secret-value`, so a
  writer that ran after another silently drops the other's keys; read-modify-write cannot be
  made safe because Secrets Manager has no conditional put.
  REJECTED: SSM Parameter Store standard tier (free) — no cross-region replication, and a region
  reading its secrets locally is the invariant the second region exists for; a writer-side
  double write re-implements replication by hand.
  REJECTED: leave as is — the saving is small, but every new secret would have copied the
  one-value shape and the cost grows with the set.
  INVARIANT: the staging account holds the same layout as prod, because the release rehearsal
  proves prod's secrets machinery on it — the one shared `secret_names` default in
  terraform/secrets and the staging-e2e writers that seed the same bundles.
  INVARIANT: each container has at most one writer script — unenforced; the provision scripts
  are listed by hand.
  NON-GOAL: read-modify-write in the provision scripts — a bundle with one writer keeps the
  whole-value write, and a second writer is a layout change, not a script change.
- @secrets @jwt — The JWT material (private key, public key, encryption key) is its own bundle
  and is not folded into the backend runtime configuration, although the same role reads both.
  The runtime configuration is injected whole into every backend container's process
  environment; the signing key is mounted as a file, and a key in the environment is readable
  by anything that can read the process. The two bundles differ by injection path, not by
  reader.
- @secrets @public — A public value is not stored in the secret store: the origin-pull CA
  certificate (the same for every customer) leaves Secrets Manager with its ExternalSecret, and
  the gateway's TLS option already tolerates its absence. When mutual TLS is restored, the CA is
  committed as a plain manifest.
  REJECTED: keep it for symmetry with the origin certificate — it pays two containers a month to
  protect nothing, and its presence suggested the value was secret.
- @secrets @migration — Consolidation is create-new, cut-over, retire-old, never an in-place
  rename: the new bundle is created with its replica attached before any consumer moves, every
  consumer (manifests in both regions, IAM patterns in both region stacks, scripts) is pointed at
  the bundle and observed synced, and only then does the old container leave terraform state by
  `state rm` (its `prevent_destroy` refuses a planned destroy) and get deleted with the seven-day
  recovery window as the rollback.
  INVARIANT: no consumer points at a container that lacks its replica — the Seoul SecretStore
  reads the local region only, so a bundle created without `--add-replica-regions` fails there
  at the first sync; the provisioning scripts create with the replica.
  NON-GOAL: a zero-downtime guarantee for the cut-over — ExternalSecrets refresh hourly and pods
  read their Secrets at start, so a consumer at most keeps the old value until its next refresh,
  and both values are identical during the window.

## Takeaway
- takeaway: a per-item price turns the item boundary into a design decision; draw it where
  rotation, authorization and writing already agree, and the count follows.
