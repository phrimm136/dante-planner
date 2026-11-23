# terraform/rds — Phase 1 RDS provisioning

Provisions the **empty** RDS MySQL 8.0 instance + networking + parameter group for the migration.
It does **not** load data and does **not** run the cutover — those are operator runbook steps
(`docs/runbooks/rds-migration.md`). Terraform owns infra; never data.

## Prereqs
- A dedicated least-privilege **provisioning identity** assumed via STS (kept in private ops notes,
  not this repo). Confirm with `aws sts get-caller-identity` before applying.
- The settings of each environment are tracked in `envs/<env>.tfvars` (`prod`, `staging`); change
  them there, in a reviewed commit. `ops tf plan rds` passes that file and the selected profile's
  `sso_account_id` as `aws_account_id`. It refuses `-var-file`, and `-var` for a variable the file
  sets or for `aws_account_id`; a per-run value such as `-- -var apply_immediately=true` passes.
- Seed the master password once with `scripts/ops/provision/rds-master-password-secret.sh`. It is
  read from Secrets Manager at plan time, so the entry must exist before the first apply.

## Usage
```bash
export AWS_PROFILE=<your-provisioning-profile>
scripts/ops/terraform-run.sh -chdir=terraform/rds init -backend-config=../backend.<account>.hcl
scripts/ops/terraform-run.sh -chdir=terraform/rds validate
.claude/skills/ops/bin/ops tf plan rds   # READ IT — provisioning a prod DB; verify no replace/destroy
.claude/skills/ops/bin/ops tf apply <plan file printed above>     # the user, as the printed ! line
scripts/ops/terraform-run.sh -chdir=terraform/rds output rds_endpoint   # → put the host into SSM MYSQL_HOST (Commit 2)
```

## Guards
`prevent_destroy` + `deletion_protection` + `skip_final_snapshot=false` protect the data-bearing
instance: a replace-forcing change or a `terraform destroy` will **error**, not delete. Removing the
instance intentionally requires lifting these guards deliberately.

## Not managed here
- The **master password** value lives in the Secrets Manager entry `danteplanner/rds/master-password`
  (`master_password_secret_name`), seeded by `scripts/ops/provision/rds-master-password-secret.sh`
  and enrolled by `terraform/secrets`. This stack reads it ephemerally into the write-only
  `password_wo` and keeps no copy; `manage_master_user_password` is off, so `master_user_secret_arn`
  is always null. Read the entry for admin ops.
- The **app DB user** `danteplanner` + grants are created by SQL on RDS (runbook 0.6b), not Terraform —
  `mysqldump <db>` does not carry users/grants.
- The state backend: configure remote state (e.g. encrypted S3) in your private setup; local state is
  gitignored. Never commit `*.tfstate` — it can contain sensitive values.
