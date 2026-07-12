# --- Data node (pet): auth Redis StatefulSet + local rate-limit Redis + Prometheus
# Joins as a k3s agent labeled role=data. The stateful workloads (deploy/base)
# are pinned here. No Sentinel, no auto-promote — Redis outage is a typed 503 +
# auto-recovery + AOF replay (requirements: no autonomous writer promotion).
resource "aws_instance" "data" {
  ami                    = data.aws_ssm_parameter.node_ami.value
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.cluster.id]
  iam_instance_profile   = aws_iam_instance_profile.data.name
  key_name               = var.ssh_key_name != "" ? var.ssh_key_name : null

  user_data = templatefile("${path.module}/user-data/agent.sh.tftpl", {
    region      = var.region
    token_param = aws_ssm_parameter.k3s_token.name
    server_url  = "https://${aws_instance.cp.private_ip}:6443"
    node_label  = "role=data"
    set_nofile  = false
  })

  metadata_options {
    http_tokens   = "required"
    http_endpoint = "enabled"
  }

  # AZ-pinned EBS gp3 for the Redis AOF/RDB PVC (StatefulSet durability tier).
  root_block_device {
    volume_type = "gp3"
    volume_size = 30
    encrypted   = true
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-oregon-data", Role = "data" })
}
