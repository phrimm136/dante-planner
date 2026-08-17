# Terraform stacks

Eleven root modules, one shared child module (`modules/fleet`). Each stack owns a blast radius: a
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
| `iam-bootstrap` | The GitHub OIDC provider and the provisioning role (and the state bucket in the account that predates `state-backend`) | `trusted_admin_principal_arns`, `aws_account_id` |
| `log-archive` | The organization trail's destination bucket and its policy | `aws_account_id`, `management_account_id`, `organization_id` |
| `registry` | Who may pull the backend image, not the repositories themselves | `aws_account_id`, `organization_id` |
| `secrets` | Secrets Manager **containers** and their cross-region replicas — never the values | `aws_account_id` |
| `rds` | The MySQL primary, its parameter group, subnet group and security group | `vpc_id`, `db_subnet_ids`, `engine_version`, `aws_account_id` |
| `oregon` | The primary k3s fleet (`modules/fleet`), peered to the RDS VPC | `ingress_allowed_cidrs`, `rds_vpc_id`, `aws_account_id` |
| `seoul` | The secondary fleet, the cross-region RDS read replica, cross-region peering | `rds_vpc_id`, `ingress_allowed_cidrs`, `aws_account_id` |
| `cloudflare` | Tunnels, load balancer, pools, health monitor | `cloudflare_api_token`, `account_id`, `zone_id` |
| `global-accelerator` | The AWS Global Accelerator entry plane | `aws_account_id` |

## State

State lives in the account whose resources it describes, so the bucket count tracks the account
count. `state-backend` and `iam-bootstrap` keep **local** state, because between them they create
the bucket everything else stores state in; storing their own state there would be circular. Both
use one workspace per account, so those local files stay apart.

Every other stack declares a partial `backend "s3"` and takes the bucket at init time:

```bash
terraform -chdir=terraform/state-backend output -raw tf_state_bucket   # -> a backend config file
terraform -chdir=terraform/<stack> init -backend-config=../backend.<account>.hcl
```

Those config files are gitignored because a bucket name carries an account id; copy
`backend.hcl.example`. One per account, not one shared — and a stack reaching into another account
needs `assume_role` in **that file** as well as in its provider, because a backend resolves
credentials before any provider exists and cannot use theirs.

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
              │
      global-accelerator                     (reads oregon state)
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

The RDS VPC is an **input**, not an output. The only `aws_vpc` resource in this repo is the fleet's
own (`modules/fleet/network.tf`); `rds` takes `vpc_id` and `db_subnet_ids`, and both fleets take
`rds_vpc_id`, all pointing at a VPC no stack manages. On an account without it there is nothing to
supply.

Every `terraform.tfvars` is gitignored. Each stack ships a tracked `terraform.tfvars.example`.

## From scratch

Once per organization, from the management account:

1. Run `scripts/ops/provision/org-preflight.sh`, then apply `organization`. Creating an organization
   converts the calling account into the management account with no path back.
2. Enable IAM Identity Center in the console — no API does — then re-apply so the permission sets and
   assignments land.
3. Apply `log-archive` and `registry`, and verify with `scripts/ops/provision/org-verify.sh`.

Once per account, with credentials for that account:

4. Apply `state-backend`, in a workspace named for the account. Local state, no dependencies.
5. Write `terraform/backend.<account>.hcl` from its `tf_state_bucket` output.
6. Apply `iam-bootstrap` for any account that runs a fleet, giving it the provisioning role that
   applies everything below.

Then, per environment:

7. Apply `secrets`, and run the `scripts/ops/provision/` scripts that seed values.
8. Apply `rds`, `oregon` and `cloudflare`.
9. Run `cloudflare-tunnel-secrets.sh` now that the tunnel tokens exist.
10. Apply `seoul`, then `global-accelerator`.

This provisions infrastructure, not a running system: a fresh `rds` is an empty schema. Data arrives
from a dump, and the schema from Flyway on first pod boot.

## Guards

`allowed_account_ids = [var.aws_account_id]` on every AWS provider fails the plan when the resolved
credentials belong to another account — the guard a workspace does not provide, since workspaces
isolate state and nothing else.

`aws_db_instance.this` and the prod secrets carry `prevent_destroy` alongside RDS
`deletion_protection`, so a destroy or a replacement errors instead of deleting data. The fleet
stacks deliberately carry neither, so their destroy-and-rebuild cycle keeps working.
