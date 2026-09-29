# 141 floor-reference-checks-live-in-the-floor-subject
epic: none · pr: none · supersedes: 139 @planner @floors @ids

## Decisions
- @planner @floors @ids — Game-data reference checks over floors (pack known, gift known, gift
  affordable for the pack) live in the floor subject module as their own function beside
  admission, and still run only when the boundary reported nothing; an unknown pack does not hide
  its floor's gift checks, and an unknown gift skips only its own affordability check. Content
  rules are grouped by the subject whose fields they read, and a floor rule kept in a class that
  also checked identities, E.G.O and gift lists was how floor rules came to hide outside the
  floor module.
  REJECTED: keeping the checks in a cross-subject id-reference class — one class then reads every
  subject's fields, and a subject's rules are no longer found in its module.
  REJECTED: folding them into admission — admission gains the game-data registry, and the
  category-change path, which admits only, would start running reference checks.
  INVARIANT: a subject package depends only on the validation root and the game data, never on a
    sibling subject or a caller — `SubjectModuleBoundaryTest`, `FloorModuleBoundaryTest`.

## Takeaway
- takeaway: group rules by the data they read, not by the kind of check they perform; a "kind"
  class is where one subject's rules go missing.
