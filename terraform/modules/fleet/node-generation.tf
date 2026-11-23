resource "terraform_data" "node_generation" {
  for_each = var.node_generation
  input    = each.value
}

check "pets_run_the_current_boot_script" {
  assert {
    condition     = aws_instance.cp.user_data == local.cp_user_data
    error_message = "The cp instance was built from an older boot script than the code holds. It takes the current one when node_generation.cp is raised, which replaces it."
  }
  assert {
    condition     = aws_instance.data.user_data == local.data_user_data
    error_message = "The data instance was built from an older boot script than the code holds. It takes the current one when node_generation.data is raised, which replaces it."
  }
  assert {
    condition     = aws_instance.ingress.user_data == local.ingress_user_data
    error_message = "The ingress instance was built from an older boot script than the code holds. It takes the current one when node_generation.ingress is raised, which replaces it."
  }
}
