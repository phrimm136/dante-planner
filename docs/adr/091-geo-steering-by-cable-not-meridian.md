# 091 geo-steering-by-cable-not-meridian
epic: none · pr: none

## Decisions
- @edge @steering @geo — `steering_region_pools` maps exactly the Asia-Pacific Cloudflare regions (NEAS, SEAS, SAS, OC) to Seoul-first, and every other region code is deliberately unnamed and falls to the Oregon-first `default_pool_order`. Cloudflare geo steering routes by the colo's region code, not the client's location, and any code absent from `region_pools` silently inherits `default_pools`: Korean users whose ISPs egress to SIN (region SEAS) were served by Oregon while the Seoul fleet sat idle, with an HKG colo routed to ap-northeast-2 and a SIN colo to us-west-2 seconds apart.
  REJECTED: a meridian split (a great-circle cut between Seoul at 127°E and Oregon at 121°W, boundary near 3°E) — it assigns Eastern Europe, the Middle East and most of Africa to Seoul, but network distance follows submarine cables, not longitude: EEU reaches Oregon about 80-100 ms faster than Seoul over the trans-Atlantic corridor, while Europe-to-Northeast-Asia traffic detours through the Middle East or Southeast Asia.
  REJECTED: an exhaustive per-region latency table naming all thirteen codes — false precision; outside Asia-Pacific and the Atlantic side the measured differences (ME notably) sit within noise of a tie, and naming only the Seoul-first codes keeps the default meaningful instead of decorative.
- @edge @steering @geo @consequences — ME, SAF and NAF traffic pays Oregon latency even where Seoul would roughly tie, and adding a third region reopens this table wholesale, not incrementally.

## Takeaway
- takeaway: a routing table keyed on geography inherits the network's topology, not the map's; the cable is the unit of distance, and an unnamed key is a decision the default makes for you.
