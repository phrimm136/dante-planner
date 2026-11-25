# 547 config-findings-are-held-in-a-baseline-that-only-shrinks
epic: none · pr: none

## Decisions
- @scan @ci @ratchet — A pull request that changes `deploy/` or `terraform/` runs Checkov over the tracked Terraform files and over the render of both regions, and compares the findings with two committed baselines in both directions: a finding the baseline lacks fails, and a baselined finding that is gone fails until the baseline drops it. The configuration held hundreds of stock-check findings on the day the scan was added (host networking, images without a digest, containers without a readiness probe, pods without a NetworkPolicy, an unencrypted snapshot bucket), every one found by reading, so a gate that demanded zero could not be turned on and a gate that only reported would be ignored. Re-check: `.github/scripts/config-scan.sh`.
  REJECTED: Trivy in config mode — its ignore entries are a check id plus a file, so a new violation of an already-ignored id in the same file passes, and its Kubernetes findings carry no resource name to key a baseline on.
  REJECTED: Checkov's own `--baseline` flag as the gate — it blocks new findings only, so a fixed finding stays listed and its entry then hides the same violation coming back.
  REJECTED: a list of skipped check ids — it turns a check off for every resource, including the ones added later.
  INVARIANT: no finding enters a baseline through the tool — `config-scan.sh --update` rewrites a baseline only when the fresh scan adds no entry (`.github/config-scan/test.sh`, "update shrinks and never grows"); a finding accepted on purpose is recorded on the resource, with a `checkov:skip` comment in Terraform or a `checkov.io/skip<N>` annotation on a Kubernetes object, because a YAML comment does not reach the rendered object.
  INVARIANT: a scan that covered nothing is a failure, not a pass — the script exits 2 when Checkov reports no resources or a parsing error, or when a region fails to render; Checkov itself reports an unparsable manifest as an empty, successful scan.
  NON-GOAL: fixing the baselined findings — each belongs to the unit that changes the resource, and the baseline shrinks there.
  NON-GOAL: rules stock checks do not carry (a ClusterRole that reads every Secret, a security group rule left open, an instance metadata hop limit) — those need a check written for this repository.

## Takeaway
- takeaway: a gate added to a system that already breaks its rule needs a recorded starting point, and the record must fail when it is wrong in either direction, or it turns into a list of permissions.
