# 188 resistance-levels-follow-the-client-cuts
epic: none · pr: none · supersedes: none

## Decisions
- @unit-status @resist-level — A resistance multiplier maps to its level by the client's cuts (`UnitModel.GetAttributeResistLevelByType` @0x1814ec7f0 and `GetAtkResistLevelByType` @0x1814ec6e0, client 4540518d06efd18e, float32 constants): v ≤ 0 immune, v ≤ 0.51 ineffective, v < 0.99 endure, v < 1.01 normal, v ≤ 1.51 weak, otherwise fatal. A blank resistance (ADR 187) is its own level that renders "-" without a multiplier. Every page that shows resistances (identity, E.G.O, battle) uses this one mapping. REJECTED: the previous bands (endure from 0.75, nothing for 0) — 193 shipped values showed a different level than the game (0 as ineffective instead of immune; 0.6 and 0.7 as ineffective instead of endure).
- @unit-status @resist-colour — Each level takes the client's colour from `ResistValueColors_AbnormalityGuideBook` (`UnitInformationResistSlot.SetColorByResistVal` @0x180ee05a0): immune and ineffective #808080, endure #CCCCCC, normal and blank #E4C393, weak #FF8D8D, fatal #FF4040, on both the word and the multiplier. REJECTED: the Tailwind palette — it matched no client colour.
  INVARIANT: every distinct multiplier in the shipped data (twenty values, 0 to 2) has a case in the resistance mapping table test, at the client's level.

## Takeaway
- takeaway: copy a client threshold with its comparison operator; "≤ 0.51" and "< 0.99" are different rules from "≥ 0.75".
