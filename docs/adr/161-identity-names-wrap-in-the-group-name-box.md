# 161 identity-names-wrap-in-the-group-name-box
epic: none · pr: none · supersedes: 105 @cards @text (wrap box)

## Decisions
- @cards @text — An identity name wraps against a 241.65-unit box, drawn by `[Text]GroupName` (240-unit prefab rect); `[Text]GroupUpperName` does not draw the segment that wraps. Under the card's -2 letter and -5 word tracking and the shared text measure, every confirmed in-game break (the EN identity list and the twelve Japanese formation cards, 74 in all) holds only for a box from 241.45 to 241.85 units, and 241.65 is that interval's middle; the measure reads about 0.7 % wide of the prefab box, cause unverified. REJECTED: the 240-unit prefab box — 43 confirmed breaks fail; REJECTED: `[Text]GroupName`'s -5 as letter spacing — no box from 230 to 243 units fits the breaks; REJECTED: 242 units — outside the interval once the measure charges spacing between code points only.

## Takeaway
- takeaway: fit a constant to every confirmed fact at once and keep the interval's middle; a bracket from one reference set moves when the measure under it changes.
