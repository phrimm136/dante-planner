# 014 rds-provisioning-guards
epic: none · pr: none

## Decisions
- @rds @terraform @destroy-guards — The database instance carries `prevent_destroy`, `deletion_protection` and `skip_final_snapshot = false` together, and `manage_master_user_password` so the credential lands in Secrets Manager rather than in state. REJECTED: console click-ops — it drifts, is not reproducible, and defeats the point of describing infrastructure as code. The three guards overlap deliberately: the first fails the plan, the second fails the API call, the third makes even a successful deletion recoverable.
- @rds @tls — Clients connect with `VERIFY_CA` against the Amazon RDS CA bundle, mounted read-only alongside the signing keys. REJECTED: `useSSL` alone — it encrypts the transport without authenticating the endpoint, which stops passive interception and nothing else.
- @iam @provisioning @separation — Infrastructure provisioning uses a dedicated least-privilege credential assumed through STS, distinct from the runtime and session-access credentials. REJECTED: long-lived access keys — short-lived assumed-role credentials remove the standing secret. REJECTED: reusing the runtime identity — operating a running box and provisioning the infrastructure under it are different blast radii and should not share a principal.

## Takeaway
- takeaway: guard what cannot be rebuilt and leave everything else genuinely rebuildable. Guards on disposable infrastructure are the ones that get removed under pressure, which is how the habit of removing guards gets established.
