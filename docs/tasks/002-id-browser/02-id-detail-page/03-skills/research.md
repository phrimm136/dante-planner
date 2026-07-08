# Research Document: Skills Section

## Overview of Codebase

- Current IdentityDetailPage has skill slot selector with four buttons (skill1, skill2, skill3, skillDef) using active state styling
- Skills displayed using map over getCurrentSkills array to handle multiple variants in same slot
- Skill data structure has uptie3 and uptie4 tiers, each containing skill1-3 and skillDef arrays
- Each skill object contains basePower, coinPower, coinEA (C/U notation), sin, atkType (except defense), atkWeight, LV, quantity
- Skill i18n matches data structure with desc and coinDescs arrays, currently has placeholder empty strings
- Base defense level constant exists at 55 in globalConstants.ts for level calculations
- CoinEA uses C for regular coin and U for unbreakable coin in character string format
- Skill frames available for seven sins (wrath, lust, sloth, gluttony, gloom, pride, envy) plus defense with three levels each
- Frame naming pattern: {sin}{level}.webp and {sin}{level}BG.webp for backgrounds
- Sin colors defined with FG and BG values: Wrath (fe0000, fe0000), Lust (f86300, fe4000), Sloth (f4c528, fefe00), Gluttony (9dfe00, 40fe00), Gloom (0dc1eb, 00fefe), Pride (0048cc, 0040fe), Envy (9300db, fe00fe), Defense (9f693a, e9c99f)
- Skill images in identity folders named skill01-04.webp with variants using -2, -3 suffixes and uptie tiers using _4 suffix
- Attack type frames (attackType.webp, attackTypeBG.webp) separate from sin frames for layering attack icons
- IdentityCard component demonstrates image layering with relative container and absolute positioned overlays using inset-0 and pointer-events-none
- Current implementation hardcoded to uptie4 tier without selector UI
- Existing utility functions for image paths but missing skill-specific path generators

## Codebase Structure

- Main implementation in IdentityDetailPage.tsx with skill selector buttons at lines 192-237 and skill display at 240-302
- Skill components should go in /frontend/src/components/identity/ following existing component patterns
- Utility functions in /frontend/src/lib/identityUtils.ts need extension for skill image paths and sin color helpers
- Global constants in /frontend/src/lib/globalConstants.ts should include sin color mappings
- Skill frames located at /static/images/skillFrame/ with 57 total files including sins, backgrounds, attack type, and defense
- Individual skill images at /static/images/identity/{id}/ following skill0X.webp and skill0X-Y.webp naming
- Attack type icons at /static/images/UI/identity/ (slash.webp, pierce.webp, blunt.webp, attack.webp, defense.webp)
- Type definitions exist in /frontend/src/types/IdentityTypes.ts with SkillData interface already defined
- Image layering patterns use relative parent with absolute children, inset-0 for full coverage, and object-contain for frames
- Skill slot system uses SkillSlot type with skill1, skill2, skill3, skillDef mapped to arrays supporting multiple variants per slot
- Multiple skills per slot handled by array mapping with index-based variant naming (skill03.webp for first, skill03-2.webp for second)
- Defense skills (skillDef) lack atkType property requiring conditional rendering logic

## Gotchas and Pitfalls

- Coin icons (C and U) specified to be in /static/images/UI/common/ but not found during research - may need verification or creation
- Skill3 often contains multiple variants requiring proper index-based image name generation (skill03.webp, skill03-2.webp)
- Defense skills have no atkType property so conditional checks needed before accessing this field
- Sin names in data are capitalized (Sloth, Envy) but frame filenames are lowercase (sloth1.webp, envy2.webp) requiring case transformation
- One typo exists in frame assets: gloom3GB.webp should be gloom3BG.webp - may cause image loading failures
- Sin color application requires choosing between CSS filter, blend modes, or SVG masking approaches - blend mode recommended
- Attack type display needs five-layer composition (attackTypeBG, icon, attackType frame, all colored by sin) which is complex
- Skill image paths vary by slot, variant index, and potentially uptie tier requiring robust path generation logic
- Level calculation for attack vs defense uses same BASE_DEFENSE_LEVEL but different icons and labels must be applied correctly
- Current i18n skill data has empty strings requiring fallback or placeholder text during development
- Image layering order critical: sin BG → skill image → sin frame → attack type composite → text overlays as absolute positioned divs
- SkillSlot type defined as string union but getCurrentSkills uses type assertion (skills as any) creating type safety gap
- Hardcoded offense level calculation at line 269 (55 + LV) should use BASE_DEFENSE_LEVEL constant for maintainability
- Frame level selection logic unclear - may need to map skill LV (1-4) to frame levels (1-3) with clamping
- No existing patterns for applying hex colors dynamically to image layers requiring new implementation approach
