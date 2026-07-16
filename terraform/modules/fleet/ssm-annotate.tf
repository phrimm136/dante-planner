# --- Scrape-annotation converger delivery -----------------------------------
# user-data executes once per instance, so bootstrap changes never reach CPs
# that already exist. This State Manager association delivers the same installer
# (user-data/scrape-annotate-install.sh, shared with cp.sh.tftpl) to the live CP
# on association creation and again whenever the installer content changes.
# Reboot/restart convergence itself is the installed systemd unit's job — the
# association only guarantees the unit exists.
resource "aws_ssm_document" "scrape_annotate" {
  name            = "${var.name_prefix}-${var.region_name_suffix}-scrape-annotate-install"
  document_type   = "Command"
  document_format = "JSON"
  tags            = var.tags

  content = jsonencode({
    schemaVersion = "2.2"
    description   = "Install the scrape-annotation converger unit and run it once"
    mainSteps = [{
      action = "aws:runShellScript"
      name   = "installScrapeAnnotate"
      inputs = {
        runCommand = [
          file("${path.module}/user-data/scrape-annotate-install.sh"),
          "systemctl start scrape-annotate.service --no-block",
        ]
      }
    }]
  })
}

resource "aws_ssm_association" "scrape_annotate" {
  name             = aws_ssm_document.scrape_annotate.name
  association_name = "${var.name_prefix}-${var.region_name_suffix}-scrape-annotate"

  targets {
    key    = "InstanceIds"
    values = [aws_instance.cp.id]
  }
}
