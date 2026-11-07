# Consolidating the secrets into credential-set bundles

Moves the prod and staging accounts from nineteen Secrets Manager containers to eight: four new
JSON bundles replace fourteen single-value containers, and `danteplanner/origin-client-ca` is
deleted. The values do not change. Every step runs for **prod first, then staging**; the two
accounts share `terraform/secrets` and end with the same layout.

| Retired container | New bundle | Key |
|---|---|---|
| `danteplanner/jwt/rs256-private-key` | `danteplanner/jwt/material` | `rs256_private_key` |
| `danteplanner/jwt/rs256-public-key` | `danteplanner/jwt/material` | `rs256_public_key` |
| `danteplanner/jwt/encryption-key` | `danteplanner/jwt/material` | `encryption_key` |
| `danteplanner/grafana/loki-username` | `danteplanner/grafana/cloud` | `loki_username` |
| `danteplanner/grafana/loki-password` | `danteplanner/grafana/cloud` | `loki_password` |
| `danteplanner/grafana/remote-write-username` | `danteplanner/grafana/cloud` | `remote_write_username` |
| `danteplanner/grafana/remote-write-password` | `danteplanner/grafana/cloud` | `remote_write_password` |
| `danteplanner/grafana/observability-read-token` | `danteplanner/grafana/cloud` | `observability_read_token` |
| `danteplanner/mysqld-exporter/username` | `danteplanner/mysqld-exporter/dsn` | `username` |
| `danteplanner/mysqld-exporter/password` | `danteplanner/mysqld-exporter/dsn` | `password` |
| `danteplanner/mysqld-exporter/primary-endpoint` | `danteplanner/mysqld-exporter/dsn` | `primary_endpoint` |
| `danteplanner/mysqld-exporter/replica-endpoint` | `danteplanner/mysqld-exporter/dsn` | `replica_endpoint` |
| `danteplanner/rds/readonly-username` | `danteplanner/rds/readonly` | `username` |
| `danteplanner/rds/readonly-password` | `danteplanner/rds/readonly` | `password` |
| `danteplanner/origin-client-ca` | none (public CA, leaves the store) | |

Unchanged: `danteplanner/backend/runtime-config`, `danteplanner/rds/master-password`,
`danteplanner/cloudflare/tunnel-tokens`, `danteplanner/origin-tls`.

Prerequisites: AWS profiles `prod` and `staging`, `jq`, SSM access to both control planes, and a
checkout of the consolidation branch for steps 1 and 2. A worktree lacks the gitignored files, so
copy them from the main checkout first: `terraform/secrets/prod.tfvars`, `terraform/backend.prod.hcl`
and `terraform/backend.staging.hcl`. Terraform runs only through `scripts/ops/terraform-run.sh`.

## The ordering rules

> **A bundle exists with its Seoul replica before anything reads it or enrolls it.**

The Seoul SecretStore reads only the local region, and `terraform/secrets` adopts containers with
`import` blocks, which fail on a missing name. The staging rehearsal applies `terraform/secrets`
*before* it seeds any value, so staging's bundles must exist before the first rehearsal on the new
code.

> **Until step 4, every `terraform/secrets` plan carries the coexistence override.**

The merged `secret_names` lists only the eight-container layout. The fourteen old names are still
in state with `prevent_destroy`, so a plan without the override (step 2) fails with
`Instance cannot be destroyed`. The staging rehearsal reads its tfvars from SSM, so the override
goes there too.

> **`origin_client_ca` leaves state before the first plan on the new code.**

The resource block is gone, and `prevent_destroy` went with it: a plan would destroy the
container without an error. Prod sets `origin_client_ca = ""`, so prod state holds nothing; staging
sets the CA, so staging state very likely holds both addresses. Step 2 checks and removes them.

## 1. Create the bundles and copy the values

Each writer script's `--migrate` mode creates its bundle with the Seoul replica when it is absent,
reads the old containers and writes one JSON object. It prompts for nothing and prints no value.

```bash
export AWS_PROFILE=prod        # then repeat the whole step with AWS_PROFILE=staging
scripts/ops/provision/grafana-secrets.sh --migrate
scripts/ops/provision/mysqld-secrets.sh --migrate
scripts/ops/provision/rds-readonly-secrets.sh --migrate
```

The JWT material has no writer script in prod, and staging's writer (`staging-e2e-secrets.sh`)
needs the staging database, which exists only during a rehearsal; build the bundle by hand in both
accounts. Values travel on stdin and as JSON strings, so no value reaches argv and no trailing
newline is added or lost:

```bash
aws secretsmanager create-secret --region us-west-2 --name danteplanner/jwt/material \
  --add-replica-regions Region=ap-northeast-2 >/dev/null
bash -euo pipefail <<'EOF'
for k in rs256-private-key rs256-public-key encryption-key; do
  aws secretsmanager get-secret-value --region us-west-2 --secret-id "danteplanner/jwt/$k" --output json
done \
  | jq -cs '{rs256_private_key: .[0].SecretString, rs256_public_key: .[1].SecretString, encryption_key: .[2].SecretString}' \
  | aws secretsmanager put-secret-value --region us-west-2 --secret-id danteplanner/jwt/material \
      --secret-string file:///dev/stdin >/dev/null
EOF
```

Gate: each bundle has its replica `InSync`, and every key matches its old container byte for byte.
The check prints names and `match`/`DIFF` only, never a value:

```bash
for b in jwt/material grafana/cloud mysqld-exporter/dsn rds/readonly; do
  aws secretsmanager describe-secret --region us-west-2 --secret-id "danteplanner/$b" \
    --query '[Name, ReplicationStatus[0].Region, ReplicationStatus[0].Status]' --output text
done
bash -euo pipefail <<'EOF'
v() { aws secretsmanager get-secret-value --region us-west-2 --secret-id "danteplanner/$1" --output json | jq .SecretString; }
check() { # old-name bundle key
  old=$(v "$1"); new=$(v "$2" | jq -r . | jq ".$3")
  if [ -n "$old" ] && [ "$old" != "null" ] && [ "$old" = "$new" ]; then echo "match  $1"; else echo "DIFF   $1"; fi
}
check jwt/rs256-private-key               jwt/material        rs256_private_key
check jwt/rs256-public-key                jwt/material        rs256_public_key
check jwt/encryption-key                  jwt/material        encryption_key
check grafana/loki-username               grafana/cloud       loki_username
check grafana/loki-password               grafana/cloud       loki_password
check grafana/remote-write-username       grafana/cloud       remote_write_username
check grafana/remote-write-password       grafana/cloud       remote_write_password
check grafana/observability-read-token    grafana/cloud       observability_read_token
check mysqld-exporter/username            mysqld-exporter/dsn username
check mysqld-exporter/password            mysqld-exporter/dsn password
check mysqld-exporter/primary-endpoint    mysqld-exporter/dsn primary_endpoint
check mysqld-exporter/replica-endpoint    mysqld-exporter/dsn replica_endpoint
check rds/readonly-username               rds/readonly        username
check rds/readonly-password               rds/readonly        password
EOF
```

Expect four lines ending `ap-northeast-2  InSync` and fourteen `match`. A `DIFF` stops the run:
re-run that bundle's writer before going on.

## 2. Enroll the bundles with the coexistence override

Add this block to the account's `terraform/secrets` tfvars. It is the merged default plus the
fourteen old names, and it stays until step 4:

```hcl
secret_names = [
  "danteplanner/backend/runtime-config",
  "danteplanner/jwt/material",
  "danteplanner/grafana/cloud",
  "danteplanner/mysqld-exporter/dsn",
  "danteplanner/rds/readonly",
  "danteplanner/rds/master-password",
  "danteplanner/cloudflare/tunnel-tokens",
  "danteplanner/jwt/rs256-private-key",
  "danteplanner/jwt/rs256-public-key",
  "danteplanner/jwt/encryption-key",
  "danteplanner/grafana/loki-username",
  "danteplanner/grafana/loki-password",
  "danteplanner/grafana/remote-write-username",
  "danteplanner/grafana/remote-write-password",
  "danteplanner/grafana/observability-read-token",
  "danteplanner/mysqld-exporter/username",
  "danteplanner/mysqld-exporter/password",
  "danteplanner/mysqld-exporter/primary-endpoint",
  "danteplanner/mysqld-exporter/replica-endpoint",
  "danteplanner/rds/readonly-username",
  "danteplanner/rds/readonly-password",
]
```

In the same edit, delete the whole `origin_client_ca = ...` assignment (in staging it is a
multi-line heredoc). An undeclared variable in a var-file is only a warning, but the line no longer
means anything.

**Prod:**

```bash
export AWS_PROFILE=prod
scripts/ops/terraform-run.sh -chdir=terraform/secrets init -backend-config=../backend.prod.hcl -reconfigure
scripts/ops/terraform-run.sh -chdir=terraform/secrets state list | rg origin_client_ca   # expect nothing
scripts/ops/terraform-run.sh -chdir=terraform/secrets plan -var-file=prod.tfvars       # READ IT
scripts/ops/terraform-run.sh -chdir=terraform/secrets apply -var-file=prod.tfvars
```

**Staging:** the rehearsal workflow reads `/danteplanner/staging/tfvars/secrets` from SSM, but on
2026-11-07 the staging account held no SSM parameter at all and the only copy was the main
checkout's gitignored `terraform/secrets/staging.tfvars`. Use that file when the parameter is
absent (copy it into the worktree, edit, plan, apply, copy it back, delete the worktree copy);
when the parameter exists, fetch it, edit it as above, and write it back before planning:

```bash
export AWS_PROFILE=staging
aws ssm get-parameter --region us-west-2 --name /danteplanner/staging/tfvars/secrets \
  --with-decryption --query Parameter.Value --output text > terraform/secrets/staging.tfvars
"${EDITOR:-vi}" terraform/secrets/staging.tfvars        # add secret_names, delete origin_client_ca
aws ssm put-parameter --region us-west-2 --name /danteplanner/staging/tfvars/secrets \
  --type SecureString --overwrite --value file://terraform/secrets/staging.tfvars
scripts/ops/terraform-run.sh -chdir=terraform/secrets init -backend-config=../backend.staging.hcl -reconfigure
scripts/ops/terraform-run.sh -chdir=terraform/secrets state list | rg origin_client_ca
# for each address it lists:
scripts/ops/terraform-run.sh -chdir=terraform/secrets state rm \
  'aws_secretsmanager_secret.origin_client_ca[0]' 'aws_secretsmanager_secret_version.origin_client_ca[0]'
scripts/ops/terraform-run.sh -chdir=terraform/secrets plan -var-file=staging.tfvars    # READ IT
scripts/ops/terraform-run.sh -chdir=terraform/secrets apply -var-file=staging.tfvars
```

Gate, per account: the plan reads `4 to import, 0 to add, 4 to change, 0 to destroy`. The four
in-place changes only record `force_overwrite_replica_secret = false` and
`recovery_window_in_days = 30`, terraform-side defaults an import leaves empty; any other change
stops the run (see the header of `terraform/secrets/main.tf`). Staging's first apply also adds the
Seoul replica to every existing container (`+ replica { region = "ap-northeast-2" }`) and imports
`danteplanner/origin-tls`, because staging had never been applied with replicas; that is the
designed state, and the old containers go away in step 4.

## 3. Merge, cut over, and observe `Synced`

Precondition: the step 1 and step 2 gates have passed in both prod and staging. The change must
not reach `main` before then, neither by a direct merge nor by a dev-to-main promotion: ArgoCD
watches `main`, and the bundles' consumers would point at containers that do not exist yet. Keep
the change on its own branch until the precondition holds.

Merge the branch to `main`. ArgoCD syncs both regions, and every ExternalSecret moves to its bundle.
The IAM grants already applied are wider than the new ones (`danteplanner/jwt/*` covers
`danteplanner/jwt/material-*`), so the cut-over reads succeed before the region stacks change. The
`origin-client-ca` ExternalSecret is pruned (the root apps sync with `prune: true`).

Check every ExternalSecret in both prod clusters. The check runs on each control plane over SSM,
forces a refresh, and lists each ExternalSecret with its source and status:

```bash
export AWS_PROFILE=prod
synced() { # aws-region terraform-stack
  local id cmd
  scripts/ops/terraform-run.sh -chdir="terraform/$2" init -backend-config=../backend.prod.hcl -reconfigure >/dev/null
  id=$(scripts/ops/terraform-run.sh -chdir="terraform/$2" output -raw cp_instance_id)
  cmd=$(aws ssm send-command --region "$1" --instance-ids "$id" --document-name AWS-RunShellScript \
    --parameters 'commands=["k3s kubectl -n danteplanner annotate externalsecret --all force-sync=$(date +%s) --overwrite >/dev/null","sleep 30","k3s kubectl -n danteplanner get externalsecret -o custom-columns=NAME:.metadata.name,KEY:.spec.data[*].remoteRef.key,EXTRACT:.spec.dataFrom[*].extract.key,READY:.status.conditions[0].status,REASON:.status.conditions[0].reason --no-headers"]' \
    --query Command.CommandId --output text)
  aws ssm wait command-executed --region "$1" --command-id "$cmd" --instance-id "$id"
  aws ssm get-command-invocation --region "$1" --command-id "$cmd" --instance-id "$id" \
    --query StandardOutputContent --output text
}
synced us-west-2 oregon
synced ap-northeast-2 seoul
```

Gate: every row reads `True  SecretSynced`, the KEY and EXTRACT columns name only the eight-container
layout, and no `origin-client-ca` row is left. Then narrow the app-node grants to the bundles:

```bash
scripts/ops/terraform-run.sh -chdir=terraform/oregon plan -var-file=prod.tfvars    # READ IT: the app-node secrets policy updates in place, nothing else
scripts/ops/terraform-run.sh -chdir=terraform/oregon apply -var-file=prod.tfvars
scripts/ops/terraform-run.sh -chdir=terraform/seoul  plan -var-file=prod.tfvars
scripts/ops/terraform-run.sh -chdir=terraform/seoul  apply -var-file=prod.tfvars
synced us-west-2 oregon
synced ap-northeast-2 seoul
```

The gate is the same, now proving the narrowed grants.

**Staging** has no standing clusters: its proof is one rehearsal run on `main` after the merge
(`Staging rehearsal (full 1:1 e2e)`, dispatched from the Actions tab). It applies
`terraform/secrets` with the SSM override, seeds the bundles through `staging-e2e-secrets.sh`,
and runs the e2e suites, whose auth setup reads `danteplanner/jwt/material`. Gate: the run passes.

## The coexistence window and its removal condition

Between the merge and step 4, the old and new containers both exist with identical values; nothing
reads the old ones. The window closes when **both** hold:

- every ExternalSecret in both prod clusters reports `SecretSynced` against the bundles after the
  region stacks applied (step 3's second check), and
- `staging-rehearsal.yml` has passed once on `main` with the new layout.

During the window, `scripts/ops/provision/copy-secrets-across-accounts.sh` copies every
`danteplanner/*` name by prefix, old and new alike. Containers scheduled for deletion in step 4 are
not listed by `list-secrets`, so it stops copying them after the window.

## 4. Retire the old containers

Per account, prod first. Remove the `secret_names` block from the tfvars (staging: fetch, edit and
`put-parameter` back as in step 2), then forget the old names in state, prove the plan is empty, and
delete them with the seven-day recovery window. A replicated primary cannot be deleted, so the
replica goes first.

```bash
export AWS_PROFILE=prod        # staging: AWS_PROFILE=staging, backend.staging.hcl, staging.tfvars
scripts/ops/terraform-run.sh -chdir=terraform/secrets init -backend-config=../backend.prod.hcl -reconfigure
bash -euo pipefail <<'EOF'
old=(
  danteplanner/jwt/rs256-private-key danteplanner/jwt/rs256-public-key danteplanner/jwt/encryption-key
  danteplanner/grafana/loki-username danteplanner/grafana/loki-password
  danteplanner/grafana/remote-write-username danteplanner/grafana/remote-write-password
  danteplanner/grafana/observability-read-token
  danteplanner/mysqld-exporter/username danteplanner/mysqld-exporter/password
  danteplanner/mysqld-exporter/primary-endpoint danteplanner/mysqld-exporter/replica-endpoint
  danteplanner/rds/readonly-username danteplanner/rds/readonly-password
)
for n in "${old[@]}"; do
  scripts/ops/terraform-run.sh -chdir=terraform/secrets state rm "aws_secretsmanager_secret.replicated[\"$n\"]"
done
EOF
scripts/ops/terraform-run.sh -chdir=terraform/secrets plan -var-file=prod.tfvars       # expect: No changes.
```

Only after `No changes.`:

```bash
bash -euo pipefail <<'EOF'
old=(
  danteplanner/jwt/rs256-private-key danteplanner/jwt/rs256-public-key danteplanner/jwt/encryption-key
  danteplanner/grafana/loki-username danteplanner/grafana/loki-password
  danteplanner/grafana/remote-write-username danteplanner/grafana/remote-write-password
  danteplanner/grafana/observability-read-token
  danteplanner/mysqld-exporter/username danteplanner/mysqld-exporter/password
  danteplanner/mysqld-exporter/primary-endpoint danteplanner/mysqld-exporter/replica-endpoint
  danteplanner/rds/readonly-username danteplanner/rds/readonly-password
)
for n in "${old[@]}"; do
  aws secretsmanager remove-regions-from-replication --region us-west-2 --secret-id "$n" \
    --remove-replica-regions ap-northeast-2 >/dev/null
  aws secretsmanager delete-secret --region us-west-2 --secret-id "$n" --recovery-window-in-days 7 \
    --query '[Name, DeletionDate]' --output text
done
EOF
```

**`origin-client-ca`** is not in the replicated set: prod's copy was created outside terraform by
the account move, and staging's left state in step 2. Retire it by its own path:

```bash
aws secretsmanager describe-secret --region us-west-2 --secret-id danteplanner/origin-client-ca \
  --query '[Name, ReplicationStatus[0].Region]' --output text      # absent: nothing to do
aws secretsmanager remove-regions-from-replication --region us-west-2 \
  --secret-id danteplanner/origin-client-ca --remove-replica-regions ap-northeast-2 >/dev/null   # only if a replica is listed
aws secretsmanager delete-secret --region us-west-2 --secret-id danteplanner/origin-client-ca \
  --recovery-window-in-days 7 --query '[Name, DeletionDate]' --output text
```

Gate, per account: the live set is the eight containers (staging also keeps
`danteplanner/staging/e2e-endpoints`, which this change does not touch), and the fifteen retired
names carry a deletion date:

```bash
aws secretsmanager list-secrets --region us-west-2 --filters Key=name,Values=danteplanner/ \
  --query 'SecretList[].Name' --output text | tr '\t' '\n' | sort
aws secretsmanager list-secrets --region us-west-2 --include-planned-deletion \
  --filters Key=name,Values=danteplanner/ \
  --query 'SecretList[?DeletedDate!=`null`].[Name, DeletedDate]' --output text
```

## Rollback

**Before step 4** (the old containers are live, enrolled and identical): revert the merge on `main`
(`git revert -m 1 <merge-sha>`), then apply `terraform/oregon` and `terraform/seoul` at once — the
narrowed grants do not cover the old names, and ESO keeps the last synced Secret until the grant
returns. Re-run step 3's `synced` check. The bundles and the tfvars override can stay; the override
keeps `terraform/secrets` plans clean.

**Within seven days after step 4**, per retired name and account:

```bash
aws secretsmanager restore-secret --region us-west-2 --secret-id "<name>"
aws secretsmanager replicate-secret-to-regions --region us-west-2 --secret-id "<name>" \
  --add-replica-regions Region=ap-northeast-2      # restore-secret does not bring the replica back
```

Then put the step 2 override back into the tfvars and apply `terraform/secrets` (its import blocks
re-adopt the restored names), and roll back the manifests as above. After seven days the old names
are gone; a rollback then means re-creating them with the values read from the bundles.
