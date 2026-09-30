# 161 identity-names-wrap-in-the-group-name-box
epic: none · pr: none · supersedes: 105 @cards @text (wrap box), 102 @cards @text

## Decisions
- @cards @text — An identity name wraps against a 241.65-unit box with -2 letter and -5 word tracking, the settings of the identity list item `PersonalitySwitchableSlot`, whose `[Text]GroupName` (240-unit prefab rect) draws the wrapping title; `[Text]GroupUpperName` only receives the one-line title, which every shipped entry leaves empty, and no client code rewrites either node's spacing. Under that tracking and the shared text measure, every confirmed in-game break (the EN identity list and the twelve Japanese formation cards, 74 in all) holds only for a box from 241.45 to 241.85 units, and 241.65 is that interval's middle; the measure reads about 0.7 % wide of the prefab box, cause unverified. REJECTED: the 240-unit prefab box — 43 confirmed breaks fail; REJECTED: the formation slot's `[Text]GroupName` (-5 letter and word spacing, 230-unit box) — no box from 230 to 243 units fits the breaks; REJECTED: 242 units — outside the interval once the measure charges spacing between code points only.

## Takeaway
- takeaway: fit a constant to every confirmed fact at once and keep the interval's middle; a bracket from one reference set moves when the measure under it changes.
