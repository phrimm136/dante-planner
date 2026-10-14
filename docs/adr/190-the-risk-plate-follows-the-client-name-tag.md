# 190 the-risk-plate-follows-the-client-name-tag
epic: none · pr: none · supersedes: none

## Decisions
- @battle @risk-plate — The header draws the risk-level plate the way the client's abnormality name tag does (`UnitInformationAbnormalityNameTagUIData..ctor` @0x180ec6ec0, `UnitInformationAbnormalityNameTag.SetData` @0x182810e50, client 4540518d06efd18e). The parsed badge is one of: a class (ZAYIN to ALEPH) without grade, drawn with `BattleUI_CreatureBattle_3_{0,12,15,22,26}`; a class with grade, drawn with `_34` to `_38` and the text "-NN" in #f8c200 (#ff9600 for WAW, #ff0000 for ALEPH); SUPPLEMENT, drawn with the blank plate `_39` and "-NN" in the larger none-class slot in #F8C200; HIDDEN, drawn with `_18`; UNDEF, drawn with the language's level-number plate (`MainUI_Formation_9_27` KR, `_31` EN, `_28` JP); HYPHEN, drawn with `MainUI_Formation_9_33`. Text placement follows the prefab `Prefab_UnitInformationAbnormalityNameTag/[Image]AbClassRank` (plate 188.46×80.41; grade box at (70, 14), 50×40, size 40; none-class grade box at (58, 8), 65×55, size 55). REJECTED: drawing only the five no-grade plates and the grade as a separate span — graded units showed the wrong plate, and 466 rows lost their plate entirely (SUPPLEMENT, HIDDEN, UNDEF, HYPHEN).
  INVARIANT: every badge value in the shipped battle data maps to a plate — the badge schema parse rejects an unknown class.
  NON-GOAL: CHOI, which no shipped data carries.

## Takeaway
- takeaway: a placeholder the client draws (UNDEF, "-", "???") is a value of the badge, not a missing badge.
