# Task: Skill Replacement

## Description
Add a skill shift section between gift observation section and comprehensive gift section in the planner editor.
This section consists of twelve grid of sinners. Each grid is composed of the identity info picture and skills list, in vertical order.
The skills list is composed of the skill info (attack type on attribute type) and and its EA. Skill 1 has 3, skill 2 has 2, skill 3 has 1 in default.
Clicking a grid opens a pane with skill exchange. The pane is again composed with two parts, one showing the current skill EA, another showing clickable pane of exchanging skills or resetting the exchange.
The showing part is composed with skill card without base power and coin power, and its EA for each skill.
The exchange part is composed with skill card -> skill card or reset i18n text. The exchange between S1->S2, S2->S3, and S1->S3 occurs.
Clicking reset pane resets the sinner's skill EA to 3,2,1.

## Research
- There exists skill image composite in `SkillImageComposite.tsx`. You have to extract layer 1,2,3,4 with the same size into the another file and use them. 
- The components in each exchange pane is in horizontal order

## Scope
- `/frontend/src/components/identity/SkillImageComposite.tsx`
- `/frontend/src/routes/PlannerMDNewPage.tsx`

## Target Source Area
- `/frontend/src/components/`
- `/frontend/src/routes/PlannerMDNewPage.tsx`

## Testing Guidelines
