# 557 third-party-components-are-installed-from-upstream-charts
epic: none · pr: none

## Decisions
- @deploy @helm @third-party — A component the project does not write is installed from its upstream Helm chart: one ArgoCD Application per component, pinned to a chart version, with its values inline in the Application. The project's own manifests are one chart of their own. The hand-written copies drifted from upstream without anyone seeing it. The copy of kube-state-metrics lacked a permission that upstream grants, so one metric never existed and the alert built on it could never fire. No image in these copies had a digest or anything that raised its version.
  REJECTED: kustomize's Helm inflation inside the overlays — ArgoCD and CI each need a flag to allow it, and the chart then renders inside the root Application, so one broken chart blocks every other resource.
  REJECTED: rendering each chart once and committing the output — a version raise becomes a regenerate step someone must remember, and the diff of a raise is the whole manifest.
  REJECTED: keeping the hand-written manifests and pinning image digests — a bot then raises the image, but the permissions, arguments and probes that change with a release stay ours to copy.
  REJECTED: values in a separate file — ArgoCD reads a values file from the chart's own repository unless a second source is added, and a second source is a field the CI renderer does not model.
  INVARIANT: CI renders every chart with the values the Application carries, validates the output against schemas and scans it like any other manifest — the renderer reads the rendered Application, and fails on any Helm field other than the release name and inline values.
  INVARIANT: an Application is identical in both regions — the test stage compares the Applications of the two regions' renders and fails on a difference.
  INVARIANT: a chart that cannot sync stops retrying — the Application's retry is bounded, so a broken chart ends in a failed sync and does not retry forever.
  NON-GOAL: values per region — no installed chart needs them; the comparison above must change when one does.
  NON-GOAL: a cluster test of the chart's objects before merge — the behavior stage covers the project's own manifests only, and the first proof of a chart raise is the sync in production.
- @deps @renovate — Renovate raises what Dependabot cannot read: chart versions in Applications, the pinned k3s release, and the pinned images and schema commit of the CI scripts; Dependabot keeps every ecosystem it already covers. A release must be a week old before a raise is proposed, where the source gives a release date. The k3s release, the k3s image the tests run and the schema commit move as one group that needs approval on the dashboard, because a k3s raise reaches a node only when that node is replaced.
  REJECTED: moving every ecosystem to Renovate — the existing updates work, and one bot with two config dialects is not worth the migration.
  REJECTED: merging chart raises automatically — no test before merge runs the chart's objects in a cluster.
  REJECTED: holding an image or commit raise until it is a week old — most registries and every git ref give no release date, and Renovate then holds such a raise forever; a person still merges each one.
  INVARIANT: the k3s release that nodes install and the one the tests run are the same — the schema stage of the deploy test compares the two pins and fails on a difference.
  INVARIANT: the Helm that CI renders with keeps the major version of the Helm inside ArgoCD — Renovate's allowed versions for that image stop at the current major; unenforced against an ArgoCD raise.
  INVARIANT: Renovate opens changes only through the managers its config enables — the config lists them, and an invalid config is reported by the app as an issue in the repository.
- @config-scan @baseline @helm — A baseline entry for a chart-rendered Pod leaves out the chart and app version labels. The scanner builds a Pod's identity from all its labels, so every chart raise renamed each accepted finding, and a baseline that only shrinks cannot take a renamed entry without a hand edit.
  REJECTED: accepting chart findings with an annotation, as for the project's own manifests — a chart's objects carry only the annotations its values expose.
  INVARIANT: a raise that changes only the version labels leaves the baseline unchanged, and a Pod that differs in any other label is a new finding — both are scenarios in the scan's own test.

## Takeaway
- takeaway: a copy of someone else's manifest is a fork with no upstream remote; what it silently lacks shows up as a thing that never happens, which no test of what does happen can find.
