# 136 planner-floor-rules-follow-the-category
epic: none · pr: none · supersedes: 134

## Decisions
- @planner @category @floors — A change of category re-runs the category-dependent floor rules (floor count, difficulty per floor, every floor present on publish, theme pack order and repeats) against the stored content, while the game-data id checks stay skipped when the content itself is unchanged. Floor validity became a function of the category, so skipping all validation let a category change store content the new category forbids, such as a published 15F planner holding five floors.
  REJECTED: re-validating everything on a category change — brings back the failure the skip removed, a data release turning an unrelated edit into an id error.
  REJECTED: keeping the skip — publishes content the publish rules reject.
- @planner @floors @index — Only floors below the category's floor count are validated and indexed for entity search; floors past it stay stored untouched. The editor keeps all fifteen floors whatever the category so that switching back restores them, while indexing the hidden ones made search return planners that render none of the matched ids.
  REJECTED: rejecting floors past the count — every 5F and 10F save from the editor carries fifteen.
  REJECTED: truncating them during normalization — deletes floors the editor keeps on purpose.
  REJECTED: validating ids on hidden floors — a retired id on a floor the user cannot see blocks the save.

## Takeaway
- takeaway: once a rule's input includes a field, changing that field alone is a change the rule must see again.
