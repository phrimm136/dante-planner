# 166 the-latin-display-face-ships-as-a-renamed-ofl-build
epic: none · pr: none

## Decisions
- @fonts @text — The Latin display face is the game's own Mikodacs with its hinting removed, renamed PlannerLatin in every name record and shipped with the OFL text and a modification note, built reproducibly from the game's file by a script in the frontend. The game's file is the geometry the advance and ink tables describe, but Chromium rejects its CFF charstrings, and the OFL forbids a modified version from carrying the reserved name Mikodacs. REJECTED: the game's file as is — Chromium's font sanitiser refuses it and the page falls back to another face; REJECTED: the previously served copy — re-hinted and rescaled to 1000/1024 of the game's size while keeping the reserved name, it drew English text 2.3 % small; REJECTED: a size-adjust over that copy — it keeps a modified font under the reserved name.
  INVARIANT: the served Latin face matches the game font's units, advances and outlines — the build script asserts them.

## Takeaway
- takeaway: a font's licence travels with every copy you make of it, including the one a build tool rewrote.
