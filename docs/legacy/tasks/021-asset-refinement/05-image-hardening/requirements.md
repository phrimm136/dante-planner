> **BEFORE COMMIT**: The implementing agent MUST censor this file before committing:
> 1. Remove all descriptions of scrambling algorithm, WASM internals, canvas tainting mechanism
> 2. Remove security architecture rationale
> 3. Keep only generic descriptions (e.g., "images processed for display")
> 4. Delete this notice itself after censoring

# Task: Phase 5 — Image Hardening

## Progress (completed in prior session)

- [x] `ProtectedImage` component — CSS `background-image`, blocks right-click save
- [x] `<img>` → `ProtectedImage` migration — 56 component files
- [x] `ScrambledImage` component — Canvas `drawImage`, tile positions not in DOM
- [x] `ThemePackCard` wired to `ScrambledImage`
- [x] `scramble_images.py` — constrained shuffle, `derive_seed()` function (not yet wired into main)
- [x] WASM crate — `get_permutation`, opaque predicate, fake tables
- [x] `wasmLoader.ts`, `SCRAMBLE_GRID` constants, asset path helpers

## Decisions
- **Canvas rendering** — `drawImage` produces opaque pixels. CSS `background-position` approach leaks permutation via DOM inspection.
- **Cross-origin canvas tainting** — scrambled images served from `assets.dante-planner.com` (R2, Phase 1) without CORS headers. Canvas becomes tainted — `toDataURL()` throws SecurityError.
- **Constrained shuffle** — no fixed points, no preserved adjacencies. Random shuffle allowed recognizable tile patterns.
- **Time+salt+imageID seed** — `SHA-256(salt + timestamp + image_id)`. Irreversible, different permutations per deploy.

## Description

Finalize image protection by enabling cross-origin canvas tainting and wiring the scramble seed improvements.

1. Wire `derive_seed()` into `scramble_images.py` main — replace `SEED_PREFIX`
2. Generate `secret.key` (32 bytes for `MASTER_SECRET_IMAGE`)
3. Update `ScrambledImage` — remove `crossOrigin = 'anonymous'`, use cross-origin URL
4. Update asset paths — all image paths prefixed with assets subdomain in prod
5. Replace expand button (`window.open`) with lightbox overlay — keeps images inside ProtectedImage, no direct URL exposure
6. Rebuild WASM + re-scramble images
7. Upload scrambled images to R2

Depends on Phase 3 (Asset Worker at `assets.dante-planner.com`).

## Scope

Read:
- `frontend/src/components/common/ScrambledImage.tsx` — Canvas rendering, `crossOrigin` attribute
- `frontend/src/lib/assetPaths.ts` — ThemePack path helper
- `frontend/src/lib/wasmLoader.ts` — WASM loading
- `static/scripts/scramble_images.py` — current scrambler
- `static/descrambler/src/lib.rs` — WASM crate

## Target

Create:
- `static/descrambler/secret.key` — 32 bytes random (private static repo)
- `frontend/src/components/common/ImageLightbox.tsx` — fullscreen overlay using ProtectedImage, replaces `window.open` for image expansion

Modify:
- `frontend/src/components/common/ScrambledImage.tsx` — remove `crossOrigin = 'anonymous'`
- `frontend/src/lib/assetPaths.ts` — all image paths return cross-origin URL in prod
- `frontend/src/components/identity/IdentityHeader.tsx` — replace `window.open(imagePath)` with lightbox
- `frontend/src/components/ego/EGOHeader.tsx` — same lightbox replacement (if expand exists)
- `static/scripts/scramble_images.py` — wire `derive_seed()`, remove `SEED_PREFIX`
- `static/descrambler/build.rs` — embed `MASTER_SECRET_IMAGE` from `secret.key`

## Done When

- [ ] `secret.key` generated and committed to private static repo
- [ ] `scramble_images.py` uses `derive_seed(salt, timestamp, image_id)`
- [ ] `canvas.toDataURL()` throws SecurityError in production
- [ ] ThemePack images render correctly (tiles assembled, no visual difference)
- [ ] Dev mode: same-origin, canvas NOT tainted (for debugging)
- [ ] Expand button replaced with lightbox overlay (no `window.open` to direct image URL)
- [ ] Lightbox renders via ProtectedImage (right-click blocked)
- [ ] Scrambled images uploaded to R2

## Test Plan

### Manual
1. Navigate to ThemePack page → images render correctly
2. Console: `document.querySelector('canvas').toDataURL()` → SecurityError
3. Dev mode: same test → returns data (not tainted)
4. Click expand → lightbox opens (no new tab, no direct URL)

### Edge Cases
- [ ] R2 unavailable → canvas stays empty, no crash
- [ ] Scramble script idempotency → always regenerate originals first
