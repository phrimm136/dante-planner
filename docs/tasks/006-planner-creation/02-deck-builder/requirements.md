# Task: Deck Builder

## Description
Identity/ego building section, or deck building section, includes sinner-wise identity and ego viewer. Each sinner's card is composed with a exisitng id card, id's skill 1/2/3 afffinity-type viewer, and five ego viewer, in vertical order. Each skill is composed with skill type icon with a background color corresponding to the skill affinity. Each ego is composed with the circular ego image with a background color correpsonding to the ego affinity. Below the viewer, there exists status viewer. The status viewer consists of affinity EA viewer and keyword EA viewer, in vertical order The affinity EA viewer calculates the selected identities skill affinity and egos affinity cost. The keyword viewer calculates the identities with certain keywords. Below the status viewer, there exists an identity/ego list. The list is already implemented, so use that. Note that there must be a toggle between identity and ego so that identity and ego list are swapped each other. Identity list is set to be default. In list, clicking one of identity or ego shows tier selector (both id nd ego) and level selector (id only) and changes the corresonding sinner's equipment. Note that if the current ego with a certain type is empty, fill with the selection. If not, modify the equipped ego. For example, if you equipped a HE ego and click another HE ego, change the HE ego equipment. If a sinner's HE ego equipment is empty, clicking a HE ego fills the equipment slot. The default identity and ego ends with the id {3-digit}01. Also, the equipped id and ego must have indicator over the cards so that the user can notice they are selected ones, and must be on the top of the list when reloaded. Now look at the viewer. Clicking one sinner card allocates/deallocates the deploy order. That is, if you click the sinner 1 and 3, the deploy order of them is #1 and #2. Then, clicking the sinner 1 again deallocates the order and then reallocate the sinner 3's order to #1. There are 7 (editable) deployment and 5 (rest of 12 sinners) backups. For example, the #1-#7 order is deployment, and #8-#12 is backup. At the bottom there are buttons for importing from deck code, copying deck code to clipboard, and resetting the deployment order.
After implmenting the deck builder, add it to the planner new page.

## Research
- Since the company announces that threadspin tier will be expaned to 5, we must split uptie and threadspin tier.
- Since each id card is small, we can't use slide bar. use dec/inc button and number field that user can fill the level.
- The max level is defined in constants.ts as BASE_LEVEL. Rename it to MAX_LEVEL and change the exceeding input to the MAX_LEVEL.

## Scope
- /frontend/src/lib/constants.ts
- /frontend/src/components/{identity|EGO}List.tsx
- /frontend/src/components/{identity|EGO}Card.tsx

## Target Code Area
- /frontend/src/components
- /frontend/src/components/PlannerMDNewPage.tsx

## Testing Guidelines
