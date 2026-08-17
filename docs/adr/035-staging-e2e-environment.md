# 035 staging-e2e-environment
epic: none · pr: none

## Decisions
- @staging @hostnames — The environment is reached exclusively through Cloudflare Tunnels on a dedicated SPA hostname and API hostname, with a per-region pin for each region. The origin fleets accept no public inbound and cloudflared dials out, so traffic either traverses a tunnel or does not exist, and a load balancer steers by the client's geography rather than by the tester's intent. REJECTED: header-spoofed region selection — the edge writes `CF-IPCountry` and `CF-Connecting-IP` from the observed connection and rejects spoofing upstream, so a deterministic pin has to exist at DNS and tunnel level. REJECTED: the paid load balancer from the first phase — it serves only the steering and failover scenarios, which no suite in that phase runs, while per-region records give the same determinism for nothing.
- @staging @hostnames @secrecy — The hostnames are absent from this record and from the repository. They front an environment whose issuer authenticates whoever asks, and the repository is public, so naming them publishes the way in; they live in untracked tfvars and reach the suites through Secrets Manager.
- @staging @oauth — The suites authenticate with a staging-only RS256 keypair and exercise the OAuth path against an in-cluster stub issuer on its own hostname, leaving the real integration to a one-time manual smoke. Google's login page cannot be driven reliably by automated browsers, so a gate that depends on it is a gate that fails for reasons unrelated to the change. REJECTED: real Google OAuth in the gate — flake and lockout risk aside, it cannot produce the tampered-state and replayed-code cases at all, because Google will not misbehave on demand. REJECTED: test tokens everywhere — leaves the callback, a mutating GET that must mint the read gate cookie, and the whole transaction seal untested in any deployed environment.
- @staging @spa — The built SPA is served by an nginx pod behind the same tunnel. A Pages-scoped API token does not exist in the toolchain, and serving the SPA elsewhere splits the entry surface across two mechanisms.
- @staging @drift — Staging-only workloads are applied outside the synced tree, so the cluster deliberately drifts from git for the environment's lifetime. This is acceptable only because the environment is destroyed at phase end; a standing environment would need them in the tree.
- @staging @stub — The stub issuer mints unsigned id_tokens, which is sound only because the backend decodes claims from the token it received over its own connection to the issuer, and which means signature-verification regressions are invisible to these suites.
- @staging @availability — Suite runs depend on Cloudflare, so an edge incident reads as a staging failure.

## Superseded
- @staging @environment → 036 — Staging is a 1:1 copy of production in its own AWS account.

## Takeaway
- takeaway: an environment that authenticates whoever asks is only as private as its hostnames, so the hostnames become a credential — and a credential that half the toolchain wants to print in a log, a manifest, or a decision record.
