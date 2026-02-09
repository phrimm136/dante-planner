# Learning Reflection: JWT Token Hardening (RS256 + AES-GCM)

## What Was Easy

- RS256 adoption via JJWT library — native API with no additional dependencies
- AES-GCM Java standard library availability — javax.crypto.Cipher eliminates external deps
- Spring @PostConstruct validation pattern — aligned with existing JwtProperties structure
- Test key injection via setters — bypass @PostConstruct without mocking Spring context
- Standard exception mapping preserved — four existing catch blocks handle new cipher failure modes

## What Was Challenging

- PEM file format handling — no existing precedent in codebase (APPLE_PRIVATE_KEY_PATH declared but never consumed), required learning KeyFactory/PKCS8EncodedKeySpec chain
- IV uniqueness guarantee under load — SecureRandom quality varies by platform; ThreadLocal pattern needed to prevent contention at scale
- Encryption boundary isolation — standard JWT claims (sub/iat/exp) must remain outside "enc" blob to allow JJWT's automatic expiry validation during parseSignedClaims()
- Three secondary test JwtProperties instances — each test that needed different keys required explicit setter updates (invalid-signature test needs independent keypair)

## Key Learnings

- Asymmetric signing audit trail — RS256 private key compromise does NOT invalidate public key distribution; key rotation design must include versioning strategy
- GCM catastrophic failure risk — IV reuse with same key breaks confidentiality completely; per-token IV generation non-negotiable, but scale (1000 req/sec) makes reuse statistically negligible
- Fail-fast startup validation — blank property checks in @PostConstruct catch missing SSM/env vars at boot time, preventing silent key loading failures in production
- ThreadLocal SecureRandom contention — SecureRandom is synchronized; shared instance degrades performance under concurrent token generation; ThreadLocal pattern essential above 100 req/sec
- JSON serialization as encryption intermediary — ObjectMapper bridges claims map → JSON bytes → AES ciphertext; standardizes claim format independent of JWT library version
- Token opacity test necessity — Base64 decoding JWT payload segment reveals nothing when encrypted, but requires explicit test to prevent regressions if someone removes encryption layer

## Spec-Driven Process Feedback

- Research mapping accurate — all clarifications in research.md (IV layout, PEM formats, test bypass) matched actual implementation requirements
- Plan execution order sound — Phase 1→2→3→4 progression aligned with dependency chain; no reordering needed
- One plan ambiguity resolved — inline secondary test JwtProperties instances needed explicit setter handling; plan mentioned three instances but didn't prescribe independent keypair for invalid-signature test (realized during implementation)
- Property naming conventions documented — ${ENV_VAR:default} pattern from research prevented guessing during configuration step

## Pattern Recommendations

- Establish PEM loader utility — JwtProperties @PostConstruct contains reusable KeyFactory chain; extract to static helper for OAuth or certificate pinning use cases
- Document ThreadLocal SecureRandom — contention issue is non-obvious; add security patterns doc explaining when shared instances fail
- Add cipher factory extraction pattern — createEncryptCipher/createDecryptCipher helpers reduce duplication; document as strategy for algorithm switching
- Standardize failing-gracefully for PostConstruct — blank-check validation pattern prevents silent startup hangs; codify in configuration loading patterns doc

## Next Time

- Pair PEM loading with circuit breaker from day one — key file read errors should retry with backoff, not hard-crash
- Plan two rounds of secondary test instance updates — invalid-signature test discovery that it needs independent keypair happens late in Phase 4
- Add iv-uniqueness statistical validation in production — metric collection on ciphertext entropy would catch platform-specific SecureRandom failures before they surface
- Document key rotation design debt upfront — acknowledge versioned-key storage as prerequisite for multi-region or blue-green deployments before shipping to production
