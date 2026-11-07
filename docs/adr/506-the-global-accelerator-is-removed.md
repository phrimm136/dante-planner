# 506 the-global-accelerator-is-removed
epic: none · pr: none

## Decisions
- @edge @accelerator @terraform — The accelerator stack, its pre-flight check and its state object are retired, and the Cloudflare tunnels behind the load balancer are the only entry plane. The state object holds no resources and no account holds an accelerator, so nothing needs a destroy, and every stack still present at the next AWS provider major upgrade is one more to migrate. Re-check: `aws s3api head-object --bucket <management state bucket> --key global-accelerator/terraform.tfstate` answers 404 once the object is deleted, and until then its body lists no resources; `aws globalaccelerator list-accelerators --region us-west-2` lists none in any account.
  REJECTED: keeping the stack as the tunnels' rollback — standby infrastructure nobody operates silently defeats the saving the tunnels were adopted for, and a rollback never rehearsed is a claim, not a path.
  INVARIANT: no account holds an accelerator — unenforced.
  NON-GOAL: the fleet module's accelerator hooks (the health-check security-group rule, its prefix list and the variable that enables them) — they go with the ingress node they were written for.
- @edge @guardrails — The region guardrail keeps exempting the accelerator's API. The entry is one of the global services that answer in a region the caller did not choose, not a grant the retired stack needed.
  REJECTED: dropping it with the stack — it makes the global-service list wrong about the service, and saves nothing while no constrained account calls it.

## Takeaway
- takeaway: once a replacement has carried production, the old path's code and state go together; a stack kept "just in case" is upgrade work nobody gets to skip.
