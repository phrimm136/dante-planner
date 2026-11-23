# Retiring the management account's bootstrap identity

The management account runs no fleet. Its `terraform/iam-bootstrap` state still holds the
provisioning identity: the GitHub OIDC provider, the `danteplanner-provisioner` role, two
policies and two role attachments. The same state also holds the account's state bucket.

This runbook moves the bucket to `terraform/state-backend` and then removes the identity.
Afterwards the management account has the same shape as every other account. `state-backend`
owns the bucket. `iam-bootstrap` exists only in an account that runs a fleet.

Nothing in the cloud changes until step 4, except one tag in step 2. Step 4 deletes the
identity. The bucket and every state in it stay.

Run every command from the repository root, on a checkout that holds
`terraform/state-backend/imports.tf` and `terraform/iam-bootstrap/removed.tf`.

## 1. Before you start

Confirm the profile resolves to the management account:

```bash
.claude/skills/ops/bin/ops --env mgmt whoami
```

Confirm that nothing uses the role. The last use must be older than any deploy you care about:

```bash
AWS_PROFILE=mgmt aws iam get-role --role-name danteplanner-provisioner --query Role.RoleLastUsed
```

Step 4 also deletes the account's GitHub OIDC provider. List the roles whose trust policy names
that provider:

```bash
AWS_PROFILE=mgmt aws iam list-roles --output text \
  --query "Roles[?contains(to_string(AssumeRolePolicyDocument), 'token.actions.githubusercontent.com')].RoleName"
```

It must print `danteplanner-provisioner` and nothing else. Any other role name stops the
runbook. That role assumes through the provider, and step 4 cuts it off.

You need `terraform/state-backend/management.tfvars` (gitignored). It holds one line,
`aws_account_id`, with the same value as `terraform/iam-bootstrap/management.tfvars`.

## 2. Adopt the bucket in state-backend

```bash
scripts/ops/terraform-run.sh -chdir=terraform/state-backend init -reconfigure -backend-config=../backend.management.hcl
.claude/skills/ops/bin/ops --env mgmt tf plan state-backend -- -var-file=management.tfvars
```

The plan must show exactly these lines:

```
~ aws_s3_bucket.tf_state
<= aws_s3_bucket.tf_state
<= aws_s3_bucket_lifecycle_configuration.tf_state
<= aws_s3_bucket_public_access_block.tf_state
<= aws_s3_bucket_server_side_encryption_configuration.tf_state
<= aws_s3_bucket_versioning.tf_state
Plan: 5 to import, 0 to add, 1 to change, 0 to destroy.
```

The one change removes the `Phase` tag that `iam-bootstrap` set on the bucket. The log shows it
under `tags`. Any other change, or any `+` or `-` line, stops the runbook.

Run the printed `! … ops tf apply <plan file>` line. Then check the state:

```bash
scripts/ops/terraform-run.sh -chdir=terraform/state-backend state list
```

It lists `data.aws_caller_identity.current` and the five `tf_state` resources, without an index.

## 3. Release the bucket from iam-bootstrap

The plan targets the five bucket resources. Without the targets, the plan also updates
`aws_iam_policy.provisioning` with a statement that was never applied here. Step 4 deletes that
policy, so the update is not needed.

```bash
scripts/ops/terraform-run.sh -chdir=terraform/iam-bootstrap init -reconfigure -backend-config=../backend.management.hcl
.claude/skills/ops/bin/ops --env mgmt tf plan iam-bootstrap \
  --expect 'aws_s3_bucket.tf_state[0]' \
  --expect 'aws_s3_bucket_lifecycle_configuration.tf_state[0]' \
  --expect 'aws_s3_bucket_public_access_block.tf_state[0]' \
  --expect 'aws_s3_bucket_server_side_encryption_configuration.tf_state[0]' \
  --expect 'aws_s3_bucket_versioning.tf_state[0]' \
  -- -var-file=management.tfvars \
  -target=aws_s3_bucket.tf_state \
  -target=aws_s3_bucket_public_access_block.tf_state \
  -target=aws_s3_bucket_versioning.tf_state \
  -target=aws_s3_bucket_server_side_encryption_configuration.tf_state \
  -target=aws_s3_bucket_lifecycle_configuration.tf_state
```

The plan must show exactly these lines:

```
forget aws_s3_bucket.tf_state[0]
forget aws_s3_bucket_lifecycle_configuration.tf_state[0]
forget aws_s3_bucket_public_access_block.tf_state[0]
forget aws_s3_bucket_server_side_encryption_configuration.tf_state[0]
forget aws_s3_bucket_versioning.tf_state[0]
Plan: 0 to add, 0 to change, 0 to destroy, 5 to forget.
```

A forget drops the resource from this state and leaves the bucket in the cloud. The log says
"will no longer be managed by Terraform, but will not be destroyed" for each one. Terraform
also prints a warning about the targets. That warning is expected here.

Run the printed `! … ops tf apply <plan file>` line. Then check the state:

```bash
scripts/ops/terraform-run.sh -chdir=terraform/iam-bootstrap state list
```

It lists four data sources and six resources. No `aws_s3_bucket` address is left.

## 4. Remove the identity

Do this step only after step 3 is applied. Before step 3, this plan also lists the five bucket
resources as `-` lines. The `removed` blocks do not protect them in a plan that removes the
whole stack, and the bucket's `prevent_destroy` left with the resource block. The guard blocks
that plan because the five addresses are not expected, and it prints no apply line.

```bash
.claude/skills/ops/bin/ops --env mgmt tf plan iam-bootstrap \
  --expect 'aws_iam_openid_connect_provider.github[0]' \
  --expect aws_iam_policy.deploy_surge \
  --expect aws_iam_policy.provisioning \
  --expect aws_iam_role.provisioner \
  --expect aws_iam_role_policy_attachment.deploy_surge \
  --expect aws_iam_role_policy_attachment.provisioning \
  -- -destroy -var-file=management.tfvars
```

The plan must show exactly these lines:

```
- aws_iam_openid_connect_provider.github[0]
- aws_iam_policy.deploy_surge
- aws_iam_policy.provisioning
- aws_iam_role.provisioner
- aws_iam_role_policy_attachment.deploy_surge
- aws_iam_role_policy_attachment.provisioning
Plan: 0 to add, 0 to change, 6 to destroy.
```

Any `aws_s3_` line stops the runbook. It means step 3 is not applied.

Run the printed `! … ops tf apply <plan file>` line.

## 5. Check the result

```bash
.claude/skills/ops/bin/ops --env mgmt whoami
scripts/ops/terraform-run.sh -chdir=terraform/iam-bootstrap state list
scripts/ops/terraform-run.sh -chdir=terraform/state-backend init -reconfigure -backend-config=../backend.management.hcl
scripts/ops/terraform-run.sh -chdir=terraform/state-backend state list
.claude/skills/ops/bin/ops --env mgmt tf plan state-backend -- -var-file=management.tfvars
```

- `whoami` still names the management account. The `mgmt` profile is an Identity Center
  session and never used the deleted role.
- The `iam-bootstrap` state list prints nothing.
- The `state-backend` init succeeds, so `terraform/backend.management.hcl` still reaches the
  bucket. Its state list shows the five `tf_state` resources.
- The `state-backend` plan prints `No changes.`

## 6. Clean up the laptop

- Delete the `[profile danteplanner-provisioner]` section from `~/.aws/config`. It assumes the
  deleted role. The `staging-provisioner` and `prod-provisioner` profiles stay.
- Move `terraform/iam-bootstrap/management.tfvars` to
  `~/.local/state/danteplanner/tf-state-backup/`. No stack reads it any more.

## 7. Afterwards

Delete `terraform/state-backend/imports.tf`, `terraform/iam-bootstrap/removed.tf`, this
runbook and its entry in `docs/debt.md`, in one change. Every account's states are then past
those blocks.
