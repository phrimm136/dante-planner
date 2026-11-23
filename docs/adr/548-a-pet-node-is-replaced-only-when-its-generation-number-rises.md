# 548 a-pet-node-is-replaced-only-when-its-generation-number-rises
epic: none · pr: none

## Decisions
- @nodes @lifecycle @terraform — The control-plane, data and ingress instances ignore drift in `ami` and `user_data`, and each is replaced only when its own number in `node_generation` rises, in the tracked module call of its region; the new instance boots from the newest image and the current boot script. The image comes from a lookup that returns the vendor's newest image on every plan, so any region apply after a new image planned to replace all three instances in one run, which takes the region down, and region changes shipped as `-target` applies. A changed boot script is applied in place by stopping and starting the instance, and cloud-init does not run it again on that boot, so the node would restart, change its public address and keep its old setup. Re-check: `rg -c 'replace_triggered_by = \[terraform_data.node_generation' terraform/modules/fleet/cp.tf terraform/modules/fleet/data-node.tf terraform/modules/fleet/ingress.tf`.
  REJECTED: ignoring both attributes and replacing by hand with `-replace` — a boot-script fix then plans clean, applies clean and reaches no node, with nothing that says so, and the replacement leaves no reviewed change behind.
  REJECTED: replacing an instance whenever its boot script changes — both control planes already differ from the code, so every region apply would carry their replacement, and the data and ingress instances share one script, so one edit replaces both in the same apply.
  REJECTED: pinning the image id in a variable — the bump still replaces all three instances in the apply that carries it, and an image id differs per region.
  REJECTED: `prevent_destroy` on the three instances — the lifecycle block takes no variable, and the same module is torn down at the end of every staging rehearsal.
  INVARIANT: a plan of a region stack replaces a pet instance only for a raised generation number, one instance per number — the lifecycle block on each instance; creating the generation resources replaces nothing and raising one number replaces one resource, both observed in a plan.
  INVARIANT: a pet that runs an older boot script than the code holds is named in every plan — the `check` block beside the generation resources warns per instance; a warning does not stop an apply.
  NON-GOAL: a warning for an older image — the vendor publishes images continuously, so it would warn on every plan; an instance's launch time is the record.
  NON-GOAL: the app launch template — app nodes are replaced by the autoscaling group, so a new template version with the newest image is how they are meant to move.
  NON-GOAL: making a replacement safe — raising a number replaces the instance and nothing more; a replaced control plane starts an empty cluster at a new address until its boot restores a snapshot and its address is stable, so that number stays where it is until then.
  NON-GOAL: changing a running pet in place — what must reach a running node before its replacement is delivered by a separate, idempotent association, as the scrape annotations are.

## Takeaway
- takeaway: an input that takes effect only at first boot is not configuration of a running machine; the tool should change it only by making a new machine, at a moment someone chose, and should say when a machine is behind.
