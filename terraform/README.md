# Terraform stacks

Ten root modules, one shared child module (`modules/fleet`). Each stack owns a blast radius: a
mistake in one cannot destroy another's resources, and each applies independently.

Four of them are **account-level** and run once per account rather than once: `state-backend` and
`iam-bootstrap` build what any other stack in that account needs, `organization` and `registry`
apply only in the account that owns them.

That isolation costs the thing this file exists to supply. **Terraform orders resources inside a
stack; it cannot order the stacks.** Only `terraform_remote_state` reads are visible to it, and
several real dependencies are not of that shape.

## What each stack owns

| Stack | Owns | Required inputs |
|---|---|---|
| `organization` | The organization, its unit hierarchy, member accounts, guardrail policies, identity-centre permission sets and assignments, the organization trail | `aws_account_id`, `organization_id`, `operator` |
| `state-backend` | One account's Terraform state bucket, and nothing else | `aws_account_id` |
| `iam-bootstrap` | The GitHub OIDC provider and the provisioning role, in an account that runs a fleet | `trusted_admin_principal_arns`, `aws_account_id` |
| `log-archive` | The organization trail's destination bucket and its policy | `aws_account_id`, `management_account_id`, `organization_id` |
| `registry` | Who may pull the backend image, and how many tagged images its repositories and their cross-region replicas keep; not the repositories themselves | `aws_account_id`, `organization_id` |
| `secrets` | Secrets Manager **containers** and their cross-region replicas — never the values | `aws_account_id` |
| `rds` | The MySQL primary, its parameter group, subnet group and security group | `engine_version` (in `envs/<env>.tfvars`), `aws_account_id` (passed by `ops tf plan`) |
| `oregon` | The primary k3s fleet (`modules/fleet`), peered to the RDS VPC | `ingress_allowed_cidrs`, `rds_vpc_id`, `aws_account_id` |
| `seoul` | The secondary fleet, the cross-region RDS read replica, cross-region peering | `rds_vpc_id`, `ingress_allowed_cidrs`, `aws_account_id` |
| `cloudflare` | Tunnels, load balancer, pools, health monitor | `cloudflare_api_token`, `account_id`, `zone_id` |

## State

State lives in the account whose resources it describes, so the bucket count tracks the account
count. Every stack, `state-backend` and `iam-bootstrap` included, declares a partial
`backend "s3"` and takes the bucket at init time; the backend file, not a workspace, chooses the
account:

```bash
terraform -chdir=terraform/state-backend output -raw tf_state_bucket   # -> a backend config file
terraform -chdir=terraform/<stack> init -backend-config=../backend.<account>.hcl
```

Those config files are gitignored because a bucket name carries an account id; copy
`backend.account.hcl.example`. One per account, not one shared — and a stack reaching into another account
needs `assume_role` in **that file** as well as in its provider, because a backend resolves
credentials before any provider exists and cannot use theirs.

Local state is used once per account: the first apply of the stack that creates the account's
bucket, before that bucket exists. That apply runs with a `backend_override.tf` holding
`terraform { backend "local" {} }`. `terraform/.gitignore` ignores override files, so git never
shows one left behind: delete it once the apply is done, before the move, whose init against the
backend file fails while the override still selects the local backend. Then
`scripts/ops/provision/bootstrap-state-to-s3.sh` moves the state into the new bucket, at
`<stack>/terraform.tfstate`, and verifies it by pulling the bucket's state back and comparing it
with the local copy by resources and outputs only: terraform stores a push into an empty S3 key
under a new lineage at serial 1, so the bucket's lineage and serial never match the local file's.
It then plans with the account's tfvars as information: a change in that plan is drift between
the code and the account that existed before the move, not damage done by it, while a plan that
fails stops the move unverified.

Locking is `use_lockfile = true` (S3-native, no DynamoDB table), and every stack sets
`workspace_key_prefix = "env"` — including the `terraform_remote_state` data sources, so a
non-default workspace reads its own peers rather than the default workspace's.

## Apply order

Edges Terraform can see are `terraform_remote_state` reads. Edges it cannot see are marked.

```
organization                                 (management account: units, members, guardrails, trail)
   │
   ├─► state-backend  (per account)          (invisible edge: every backend below needs its bucket)
   │        │
   │        └─► iam-bootstrap (per account)  (the provisioning role that applies the rest)
   │
   ├─► log-archive                           (invisible edge: the trail's destination, in its own account)
   └─► registry                              (invisible edge: fleets pull before they can run)

iam-bootstrap
   │
   ├─► secrets ──────────────► rds          (invisible edge: rds reads a secret secrets enrolls)
   ├─► oregon
   └─► cloudflare
              │
            seoul                            (reads oregon + rds state)
```

`secrets`, `oregon` and `cloudflare` are independent of one another and can be applied in any order,
or concurrently. The account-level stacks run once per account rather than once, and an account is
usable only after both of its bootstrap stacks have.

## The edges Terraform cannot enforce

**`rds` reads a secret at plan time.** `data.aws_secretsmanager_secret_version.master_password`
resolves the container that `secrets` enrolls and `scripts/ops/provision/rds-master-password-secret.sh`
seeds. Apply `rds` first and the plan fails on a missing secret, with an error that does not name the
cause.

**`secrets` creates containers; the provision scripts inject values.** Applying `secrets` alone
leaves every container empty, and a pod that mounts an empty secret starts and then misbehaves rather
than failing loudly. One script per credential family under `scripts/ops/provision/`.

**`cloudflare-tunnel-secrets.sh` runs after `terraform/cloudflare`,** because the tunnel tokens it
injects are that stack's outputs. So the credential flow is not a straight line: `cloudflare` →
script → `secrets` values → pods.

**The organization trail's destination is built by a different stack, in a different account.**
`organization` computes the bucket name from the account it vended rather than reading it, so a
mismatch surfaces as a failed trail creation rather than a trail quietly writing nowhere. Apply
`log-archive` first.

**`registry` grants the pull; the node role provides the token.** A repository policy cannot grant
`ecr:GetAuthorizationToken`, which is registry-wide, so a fleet whose node role lacks it authenticates
nowhere and one whose account lacks the policy authenticates and is then refused. Both halves are
required and they live in different stacks.

## What must already exist

The RDS VPC comes from one of two places. With `create_vpc = true` the `rds` stack creates the VPC
and its subnets (`rds/network.tf`), which both tracked settings files choose. With `create_vpc = false`
the stack takes `vpc_id` and `db_subnet_ids` of a VPC that no stack manages. Both fleets take
`rds_vpc_id` as an input in either case, so `rds` is applied before them.

A stack with an `envs/` directory (today `rds`) keeps its settings tracked in `envs/<env>.tfvars`,
one file per environment it is applied in, holding no account id, ARN, hostname or other
account-bound value. `ops tf plan <stack>` passes the file of the env it plans under and, when the
stack declares `aws_account_id`, the selected profile's `sso_account_id`. For such a stack it refuses
`-var-file`, and `-var` for a variable the settings file assigns or for `aws_account_id`, so each
variable has one source; a `-var` for any other variable, a per-run value, passes.

Every other `*.tfvars` is gitignored. Each other stack ships a tracked `environment.tfvars.example`
or `account.tfvars.example`; the auto-loaded name `terraform.tfvars` is not used anywhere.

## From scratch

Once per organization, from the management account:

1. Run `scripts/ops/provision/org-preflight.sh`, then apply `organization`. Creating an organization
   converts the calling account into the management account with no path back.
2. Enable IAM Identity Center in the console — no API does — then re-apply so the permission sets and
   assignments land.
3. Apply `log-archive` and `registry`, and verify with `scripts/ops/provision/org-verify.sh`.

Once per account, with credentials for that account:

4. Apply `state-backend` on local state, through a `backend_override.tf` (see State). No
   dependencies. While `terraform/state-backend/imports.tf` exists, this plan fails with
   "Cannot import non-existent remote object": that file adopts the management account's
   existing bucket (see `docs/debt.md`). Delete it in this account's working copy for this
   apply only, and restore it with `git checkout -- terraform/state-backend/imports.tf` after.
5. Write `terraform/backend.<account>.hcl` from its `tf_state_bucket` output.
6. Delete the override (gitignored, so `git status` will not remind you) and move that state into
   the new bucket:
   `scripts/ops/provision/bootstrap-state-to-s3.sh state-backend terraform/backend.<account>.hcl terraform/state-backend/<account>.tfvars terraform/state-backend/terraform.tfstate`,
   then the same script with `--remove-local state-backend terraform/state-backend/terraform.tfstate`
   once it prints `verified:`.
7. Apply `iam-bootstrap` for any account that runs a fleet, initialized against the same backend
   file, giving it the provisioning role that applies everything below.

Then, per environment:

8. Apply `secrets`, and run the `scripts/ops/provision/` scripts that seed values.
9. Apply `rds`, `oregon` and `cloudflare`.
10. Run `cloudflare-tunnel-secrets.sh` now that the tunnel tokens exist.
11. Apply `seoul`.

This provisions infrastructure, not a running system: a fresh `rds` is an empty schema. Data arrives
from a dump, and the schema from Flyway on first pod boot.

## Guards

`allowed_account_ids = [var.aws_account_id]` on every AWS provider fails the plan when the resolved
credentials belong to another account — the guard a workspace does not provide, since workspaces
isolate state and nothing else.

`aws_db_instance.this` and the prod secrets carry `prevent_destroy` alongside RDS
`deletion_protection`, so a destroy or a replacement errors instead of deleting data. The fleet
stacks deliberately carry neither, so their destroy-and-rebuild cycle keeps working.

`allowed_account_ids` covers only one direction: credentials that disagree with the `aws_account_id`
passed in. For a stack with `envs/` that value is the selected profile's `sso_account_id` (in CI,
the caller's own account), the same profile the credentials come from, so the check there catches
only a config entry naming the wrong account. The reverse — credentials agreeing with that value
while the directory is initialized against
another account's state bucket — reaches a plan unchallenged, because the backend lives in
`.terraform/terraform.tfstate` and no command names it. `scripts/ops/lib/terraform-guard.sh`
compares the two, reading the account id out of the `<prefix>-tfstate-<account_id>` bucket name,
and also refuses a directory holding an auto-loaded `terraform.tfvars`.

**Terraform runs through `scripts/ops/terraform-run.sh`, never the raw binary; plans run as
`ops tf plan` and the user applies them with the `! … ops tf apply <plan file>` line it prints, both
of which call it:**

```bash
.claude/skills/ops/bin/ops tf plan oregon -- -var-file=prod.tfvars
```

It is transparent, handing off every argument, stdin and the exit status, so it substitutes for
`terraform` anywhere. `init` is exempt, being the command that repairs a mismatch, and `GUARD=0`
skips one invocation for an operator who has judged the guard wrong.
