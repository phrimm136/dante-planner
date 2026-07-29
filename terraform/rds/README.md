# terraform/rds — Phase 1 RDS provisioning

Provisions the **empty** RDS MySQL 8.0 instance + networking + parameter group for the migration.
It does **not** load data and does **not** run the cutover — those are operator runbook steps
(`docs/tasks/030-rds-migration/runbook.md`). Terraform owns infra; never data.

## Prereqs
- A dedicated least-privilege **provisioning identity** assumed via STS (kept in private ops notes,
  not this repo). Confirm with `aws sts get-caller-identity` before applying.
- `cp terraform.tfvars.example terraform.tfvars` and fill your real `vpc_id` and `db_subnet_ids`.
  `terraform.tfvars` is gitignored.
- Seed the master password once with `scripts/ops/provision/rds-master-password-secret.sh`. It is
  read from Secrets Manager at plan time, so the entry must exist before the first apply.

## Usage
```bash
export AWS_PROFILE=<your-provisioning-profile>
terraform init
terraform validate
terraform plan        # READ IT — provisioning a prod DB; verify no replace/destroy
terraform apply
terraform output rds_endpoint   # → put the host into SSM MYSQL_HOST (Commit 2)
```

## Guards
`prevent_destroy` + `deletion_protection` + `skip_final_snapshot=false` protect the data-bearing
instance: a replace-forcing change or a `terraform destroy` will **error**, not delete. Removing the
instance intentionally requires lifting these guards deliberately.

## Not managed here
- The AWS-managed **master password** lives in Secrets Manager (`manage_master_user_password`); see the
  `master_user_secret_arn` output to retrieve it for admin ops.
- The **app DB user** `danteplanner` + grants are created by SQL on RDS (runbook 0.6b), not Terraform —
  `mysqldump <db>` does not carry users/grants.
- The state backend: configure remote state (e.g. encrypted S3) in your private setup; local state and
  tfvars are gitignored. Never commit `*.tfstate` — it can contain sensitive values.
