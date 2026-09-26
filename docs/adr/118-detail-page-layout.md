# 118 detail-page-layout
epic: none · pr: none

## Decisions
- @detail @layout — Identity, E.G.O and battle detail pages share one desktop layout: a header row with the name block and artwork on the left and every metadata panel on the right, a full-width tier or level selector below it, then skills, passives, E.G.O and sanity stacked in that order, each under a small localized section title and separator. The previous 4/6 split left the right column holding both the selector and every section, so skills sat behind slot tabs to fit. REJECTED: keeping the two-column split and only reordering — the width that removes the tabs is the width the left column was holding.
- @detail @tabs — No skill-slot or awaken/erosion tabs: every skill of the entity renders at once. REJECTED: tabs at full width — they hide three quarters of an identity's skills behind clicks that buy nothing once the column is wide enough.
- @detail @mobile — Mobile stacks the same sections under the same titles. REJECTED: keeping the mobile skills/passives/sanity tabs — the narrow screen is where hidden content costs most.
- @detail @order — Identity skills order by attack before defense, then tier, then id, which reproduces slot order on every identity (10310 reads 01 05 02 06 03 07 04 08); E.G.O skills order awaken, then erosion, then id; enemy, assistant and event-sinner skills order by id alone. REJECTED: one rule for all entities — enemy skills have no slot structure for attack-first to mean anything the client shows.

## Takeaway
- takeaway: tabs are a width workaround; when the layout gives the width back, the tabs should go with the constraint that justified them.
