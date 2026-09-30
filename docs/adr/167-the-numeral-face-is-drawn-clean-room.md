# 167 the-numeral-face-is-drawn-clean-room
epic: none · pr: none

## Decisions
- @fonts @skill @typography — The page's numeral face (coin scale, base power and the other game numbers) is PlannerNumerals, an original font drawn clean-room and shipped under the SIL OFL: one writer measured the game numerals' proportions from rendered images only and wrote a dimensional specification, and a separate builder that never saw the reference images or any font data generated every outline parametrically from that specification, with the specification and the generator kept beside the font as its provenance. The game's numeral face is a renamed copy of a 1992 commercial font whose software copyright runs for decades, while typeface designs themselves are not protected by copyright in the United States or Korea and the design terms that exist elsewhere lapsed by the mid-2010s, so reproducing the appearance is open and copying the file is not. REJECTED: keeping the game's copy — a redistributed commercial program under a free-font label; REJECTED: licensing the commercial original — priced by pageview per year, excluded from open-source repositories, and forbidding modification; REJECTED: a free lookalike face — its shapes read as a different font beside the game's; REJECTED: images cut from the game's glyph atlas — a second rendering path for numbers that must be thresholded per size to stay sharp.
  INVARIANT: no numeral outline comes from another font's data — revisions change only the specification and regenerate the font with its generator.
  NON-GOAL: point-level identity with the game's face; the target is no visible difference at the sizes the page draws.

## Takeaway
- takeaway: a free-font label is a claim by whoever uploaded the file; trace a font to its publisher before shipping it, and when the trace ends in a commercial original, redraw the design rather than keep the file.
