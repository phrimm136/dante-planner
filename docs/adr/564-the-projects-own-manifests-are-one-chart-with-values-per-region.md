# 564 the-projects-own-manifests-are-one-chart-with-values-per-region
epic: none · pr: none

## Decisions
- @deploy @helm @region — The project's own manifests render from one Helm chart, with one values file per region, and the root Application of a region names that file. What differs per region is written once, in that file, and a schema ties the region to its role: Seoul's auth Redis is a replica, Oregon's is the primary that Seoul reaches. A third-party chart needs the region's name too, and kustomize can give it one only through a patch on each child Application.
  REJECTED: kustomize overlays that patch the child Applications — the region's name is written in several patches, a wrong one still renders, and kustomize stays.
  REJECTED: a full copy of each Application per region — values that are common then drift apart.
  REJECTED: moving the third-party components to their charts first — each needs a region patch written for kustomize and thrown away when the project's own manifests move.
  INVARIANT: a values file does not render when its region, its AWS region, its auth Redis role or its backend Redis hosts are of another region — the schema of the values, and CI renders six wrong values and fails when one passes. Values the schema does not name are not covered.
  INVARIANT: the registry host is in no committed file — the root Application carries it as a Helm parameter; unenforced.
  NON-GOAL: moving a third-party component to its upstream chart — none of those charts can keep the live selector, so each move deletes and recreates the workload.
- @deploy @release @tag — Every reader of a deployed image tag accepts exactly one tag line of 40 hex digits in the region's values file, and anything else is a failure. The readers used to treat a file they could not read as "no tag": one then deployed every merge, and the other counted a release as not landed and drained the surge nodes before the rollout.
  REJECTED: a default for an unreadable file — each default is an action nobody asked for, and both hide that the file moved.
  INVARIANT: a missing file, a missing or second tag line and a malformed tag each fail the reader — the tests run the script and the workflow steps on each.
  INVARIANT: the backend image takes the registry parameter and the region's tag, and the Pod of mysqld-exporter carries a checksum of the configuration it mounts, because its ConfigMaps keep one name and a new CA bundle would not restart it — the render stage of the deploy test.
  NON-GOAL: two regions on two tags — a release still writes both in one commit.

## Superseded
- @deploy @helm @coexistence → fulfilled — The chart lives beside the kustomize overlays until both root Applications render the chart, and then the overlays go. While both exist, CI fails when they render different objects, and a release writes the image tag to both. Only two differences are allowed: the two generated ConfigMaps lose the content hash in their names, which a checksum annotation on the Pod replaces, and a cluster-scoped object loses a namespace it never used.
  REJECTED: switching in one change — the root Applications are patched at boot and no Application manages them, so a merge cannot move them, and the overlays must stay until a person has.
  INVARIANT: the comparison can fail — each run also compares a render with one changed field and fails when that passes.
- @deploy @argocd @switch → fulfilled — A root Application moves in two patches: automated sync goes off, and once no sync runs, one patch sets the chart source and removes the kustomize source. A sync that had started would read the new source and prune, and ArgoCD refuses a source that carries both tools. Sync comes back on only when the objects that differ are the expected ones, and any other ending before that restores the old source.
  REJECTED: leaving automated sync on — the render equality of CI does not show which values file a cluster reads.
  REJECTED: raising the control plane's generation so that its boot script sets the source — a replaced control plane starts an empty cluster.
  INVARIANT: every ending the script reaches between the first patch and the return of sync tries to restore the former source and sync in one patch, and says which of the two endings it got. A restore that failed and a process that was killed leave a state that the rollback mode repairs — the tests run a wrong comparison, a refused patch, a refused restore, and a signal before and after sync is back.

## Takeaway
- takeaway: when a tool changes and the result must not, prove the results equal first; then the one thing left to check at the switch is which input each place reads.
