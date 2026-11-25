# 569 a-custom-kind-in-use-has-its-crd-in-the-render
epic: none · pr: none

## Decisions
- @deploy @crd @prune — CI fails when a rendered custom object has no rendered CRD that serves its version, unless its group is in a list of groups whose CRDs something other than an Application installs. An Application with automated prune deletes what its chart stops rendering, and deleting a CRD deletes every object of its kind: a pruned CRD of External Secrets would take the ExternalSecrets, and their Secrets are owned by them. Re-check: `rg -n 'prune: true' deploy/chart/templates`.
  REJECTED: `Prune=false` on the CRDs — it also stops a deliberate removal, which then takes two merges, because ArgoCD reads the annotation from the live object.
  REJECTED: `creationPolicy: Orphan` on the ExternalSecrets — the values come from AWS again when an ExternalSecret returns, and an orphan outlives the definition that explains it.
  REJECTED: a list of removals that are allowed — whether the objects of the kind are still rendered answers it without a list.
  INVARIANT: the list of outside groups holds no entry that is not needed — an entry whose CRD is rendered, or whose group nothing uses, fails. That nobody adds one is unenforced.
  NON-GOAL: the removal or rename of a whole Application — ArgoCD v2.13 leaves CRDs out of the cascading deletion of an Application (`shouldBeDeleted` in its application controller), so they stay, and a deliberate removal of them is then done by hand. A raise of ArgoCD re-reads that function.
  NON-GOAL: a removal that does not go through git — a person with cluster access is not held by CI.
  NON-GOAL: the version of an outside group — its CRD is not in the render, so only its presence in the list is known.
- @deploy @crd @schema — CI renders a chart's `crds/` directory, as ArgoCD does, and does not validate a CustomResourceDefinition against a schema, because the pinned schema set holds none for that kind. The API server is the first to judge an upstream CRD.
  REJECTED: vendoring a schema for CustomResourceDefinition — the objects are upstream's and reach CI unchanged.
- @deploy @region — A chart that needs the region's name reads it from a ConfigMap of the own chart, so its Application stays identical in both regions.
  REJECTED: letting an Application differ per region — the comparison that keeps the two regions' charts the same would have to know which difference is meant.

## Takeaway
- takeaway: guard a deletion by asking whether anything still uses the thing, and the deliberate case needs no exception.
