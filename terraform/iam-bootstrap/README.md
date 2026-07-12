# terraform/iam-bootstrap — Oregon provisioning identity

Creates the **least-privilege IAM identity that provisions the Oregon fleet** (`terraform/oregon`).
The existing `terraform-rds` role can't provision Oregon (no EC2/IAM/ECR/SSM/etc.), so this stack
mints a dedicated **provisioning role** assumable two ways:

- a **human admin from a laptop** (`sts:AssumeRole`), for the initial apply of `terraform/oregon`;
- **GitHub Actions via OIDC** (`sts:AssumeRoleWithWebIdentity`), for applying changes in CI.

There is **no instance profile** — neither runner is an EC2 instance.

> **This stack is applied ONCE, by an admin, with its own state.** It is NOT part of
> `terraform/oregon` (separate directory, separate state). It is the bootstrap that makes the
> other stacks assumable. Apply it by hand with admin credentials; everything else then runs as
> the role it produces.

## What it creates

| Resource | Purpose |
|----------|---------|
| `aws_iam_role.provisioner` | The provisioning role (`var.role_name`, default `dante-oregon-provisioner`). |
| `aws_iam_policy.provisioning` | Service-scoped permissions (EC2 / ASG / ECR / SSM / S3 / CloudWatch / Logs + `oregon-*` IAM). |
| `aws_iam_openid_connect_provider.github` | GitHub Actions OIDC provider (guarded by `create_github_oidc_provider`; only one per account). |

## Scoping

**Not AdministratorAccess.** The policy is grouped by service. Only the two services that support
account-safe resource ARNs are resource-scoped:

- **IAM** — `iam:*`/`iam:PassRole` are confined to the exact role and instance-profile name patterns
  `terraform/oregon` creates: `arn:aws:iam::<account>:role/<name_prefix>-oregon-*` and
  `arn:aws:iam::<account>:instance-profile/<name_prefix>-oregon-*`. So keep `name_prefix` in sync
  with `terraform/oregon` or the provisioner can't manage its node roles.
- **S3** — confined to `arn:aws:s3:::<name_prefix>-oregon-*` (the etcd-snapshot bucket).

Everything else (`ec2:*`, `autoscaling:*`, `ecr:*`, SSM read+token write, `cloudwatch:*`, `logs:*`)
is at `Resource="*"` because their `Describe*`/registry/tag-on-create actions don't accept
resource-level ARNs — the scope there is the service + action set. Two extra narrow grants:
`iam:CreateServiceLinkedRole` conditioned to `autoscaling.amazonaws.com` (first ASG in a fresh
account), and KMS `Encrypt/Decrypt/GenerateDataKey` conditioned to `kms:ViaService = ssm.<region>`
(the SecureString k3s join token uses the AWS-managed `aws/ssm` key).

## Bootstrap runbook

Run once, with **admin** credentials (not the RDS role):

```bash
cp terraform.tfvars.example terraform.tfvars     # set trusted_admin_principal_arn (gitignored)
terraform -chdir=terraform/iam-bootstrap init
terraform -chdir=terraform/iam-bootstrap validate
terraform -chdir=terraform/iam-bootstrap plan     # READ IT
terraform -chdir=terraform/iam-bootstrap apply
terraform -chdir=terraform/iam-bootstrap output provisioner_role_arn
```

Then wire the two consumers to that role ARN:

**(a) Laptop AWS profile** — assume the role for local `terraform/oregon` applies:

```ini
# ~/.aws/config
[profile dante-admin]
# your admin credentials (IAM user keys or SSO)

[profile dante-oregon-provisioner]
role_arn    = <provisioner_role_arn output>
source_profile = dante-admin
region      = us-west-2
```

```bash
AWS_PROFILE=dante-oregon-provisioner terraform -chdir=terraform/oregon apply
```

**(b) Oregon CI workflow** — replace static access keys with OIDC. The job needs
`permissions: id-token: write` and assumes the role instead of configuring keys:

```yaml
permissions:
  id-token: write   # required to request the GitHub OIDC token
  contents: read
steps:
  - uses: aws-actions/configure-aws-credentials@v4
    with:
      role-to-assume: <provisioner_role_arn output>
      aws-region: us-west-2
  - run: terraform -chdir=terraform/oregon apply -auto-approve
```

Only jobs whose OIDC `sub` matches `github_oidc_subject` (default: `main` branch) can assume the
role. Tighten it to `repo:phrimm136/dante-planner:environment:oregon` and gate the workflow on a
protected GitHub `oregon` environment for a stricter boundary.

## Public-repo invariant

This repo is public. No account ID, ARN, or principal appears as a literal in any committed file —
the account ID is derived from `data.aws_caller_identity`, and `trusted_admin_principal_arn` is a
required variable with no default (supply it via gitignored `terraform.tfvars`; never commit it).
`terraform.tfvars.example` holds placeholders only. The `github_oidc_subject` default is
the public git remote slug and is safe to commit.

## OIDC provider already exists?

AWS allows only one OIDC provider per URL per account. If `token.actions.githubusercontent.com` is
already registered (e.g. by another stack), set `create_github_oidc_provider = false` — the trust
policy then looks the existing provider up as a data source instead of creating a duplicate.

## Not managed here

- **The Oregon fleet** (`terraform/oregon`) — this stack only grants the identity that applies it.
- **State backend:** configure encrypted remote state in your private setup; local state and
  `terraform.tfvars` are gitignored. Never commit `*.tfstate`.
