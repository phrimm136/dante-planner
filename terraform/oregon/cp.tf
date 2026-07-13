# --- Cluster join token -----------------------------------------------------
# Generated once by Terraform, stored as a SecureString. The CP reads it to
# init the server; every agent reads it to join. Fully unattended — no operator
# secret handoff. The token lives in Terraform state (sensitive) and SSM only.
resource "random_password" "k3s_token" {
  length  = 48
  special = false
}

resource "aws_ssm_parameter" "k3s_token" {
  name        = "/${var.name_prefix}/oregon/k3s-join-token"
  description = "k3s cluster join token (CP writes, agents read)"
  type        = "SecureString"
  value       = random_password.k3s_token.result
  tags        = var.tags
}

# --- CP node (pet): k3s server + embedded etcd + ArgoCD core ----------------
resource "aws_instance" "cp" {
  ami                    = data.aws_ssm_parameter.node_ami.value
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.cluster.id]
  iam_instance_profile   = aws_iam_instance_profile.cp.name
  key_name               = var.ssh_key_name != "" ? var.ssh_key_name : null

  user_data = templatefile("${path.module}/user-data/cp.sh.tftpl", {
    region              = var.region
    token_param         = aws_ssm_parameter.k3s_token.name
    s3_bucket           = aws_s3_bucket.etcd_snapshots.bucket
    snapshot_retention  = var.etcd_snapshot_retention
    gitops_repo_url     = var.gitops_repo_url
    gitops_revision     = var.gitops_target_revision
    argocd_version      = var.argocd_version
    gateway_api_version = var.gateway_api_version
    eso_chart_version   = var.external_secrets_chart_version
    ecr_image           = "${var.backend_image_repo}=${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.region}.amazonaws.com/${var.backend_image_repo}:*"
  })

  metadata_options {
    http_tokens   = "required" # IMDSv2 only
    http_endpoint = "enabled"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 30
    encrypted   = true
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-oregon-cp", Role = "control-plane" })
}
