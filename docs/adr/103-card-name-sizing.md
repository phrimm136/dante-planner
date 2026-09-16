# 103 card-name-sizing
epic: none · pr: none

## Decisions
- @cards @text — Each card gets its own sizing rule: identity names hold a fixed size and grow upward into their own box, EGO names fit within two lines, and theme pack names fit on one line with no lower bound but the floor. The three rules are what the game does, and a single shared rule would have to be the loosest of them. REJECTED: one bounded two-line fit everywhere — it silently truncates theme pack names the game shows in full.
- @cards @text — A name that cannot fit at the floor size overflows rather than shrinking further. A floor exists so a long name stays legible; ignoring it to force a fit trades the one property the floor was for. REJECTED: shrinking without a floor — a long name becomes unreadable instead of overflowing visibly.
- @cards @text @i18n — English display text carries no per-character letter spacing. The game applies none in any language, and the spacing was a local embellishment that every measurement then had to account for. REJECTED: keeping it and feeding it to the measurer — it makes every English width depend on a value with no source in the game.
- @cards @text @i18n — Chinese takes the Korean name-underlay row. The game ships no Chinese display face and therefore no underlay material to transcribe, and Korean is the row whose face the Chinese one is drawn closest to. REJECTED: leaving Chinese undecorated — the name then reads flat against art that every other language's name stands out from.

## Superseded
- @cards @text → 104 — A card name's font size comes from a pure fit function over an injected measurer, not from a DOM probe. A probe renders the name, reads the layout back, and renders again, which costs a second layout pass per card and cannot be tested without a browser; a measurer over a canvas context answers the same question from text, size and face alone. REJECTED: rendering at the maximum size and scaling the element down by the overflow ratio — it distorts the stroke weight along with the glyphs.
- @cards @text @i18n → 104 — The measurer is withheld until the language's display face has loaded, and a name sizes only once that face is real. Measuring against a fallback face yields widths the rendered card never has, so the name would size twice and visibly jump. REJECTED: measuring immediately and re-measuring on the font-loaded event — the same two passes, with a flash in between.

## Takeaway
- takeaway: put text measurement behind a function of (text, size, face) and the layout rule becomes a pure, testable one; what is left at the seam is only "which face, and has it loaded".
