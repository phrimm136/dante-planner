# Take ownership of the pre-existing prod secrets to attach a cross-region
# replica. Config-driven import: the import blocks adopt the existing secrets
# into state during the SAME apply that adds the replica — no manual
# `terraform import`, so replication is enabled by one unattended apply
# (requirements: "RS256 private key via multi-region secret replication").
#
# Only the secret CONTAINER is managed, never a secret_version — the values are
# untouched here, and AWS replicates every version to the replica region on its
# own. Seoul's app-node IAM must grant secretsmanager:GetSecretValue on the
# region-specific replica ARN (handled in the Seoul stack, chunk 3).
#
# REVIEW THE IMPORT PLAN BEFORE APPLYING. This config declares only name+replica;
# if a real secret carries a custom kms_key_id, rotation, or recovery window, the
# refresh-on plan will try to null those (an in-place change prevent_destroy does
# NOT guard). If the plan shows any attribute change beyond adding the replica,
# add a matching attribute or lifecycle{ ignore_changes = [...] } and re-plan.
import {
  for_each = var.secret_names
  to       = aws_secretsmanager_secret.replicated[each.value]
  id       = each.value
}

resource "aws_secretsmanager_secret" "replicated" {
  for_each = var.secret_names
  name     = each.value

  replica {
    region = var.replica_region
  }

  # Prod secrets: never let a destroy delete them. This stack is applied once and
  # is not part of the fleet's destroy+apply rebuild, so prevent_destroy here does
  # NOT compromise the unattended-rebuild proof (unlike the fleet stack).
  lifecycle {
    prevent_destroy = true
  }
}
