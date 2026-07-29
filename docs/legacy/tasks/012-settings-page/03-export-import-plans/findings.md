# Learning Reflection: Planner Export/Import

## What Was Easy

- Pattern reuse: SyncSection Suspense wrapper, BatchConflictDialog, usePlannerStorage hooks aligned perfectly
- pako compression already proven in deckCode.ts - only needed type assertion for header option
- Browser Blob/File APIs worked naturally; magic byte validation provided clear integrity check
- Four-language i18n support added without architectural friction
- ExportEnvelopeSchema via Zod required only structural checks, no versioning complexity

## What Was Challenging

- Device ID recovery logic: getOrCreateDeviceId() can return invalid UUID, requiring validation + retry + fallback
- Partial import semantics: tracking imported/skipped/errors across three phases (save, conflict, resolution)
- Progress indicator timing: distributing stages (10%, 20%, 40%, etc.) required empirical estimation
- BatchConflictDialog hardcodes "Server" labels - passed imported planner with clarifying comment
- 10MB file size limit chosen empirically to prevent browser memory exhaustion

## Key Learnings

- Defensive device ID handling: multi-layer fallback pattern for any device-scoped operation
- Gzip magic byte validation prevents cryptic pako errors on malformed input
- Suspense wrapper at component export boundary (not within Content) prevents hook misuse
- Conflict semantics (Local vs Imported) require careful labeling in callsite comments
- Separating PlannerExportItem (portable) from SaveablePlanner (device-bound) made deviceId logic explicit
- DOMPurify sanitization is destructive - only safe for titles, not content fields

## Spec-Driven Process Feedback

- research.md file-to-pattern mapping was 100% accurate - zero surprises
- plan.md execution order (constants → types → schemas → component → i18n) prevented circular deps
- Spec ambiguity: "light validation" didn't define severity for version mismatch
- Edge case coverage: partial import + conflict + error recovery combinations not enumerated

## Pattern Recommendations

- Device ID Recovery: document retry + UUID validation + fallback for device-scoped storage
- Gzip Format Validation: add magic byte check pattern before decompression
- Progress Stage Distribution: empirical calibration ~20% per phase for predictable UX
- DOMPurify Integration: document XSS prevention pattern (sanitize on import, not storage)

## Next Time

- Pre-implementation audit of getOrCreateDeviceId() callsites for error handling
- Add manual test step for progress bar UX with 100+ planners
- Request parameterized dialog labels before reusing conflict dialogs
- Include decision journal in spec for "what happens if X fails" scenarios
