# terraform/rds — Phase 1 RDS provisioning

Provisions the **empty** RDS MySQL 8.0 instance + networking + parameter group for the migration.
It does **not** load data and does **not** run the cutover — those are operator runbook steps
(`docs/30-rds-migration/runbook.md`). Terraform owns infra; never data.

## Prereqs
- A dedicated least-privilege **provisioning identity** assumed via STS (kept in private ops notes,
  not this repo). Confirm with `aws sts get-caller-identity` before applying.
- `cp terraform.tfvars.example terraform.tfvars` and fill your real `vpc_id`, `db_subnet_ids`,
  `availability_zone` (= the EC2 instance's AZ), `ec2_security_group_id`. `terraform.tfvars` is gitignored.

## Usage
```bash
export AWS_PROFILE=<your-provisioning-profile>
terraform init
terraform validate
terraform plan        # READ IT — provisioning a prod DB; verify no replace/destroy
terraform apply
terraform output rds_endpoint   # → put the host into SSM MYSQL_HOST (Commit 2)
```

## Migration toggle
- Set `enable_replication_ingress = true` during Zone 0 so the RDS replica can pull the binlog from
  the on-box source MySQL, then `terraform apply`.
- After cutover + confidence window (Zone 4), set it back to `false` and `terraform apply` to close the hole.

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
