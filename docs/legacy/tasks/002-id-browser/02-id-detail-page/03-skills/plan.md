# Implementation Plan: Skills Section

## Clarifications Needed

No clarifications needed - requirements are clear with user feedback provided.

## Task Overview

Implement comprehensive skill display section with layered image composition showing skill artwork, sin-colored frames, attack type indicators, and power overlays per SkillSpec.png reference. Each skill displays coin EA icons, level information, attack weight, and skill names with descriptions from i18n data. Support uptie4-specific images with _4 postfix and fallback, multiple skill variants per slot, and sin-based color theming using CSS blend modes. Frame selection maps skill LV to frame level with defense skills using def1 frame.

## Steps to Implementation

1. **Create sin color constants**: Add FG and BG hex color mappings for all seven sins plus defense to global constants file
2. **Build skill image path utilities**: Create functions for skill images with _4 uptie postfix and fallback, sin frames mapped by skill LV, backgrounds, attack type icons, and coin icons
3. **Create SkillImageComposite component**: Layer sin frame background, skill image, sin frame with CSS multiply blend mode for sin coloring and power text overlays
4. **Create AttackTypeIndicator component**: Layer attack type background, slash/pierce/blunt icon, and frame with sin-based coloring for offensive skills only
5. **Create CoinDisplay component**: Render coin EA string as horizontal sequence of coin icons (C or U) from static images
6. **Create SkillInfoPanel component**: Display skill name from i18n, level with attack or defense icon and underline, attack weight indicators vertically
7. **Create SkillDescription component**: Render base description followed by tabbed coin descriptions with coin icon prefix from i18n identity data
8. **Build complete SkillCard component**: Combine image composite, attack type, coin display, info panel, and descriptions matching SkillSpec.png layout
9. **Integrate into IdentityDetailPage**: Replace placeholder skill rendering with new SkillCard components for all skill slots
10. **Add error handling**: Handle missing images with fallback, empty i18n strings, defense skills without atkType, and multi-variant skills

## Success Criteria

- Skill images display three-layer composition: sin frame background, skill artwork, sin frame overlay with sin colors via CSS blend modes
- Uptie4 skills attempt to load _4 postfix images first, falling back to base images if missing
- Base power displays left of skill image, coin power on top, attack type indicator on bottom per SkillSpec.png reference
- Coin EA renders as horizontal sequence of C and U coin icons matching coinEA string characters from coin icon files
- Skill names from i18n identity files display next to skill image with proper localization
- Skill level shows attack icon for skills 1-3 or defense icon for defense skill with underlined total level and modifier
- Attack weight renders as visual indicators for offensive skills, omitted for defense skills without atkType
- Skill descriptions display base text then tabbed coin descriptions each with coin icon prefix

## Assumptions Made

- Uptie4 images use _4 postfix (skill04_4.webp) with automatic fallback to base images when missing
- Coin icons located per instructions correction in appropriate directory with C and U variants
- CSS multiply blend mode provides sufficient color accuracy for sin-themed frames across browsers
- Frame level maps directly to skill LV (1-3 for offense) or uses def1 for defense skills
- Skill names located at Skills.Uptie3/Uptie4.Skill1/2/3 arrays in i18n matching data structure with skill name as separate property
