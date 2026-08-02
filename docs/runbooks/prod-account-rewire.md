# Rewiring production from the management account to danteplanner-prod

Prepares the vended `danteplanner-prod` account to receive production: its state bucket, its
CI identity, copies of every secret value, and copies of the terraform state history. Nothing
in the management account is modified or removed at any step — every copy leaves the original
standing as the rollback path. Moving the *resources* (RDS, the fleets, the registries) is the
prod-migration phase proper and is not this runbook.

Prerequisites: the account exists under Workloads/Prod with AdministratorAccess assigned;
AWS profiles for the management account and the prod account are configured locally.

## 1. State bucket (terraform/state-backend)

Local-state stack, one workspace per account:

```bash
terraform -chdir=terraform/state-backend workspace new danteplanner-prod
# Create terraform/state-backend/danteplanner-prod.tfvars (gitignored, like its siblings):
#   aws_account_id  = "<prod account id>"
#   assume_role_arn = ""        # empty when the profile's credentials already land in prod
AWS_PROFILE=<prod> terraform -chdir=terraform/state-backend apply -var-file=danteplanner-prod.tfvars
```

## 2. Backend config for the prod account

```bash
terraform -chdir=terraform/state-backend output -raw tf_state_bucket
# Copy terraform/backend.hcl.example to terraform/backend.prod.hcl (gitignored) and set that
# bucket. Add assume_role there too if the ambient credentials are not the prod account's —
# a backend resolves credentials before any provider exists.
```

## 3. CI identity (terraform/iam-bootstrap)

Same local-state, workspace-per-account shape. Creates the GitHub OIDC provider and the
`prod-provisioner` role inside the prod account. Its trust has exactly two doors: the
AdministratorAccess permission-set role (a laptop session) and the OIDC subject scoped to the
GitHub `production` environment.

```bash
terraform -chdir=terraform/iam-bootstrap workspace new danteplanner-prod
AWS_PROFILE=<prod> terraform -chdir=terraform/iam-bootstrap apply -var-file=danteplanner-prod.tfvars
terraform -chdir=terraform/iam-bootstrap output -raw provisioner_role_arn
```

Create the GitHub `production` environment before any workflow uses the role: the subject
names it, so assumption from a job outside it is denied. Nothing in GitHub changes yet —
the role ARN goes into `AWS_PROVISIONER_ROLE_ARN` at cutover.

Verify the trust wiring without deploying anything:

```bash
AWS_PROFILE=<prod> aws sts assume-role \
  --role-arn "$(terraform -chdir=terraform/iam-bootstrap output -raw provisioner_role_arn)" \
  --role-session-name wiring-check --query 'Credentials.Expiration'
```

## 4. Copy secret values

Dry-run first; the plan lists every `danteplanner/*` name with its replica regions and
whether the destination would create or overwrite:

```bash
SRC_PROFILE=<management> DST_PROFILE=<prod> \
  scripts/ops/provision/copy-secrets-across-accounts.sh
SRC_PROFILE=<management> DST_PROFILE=<prod> \
  scripts/ops/provision/copy-secrets-across-accounts.sh --execute
```

Verify: pick one secret and compare a checksum of its value between accounts.

## 5. Copy state history

```bash
SRC_PROFILE=<management> DST_PROFILE=<prod> \
  scripts/ops/provision/copy-state-across-accounts.sh terraform/backend.hcl terraform/backend.prod.hcl
# then re-run with --execute
```

For the stacks whose resources live in the management account (rds, oregon, seoul, secrets)
this copy is history, not a re-homing: the destination account cannot manage another
account's resources, and those stacks get fresh applies in prod on their migration day.

## 6. Re-home the cloudflare stack

The one stack whose resources live outside AWS entirely, so its state copy IS its migration:

```bash
AWS_PROFILE=<prod> terraform -chdir=terraform/cloudflare init -backend-config=../backend.prod.hcl -reconfigure
AWS_PROFILE=<prod> terraform -chdir=terraform/cloudflare plan -var-file=terraform.tfvars
```

Expect one known, benign drift and nothing else: state written before secrets were driven out
of it still carries the tunnel secrets, so the plan destroys the `random_password` generators
(state-only, no API call) and updates each tunnel in place to drop the stored attribute.

Apply it BEFORE the accelerator-to-tunnel cutover, never after: if the provider re-mints a
tunnel secret, doing so while no connector holds a token costs nothing, while the same change
against live connectors is an origin outage. Re-store the tokens immediately afterwards
(`scripts/ops/provision/cloudflare-tunnel-secrets.sh`) so the cutover finds current values.

Anything beyond that — a destroyed Cloudflare resource, a changed hostname, a pool or monitor
disappearing — means the copied state and the live resources disagree. Stop and diff.

## 7. Deferred to cutover (checklist, not steps)

- GitHub secrets: `AWS_PROVISIONER_ROLE_ARN` and `AWS_ACCOUNT_ID` flip to the prod account's
  values when the fleets move; the staging role is untouched.
- ECR: repositories and the cross-region replication re-create in prod; `deploy-fleet.yml`
  resolves registries from the account secret, so the workflow itself does not change.
- RDS: snapshot-share-and-restore into prod, rehearsed first (the staging rehearsal workflow
  is the drill ground; its rollback is the management copy this runbook preserved).
- Fleets: fresh `oregon`/`seoul` applies against `backend.prod.hcl`; ESO follows the fleet
  and reads the secrets copied in step 4.
- Identity Center session profile: consider PowerUserAccess for daily prod work once
  workloads land, keeping AdministratorAccess for provisioning sessions.

## When this is done

Production running in the prod account, with the management account holding no workload,
retires this runbook: delete it, and delete the management account's state bucket and secrets
rather than leaving a second copy of every credential standing. The copy scripts are generic
and outlive it; the rollback copies are the only reason to keep the origin account intact, and
that reason expires the moment the migration is accepted.
