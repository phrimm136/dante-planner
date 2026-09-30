# 157 rich-text-follows-tmp-tag-state
epic: none · pr: none · supersedes: none

## Decisions
- @richText @tmp — Game text is read the way TextMeshPro reads it: tags change a running state at their position (colour, mark and size stacks; bold, underline and strike counters; state carried across line breaks) and each stretch of text renders with the state it was written under. One scanner does this for every reader on the site and replaces the grammar tokenizer. The client renders all text through TextMeshPro, and authored battle text crosses and leaves tags open (`<s>` opened under one colour and closed under another, a `</u>` with no opener). REJECTED: a balanced-element grammar — it cannot express crossing tags; REJECTED: a state mode inside the grammar engine — two reading models in one engine, with readers still choosing between them.
  INVARIANT: no raw tag reaches the DOM and each run carries the state TextMeshPro would give it — scanner tests and a shipped-text pin.
- @richText @mark — A mark run draws the mark colour behind the text and mixes every coloured glyph inside it, keyword chips included, toward the mark colour at the mark's alpha. TextMeshPro draws the mark as a translucent quad over the glyphs: in-game an `#f8c200` chip under `#ff000040` reads as `rgb(250, 145, 0)`. REJECTED: a background colour alone — chips keep their unmixed colour, unlike the client.
  INVARIANT: a coloured descendant of a mark run is mixed toward the mark — browser colour check.

## Takeaway
- takeaway: when the source format is a state machine, read it as one; a tree reader only works while authors happen to nest.
