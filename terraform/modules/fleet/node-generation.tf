resource "terraform_data" "node_generation" {
  for_each = var.node_generation
  input    = each.value
}

# The assertions read these flags, not the scripts: a failed assertion prints every value its
# condition refers to, and a boot script is hundreds of lines and names the account in a bucket.
locals {
  pet_runs_the_current_boot_script = {
    cp      = sha256(aws_instance.cp.user_data) == sha256(local.cp_user_data)
    data    = sha256(aws_instance.data.user_data) == sha256(local.data_user_data)
    ingress = sha256(aws_instance.ingress.user_data) == sha256(local.ingress_user_data)
  }
}

check "pets_run_the_current_boot_script" {
  assert {
    condition     = local.pet_runs_the_current_boot_script.cp
    error_message = "The cp instance was built from an older boot script than the code holds. It takes the current one when node_generation.cp is raised, which replaces it."
  }
  assert {
    condition     = local.pet_runs_the_current_boot_script.data
    error_message = "The data instance was built from an older boot script than the code holds. It takes the current one when node_generation.data is raised, which replaces it."
  }
  assert {
    condition     = local.pet_runs_the_current_boot_script.ingress
    error_message = "The ingress instance was built from an older boot script than the code holds. It takes the current one when node_generation.ingress is raised, which replaces it."
  }
}
