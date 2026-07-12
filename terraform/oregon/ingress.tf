# --- Ingress node (pet): Traefik only, the Global Accelerator endpoint ------
# Joins as a k3s agent labeled role=ingress; the Traefik Gateway (deploy/base)
# is pinned here. Carries the public-443 ingress SG. GA itself is Phase 14.
resource "aws_instance" "ingress" {
  ami                    = data.aws_ssm_parameter.node_ami.value
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.cluster.id, aws_security_group.ingress.id]
  iam_instance_profile   = aws_iam_instance_profile.ingress.name
  key_name               = var.ssh_key_name != "" ? var.ssh_key_name : null

  user_data = templatefile("${path.module}/user-data/agent.sh.tftpl", {
    region                = var.region
    token_param           = aws_ssm_parameter.k3s_token.name
    server_url            = "https://${aws_instance.cp.private_ip}:6443"
    node_label            = "role=ingress"
    set_nofile            = false
    cred_provider_version = var.ecr_credential_provider_version
  })

  metadata_options {
    http_tokens   = "required"
    http_endpoint = "enabled"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
    encrypted   = true
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-oregon-ingress", Role = "ingress" })
}
