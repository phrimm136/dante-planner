# --- etcd metrics enablement convergence ------------------------------------
# The bootstrap passes --etcd-expose-metrics in the k3s server args, but
# user-data runs once per instance, so CPs that predate the flag never gain
# it. This association converges on the observable state: if :2381 already
# serves metrics (args or config file, either source), it exits untouched;
# only a dead endpoint triggers the config append and a k3s restart.
resource "aws_ssm_document" "etcd_metrics" {
  name            = "${var.name_prefix}-${var.region_name_suffix}-etcd-metrics-enable"
  document_type   = "Command"
  document_format = "JSON"
  tags            = var.tags

  content = jsonencode({
    schemaVersion = "2.2"
    description   = "Ensure the k3s embedded etcd serves metrics on :2381"
    mainSteps = [{
      action = "aws:runShellScript"
      name   = "ensureEtcdMetrics"
      inputs = {
        runCommand = [
          <<-EOT
            curl -sf -m 3 http://127.0.0.1:2381/metrics >/dev/null && exit 0
            mkdir -p /etc/rancher/k3s
            grep -q '^etcd-expose-metrics' /etc/rancher/k3s/config.yaml 2>/dev/null \
              || echo 'etcd-expose-metrics: true' >> /etc/rancher/k3s/config.yaml
            systemctl restart k3s
          EOT
        ]
      }
    }]
  })
}

resource "aws_ssm_association" "etcd_metrics" {
  name             = aws_ssm_document.etcd_metrics.name
  association_name = "${var.name_prefix}-${var.region_name_suffix}-etcd-metrics"

  targets {
    key    = "InstanceIds"
    values = [aws_instance.cp.id]
  }
}
