# Task: Skills

## Description
Refine the skill section. The skill image with base/coin power is next to the skill name and specs. See `SkillSpec.png`. The skill image is a stack of sin frame background, skill image, and sin frame (from bottom to top) in `/static/images/skillFrame/` and `/static/images/identity/{id}/`. Base power is on the left of the skill image, coin power on the top of the skill image, attack type on the bottom of the image. The attack type image is consisted of attack type frame background, attack type icon, and attack type frame, from bottom to top. The frames and frame backgrounds have to be colored in respect to the sin of the skill. In the `coinEA`, `C` means coin icon and `U` means unbreakable coin icon, in `/static/images/UI/common/`. Coin EA, (skill name and skill EA), (attack/defense level, attack weight) are in vertical order next to the skill image. The attack/defense level is consisted of the corresponding icon and the total level with underline. Under this skill spec, there is skill description. Read `/static/i18n/{lang}/{id}.json`. `desc` first, and then `coinDesc`. the coin description is consisted of coin icon description text. Each description is tabbed.

## Research
- Color (FG, BG): Wrath (#fe0000, #fe0000), Lust (#f86300, #fe4000), Sloth (#f4c528, #fefe00), Gluttony (#9dfe00, #40fe00), Gloom (#0dc1eb, #00fefe), Pride (#0048cc, #0040fe), Envy (#9300db, #fe00fe), def (#9f693a, #e9c99f)

## Target
`/frontend/src/components/identity/`
`/frontend/src/routes/IdentityDetailPage.tsx`

## Target Code Area
`/frontend/src/components/identity/`
`/frontend/src/routes/IdentityDetailPage.tsx`

## Testing Guidelines