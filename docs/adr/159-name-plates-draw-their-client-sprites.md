# 159 name-plates-draw-their-client-sprites
epic: none · pr: none · supersedes: 156 @nameplate

## Decisions
- @skill @passive @nameplate — Each name plate draws its own client sprite, sliced horizontally into a fixed left cap, a stretched centre and a fixed right cap at the sprite's borders: a skill plate is `BattleUI_CoinPopup_skillName` 69.33 units tall (caps 11.25 and 123.75 units) with its white body multiplied by the attribute colour, a passive plate is `New_MainUI_PersonalityList_2_46` 72.74 units tall (caps 112 and 171 units). The name inside keeps its own node (skill 0–45 with margins 20/50, passive 25–40 with margins 30/100), and both names centre on their plate by TextMeshPro's geometry rule. The shared passive shape put a skill name's plate 14 units deeper than the sprite's body and drew a gradient tail whose dark part sat 75 to 90 units left of the sprite's, so names looked raised and the tail began under the name's last glyphs. REJECTED: re-deriving the gradient tail from the sprite — a hand transcription of a picture the client ships, which drifted once already; REJECTED: one shared plate shape — the two prefabs differ in sprite, height, size range and margins, and the phenomena came from sharing them.
  INVARIANT: no plate colour, tail or height is transcribed into gradients — plate style tests read the sprite pieces and their per-kind sizes.
  NON-GOAL: the skill name's glyph-box midline (TextMeshPro centres each glyph's metric box; the page's font tables carry advances only).

## Takeaway
- takeaway: when the client draws an element from a sprite, ship the sprite; a transcription of its shape is a second source that drifts from the first.
