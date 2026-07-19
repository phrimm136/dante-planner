# Phase 03 (local-tdd)

## Rows
- globals-references-subset-css: frontend/src/styles/__tests__/fonts.test.ts#globals-imports-pretendard-subset-and-drops-full-font-face — its font declarations are parsed
- fonts-byte-unchanged: scripts/perf/lib/font-diff (git status --porcelain frontend/src/assets/fonts) — assets/fonts is compared against its base-commit state

## Touches
- frontend/src/styles/globals.css

## Validation
- `yarn --cwd frontend vitest run src/styles`

## Seams (drives)
- frontend/src/assets/fonts/** -> byte-identical before/after
- globals.css -> imports pretendard-subset.css, no hand-written full Pretendard @font-face

## DECISION SLOTS
SLOT ordering-rationale: Single seam (`globals.css`). Write `globals-references-subset-css` red first (current globals declares the full three-format @font-face), then swap the declaration to import the vendored `pretendard-subset.css`. `fonts-byte-unchanged` is a standing gate, checked not driven — D6 forbids touching any binary, so the edit is CSS-only.
SLOT batch-boundary: One red-green cycle for `globals-references-subset-css`; `fonts-byte-unchanged` rides as a must-stay-green gate over the same CSS edit (which touches no font binary). Single atomic batch — the phase is one stylesheet change.
SLOT fallback-scope: No stub surface (no new interfaces). The phase is atomic; if blocked, revert the CSS edit — nothing partial to stub, and `fonts-byte-unchanged` guarantees no binary was left mutated.
