# 026 fe-bundle-invariants
epic: none · pr: none

## Decisions
- @barrel @tree-shaking — There is no aggregate barrel: each slice exposes only its own entry module, and every re-export uses the named `export { x } from './x'` form. REJECTED: a top-level barrel re-exporting the slices, and REJECTED: star re-exports — either one drags the rich-text editor into the entry chunk, and it takes only one eagerly-imported shared hook reaching four slice symbols to do it.
- @schemas @locality @lazy — The document schemas live inside the lazily-loaded slice rather than in the shared schema barrel, which makes the editor-stays-out-of-the-entry-chunk rule structural instead of a comment asking future readers not to re-export something.
- @format @byte-parity — Data and translation JSON are excluded from the formatter. Translation strings carry zero-width trap-street markers that must survive byte-identically, and while formatters of this class do not rewrite string contents, the exclusion is cheap and the failure would be silent and unrecoverable.

## Takeaway
- takeaway: a bundle-size invariant enforced by a comment lasts exactly until someone adds a convenient re-export. Making it structural — the module physically lives inside the lazy boundary — is the difference between a rule and a hope, because the import graph then cannot express the violation.
