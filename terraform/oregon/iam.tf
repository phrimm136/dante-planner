# One EC2 role per node class so permissions track the node's job. All four get
# SSM Session Manager (management, unattended) + ECR read (no IRSA without EKS,
# so the node instance profile is the image-pull boundary). Class-specific grants
# are inline below.

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "cp" {
  name               = "${var.name_prefix}-oregon-cp"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
  tags               = var.tags
}

resource "aws_iam_role" "ingress" {
  name               = "${var.name_prefix}-oregon-ingress"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
  tags               = var.tags
}

resource "aws_iam_role" "data" {
  name               = "${var.name_prefix}-oregon-data"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
  tags               = var.tags
}

resource "aws_iam_role" "app" {
  name               = "${var.name_prefix}-oregon-app"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
  tags               = var.tags
}

locals {
  node_roles = {
    cp      = aws_iam_role.cp.name
    ingress = aws_iam_role.ingress.name
    data    = aws_iam_role.data.name
    app     = aws_iam_role.app.name
  }
  # Token param ARN pattern (the parameter itself is defined in cp.tf).
  token_param_arn = "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${aws_ssm_parameter.k3s_token.name}"
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  for_each   = local.node_roles
  role       = each.value
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "ecr_read" {
  for_each   = local.node_roles
  role       = each.value
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# --- CP: writes the join token, owns etcd snapshots in S3 -------------------
data "aws_iam_policy_document" "cp" {
  statement {
    sid       = "PublishJoinToken"
    actions   = ["ssm:PutParameter", "ssm:GetParameter"]
    resources = [local.token_param_arn]
  }
  statement {
    sid       = "EtcdSnapshotBucket"
    actions   = ["s3:ListBucket", "s3:GetBucketLocation"]
    resources = [aws_s3_bucket.etcd_snapshots.arn]
  }
  statement {
    sid       = "EtcdSnapshotObjects"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.etcd_snapshots.arn}/*"]
  }
}

resource "aws_iam_role_policy" "cp" {
  name   = "${var.name_prefix}-oregon-cp"
  role   = aws_iam_role.cp.id
  policy = data.aws_iam_policy_document.cp.json
}

# --- Agents (ingress, data, app): read the join token to join the server ----
data "aws_iam_policy_document" "read_join_token" {
  statement {
    sid       = "ReadJoinToken"
    actions   = ["ssm:GetParameter"]
    resources = [local.token_param_arn]
  }
}

resource "aws_iam_role_policy" "ingress_join" {
  name   = "${var.name_prefix}-oregon-ingress-join"
  role   = aws_iam_role.ingress.id
  policy = data.aws_iam_policy_document.read_join_token.json
}

resource "aws_iam_role_policy" "data_join" {
  name   = "${var.name_prefix}-oregon-data-join"
  role   = aws_iam_role.data.id
  policy = data.aws_iam_policy_document.read_join_token.json
}

resource "aws_iam_role_policy" "app_join" {
  name   = "${var.name_prefix}-oregon-app-join"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.read_join_token.json
}

# --- App: the External Secrets Operator controller (pinned to role=app nodes)
# reads the RS256 key from Secrets Manager via this instance profile. Coarser
# than IRSA — any pod on an app node inherits it. Documented deviation (README).
data "aws_iam_policy_document" "app_secrets" {
  statement {
    sid     = "ReadBackendSecrets"
    actions = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
    resources = [
      # The app's own secret namespace: jwt/* keys + the backend/* runtime-config bundle.
      "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${dirname(dirname(var.rs256_private_key_secret_name))}/*"
    ]
  }
}

resource "aws_iam_role_policy" "app_secrets" {
  name   = "${var.name_prefix}-oregon-app-secrets"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.app_secrets.json
}

# --- Instance profiles ------------------------------------------------------
resource "aws_iam_instance_profile" "cp" {
  name = "${var.name_prefix}-oregon-cp"
  role = aws_iam_role.cp.name
}

resource "aws_iam_instance_profile" "ingress" {
  name = "${var.name_prefix}-oregon-ingress"
  role = aws_iam_role.ingress.name
}

resource "aws_iam_instance_profile" "data" {
  name = "${var.name_prefix}-oregon-data"
  role = aws_iam_role.data.name
}

resource "aws_iam_instance_profile" "app" {
  name = "${var.name_prefix}-oregon-app"
  role = aws_iam_role.app.name
}
