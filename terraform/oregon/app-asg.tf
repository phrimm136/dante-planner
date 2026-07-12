# --- App tier (cattle): ASG of k3s agents labeled role=app -------------------
# Spring runs as a DaemonSet on role=app nodes, so the ASG is the only scaling
# dial: node count = pod count. min=1/desired=1/max=2 (max doubles as deploy
# surge and load headroom). Nodes join unattended via user-data + the SSM join
# token. LimitNOFILE=65536 is set on the k3s-agent service (SSE fd stampede).
resource "aws_launch_template" "app" {
  name_prefix   = "${var.name_prefix}-oregon-app-"
  image_id      = data.aws_ssm_parameter.node_ami.value
  instance_type = var.instance_type
  key_name      = var.ssh_key_name != "" ? var.ssh_key_name : null

  iam_instance_profile {
    name = aws_iam_instance_profile.app.name
  }

  vpc_security_group_ids = [aws_security_group.cluster.id]

  metadata_options {
    http_tokens   = "required"
    http_endpoint = "enabled"
  }

  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_type = "gp3"
      volume_size = 20
      encrypted   = true
    }
  }

  user_data = base64encode(templatefile("${path.module}/user-data/agent.sh.tftpl", {
    region      = var.region
    token_param = aws_ssm_parameter.k3s_token.name
    server_url  = "https://${aws_instance.cp.private_ip}:6443"
    node_label  = "role=app"
    set_nofile  = true
    cred_provider_version = var.ecr_credential_provider_version
  }))

  tag_specifications {
    resource_type = "instance"
    tags          = merge(var.tags, { Name = "${var.name_prefix}-oregon-app", Role = "app" })
  }

  tags = var.tags
}

resource "aws_autoscaling_group" "app" {
  name                = "${var.name_prefix}-oregon-app"
  min_size            = var.app_asg_min_size
  desired_capacity    = var.app_asg_desired_capacity
  max_size            = var.app_asg_max_size
  vpc_zone_identifier = aws_subnet.public[*].id
  health_check_type   = "EC2"

  launch_template {
    id      = aws_launch_template.app.id
    version = "$Latest"
  }

  # Surge deploys roll one node at a time; the cross-region fallback absorbs the
  # ~30s Spring boot gap (requirements: default deploy = fallback-absorbed rolling).
  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 50
    }
  }

  tag {
    key                 = "Name"
    value               = "${var.name_prefix}-oregon-app"
    propagate_at_launch = true
  }

  tag {
    key                 = "Role"
    value               = "app"
    propagate_at_launch = true
  }
}
