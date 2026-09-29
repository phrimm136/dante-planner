# 155 client-units-follow-the-description-font
epic: none · pr: none · supersedes: 154 @importance

## Decisions
- @skill @importance — Client UI units map to page length through the description font: the unit information panels set their description TextMeshPro to fontSize 40 with auto-size off, so 40 client units are one text-sm em (0.875 rem), for every client-sized element. The importance triangle is its prefab rect, 200 units square: a skill's sits 86 units in from the box's right edge and 22 units down (the `ImportanceUI` unlock position, −86/−37, inside an importance group that overhangs the panel by 15); a passive's sits at the right edge, 3 units above the box (0/−37 inside a group overhanging by 40). REJECTED: fractions of the box width — the client panel's width is fixed in text units (the triangle is 15 % of the skill tab and 22 % of the skill summary popup, both at fontSize 40), so a width fraction only agrees with the game at one card width and doubled the triangle on desktop; REJECTED: 50 units to a 1.25 rem line — calibrated from coin captures rather than read from the prefab, it drew every client length 1.14 times too large.
  INVARIANT: every client-unit length (triangle, coin, plate glow) derives from the one description-font scale — theme constants and the importance table tests.
  NON-GOAL: choosing between `ImportanceUI`'s base, part-icon and unlock positions per skill; its selection logic is not in the decompiled store.

## Takeaway
- takeaway: read the fixed quantity from the client before calibrating against a capture; a capture shows one layout, the prefab states the rule.
