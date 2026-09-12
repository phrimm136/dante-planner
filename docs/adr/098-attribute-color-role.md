# 098 attribute-color-role
epic: none · pr: none

## Decisions
- @color @attribute (taste) — The one color the frontend paints an attribute in is the `background` role of the attribute color table, falling back to `type` where the table has no background (WHITE, BLACK, NEUTRAL). The table replaced a single tint per attribute with seven roles, and the shipped look was a muted tint that `background` sits nearest to for six of the seven affinities. REJECTED: `type`, the color the client paints frames and name plates with — it is the fully saturated primary (pure red for CRIMSON) and reads as a different product. REJECTED: choosing the nearest role per attribute — AZURE alone would land on `fontOutline`, and a per-attribute table is a second color table to maintain.
- @color @hex — Eight-digit hex values from the color tables reach CSS unchanged; `darkenColor` parses the first six digits and returns an opaque color. CSS accepts `#rrggbbaa` everywhere the app runs, and stripping alpha would silently discard the one table (importance stripes) whose alpha is meaningful. REJECTED: normalizing every table value to six digits on read.
- @color @sinner — Sinner color keys are a separate `SINNER_NAMES` constant in the client's own spelling (`Yisang`, `Merusault`), not the display-spelled `SINNERS`. The table is keyed by the client enum, and remapping on read would hide the one place the two spellings meet. REJECTED: reusing `SINNERS` and translating keys on load.

## Takeaway
- takeaway: when upstream replaces a scalar with a struct, pick the field once by a stated rule and record the rule; per-item nearest-match fits the old look better and becomes a table nobody can regenerate.
