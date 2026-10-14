# 191 identity-level-caps-follow-the-client
epic: none · pr: none

## Decisions
- @planner @level: A Mirror Dungeon planner's identity level cap is its season's
  `identityMaxLevel` in `static/data/MD{N}/mirrorDungeon.json`. The planner's content version
  chooses the season, and both runtimes read the cap from that file. The game raises the dungeon
  cap per season (MD6 55, MD7 60, MD8 65), while both runtimes held one constant, 60.
  REJECTED: one cap for every season. An MD6 planner then accepts levels its season never had.
  REJECTED: the cap as a frontend and backend constant. The two copies already drifted from the
  client, which shows 65 outside the dungeon.
- @planner @level: The content parse clips a stored or incoming identity level above its
  season's cap to the cap, before validation, on both runtimes. A level below 1 still fails
  validation. Without the clip, plans saved at 60 fail every save of an MD6 planner. In game, a
  level above the dungeon's cap has no effect on Normal.
  REJECTED: exempting plans saved before the change. It needs a cut-off marker in every planner
  and a branch in validation that never goes away.
  REJECTED: rejecting the level. It blocks the user from saving content they did not write.
- @planner @level: An identity level is always an integer. The content parse writes a level whose
  JSON number has an integral value (60.0) as an integer node (60), before the clip. The frontend
  cannot tell 60.0 from 60 after it parses JSON, so a rejection makes the two runtimes disagree on
  stored content.
  REJECTED: rejecting 60.0 as a wrong type. A stored plan holding 60.0 then fails every save while
  the frontend shows it as valid.
- @identity @level: Pages about identities outside a planner use the season cap in
  `static/data/identityLevelCap.json`, the last row of the client's `maxlevelbystoryprogress`.
  These are the detail page level slider, the identity cards and skill power. Identity skill
  power follows the detail page's level slider. EGO skill power keeps the season cap, because an
  EGO has no level of its own.
  REJECTED: one constant for both caps. The season cap (65) and a dungeon cap (60 in MD7) differ
  for weeks after every season update.

INVARIANT 191 @planner @level: The clip is idempotent and identical on both runtimes. Gate: the
shared content corpus replayed by both suites, including a level above the cap that comes back
at the cap.

NON-GOAL 191 @planner @level: Refracted Railway planners get no level cap. They carry no
equipment.

## Takeaway
- takeaway: if the game owns a number that changes per season, both runtimes read it from the
  season's data file and keep no copy.
