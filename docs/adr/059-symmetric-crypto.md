# 059 symmetric-crypto

## Decisions

- @crypto — `AesGcmCipher` stays as the single symmetric cipher factory. The only
  bespoke code in it is the IV-prefix wire framing; algorithm, mode, tag length, and
  randomness come from the JDK, and the fresh-IV and fail-closed properties hold.
  REJECTED: Google Tink — buys AAD and keyset rotation at the cost of a new dependency
  and a keyset-format migration for the existing env-var key, serving one call site.
  REJECTED: Nimbus JWE, though already on the classpath — replaces the whole
  encrypt-then-sign OAuth transaction format, a rewrite disproportionate to the risk.
- @crypto — The CSRF key derivation becomes full RFC 5869 HKDF (extract, then expand),
  validated against the RFC's published test vectors. Expand-only over a uniform
  secret is defensible in theory, but it leaves the derivation nonstandard and
  unverifiable against any external vector. Consequence accepted: derived keys change
  once, invalidating live CSRF tokens at deploy; clients re-issue on the next page
  load.
  REJECTED: BouncyCastle or Tink for the KDF — a dependency for thirty testable lines.

## Takeaway

- takeaway: judge hand-rolled crypto by which primitive it replaces, not how many
  lines it is; framing bytes around JDK primitives is reviewable, a nonstandard KDF is
  not — anything derived must match a published spec with test vectors.
