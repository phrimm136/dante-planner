# Research: Copy UI From Identity and Refactor Components

## Overview of Codebase

- IdentityDetailPage uses 10 components in two-column responsive grid layout with left stats and right skills sections
- Main components: IdentityHeader (with image toggle), StatusPanel (HP/Speed/Defense), ResistancePanel (Slash/Pierce/Blunt), StaggerPanel, TraitsDisplay
- Skills system uses SkillCard with three sub-components: SkillImageComposite (5-layer octagonal composite), SkillInfoPanel (with CoinDisplay), SkillDescription
- Skills organized in 4 tabs (skill1/2/3/def) with multiple variants per tab based on uptie level (3 vs 4)
- SkillImageComposite uses complex layering: sin frame background, octagonal skill image, sin frame overlay, attack type corner badge, power overlays
- Data loading uses two parallel async imports: identity data JSON and i18n JSON files with dynamic language switching
- Props drilling pattern throughout with no global state management, useState for local UI states
- Styling uses Tailwind with semantic tokens (bg-card, border-border), absolute positioning for overlays, flex/grid compositions
- Image paths follow pattern: /static/images/Identities/{id}/skill01.webp with numbered skills and uptie suffixes
- Existing common components: SearchBar and IconFilter are fully generic and reusable
- Identity-specific utilities in identityUtils.ts for path generation (getRarityIconPath, getSkillImagePath, getSinFramePath, etc)
- Resistance system uses getResistanceInfo utility mapping numeric values to categories (Fatal/Weak/Normal/Endure/Ineff) with color coding
- Passive system shows multiple passives with category labels plus separate support passive section
- Rarity displayed using star images from /static/images/UI/identity/ directory
- Image toggle button in header switches between gacksung and normal variant images with fallback error handling
- TypeScript interfaces defined per-component without centralized type library
- Components highly coupled to Identity data structure with hardcoded field assumptions throughout
- No existing EGODetailPage implementation found in routes directory
- Two-stage image fallback in SkillImageComposite: tries uptie4, falls back to uptie3, shows missing placeholder

## Codebase Structure

- Frontend organized with /frontend/src/routes/ for pages and /frontend/src/components/ for reusable components
- Component directories: /components/common/ for shared elements, /components/identity/ for identity-specific, need /components/ego/ for EGO
- Static assets split by type: /static/data/ for JSON, /static/images/ for assets with entity subdirectories
- EGO data at /static/data/EGO/{id}.json has different schema than Identity with sin resistance/cost instead of HP/Speed/Defense
- EGO images at /static/images/EGO/{id}/ contain awaken_profile.webp and erosion_profile.webp (some EGOs missing corrosion)
- EGO rank images stored in /static/images/UI/EGO/ directory for rank display replacing rarity stars
- IdentityDetailPage.tsx single-file pattern at 11,861 bytes, should mirror for EGODetailPage.tsx structure
- Skills in Identity JSON use nested object with skill1/skill2/skill3/skillDef keys containing arrays of variants
- Uptie data nested within each skill variant with separate stats for uptie 3 and 4 levels
- Sin resistance and sin cost are EGO-specific fields not present in Identity data model
- Utility functions centralized in identityUtils.ts, need egoUtils.ts for EGO path generation
- Global constants in globalConstants.ts including BASE_LEVEL, SEARCH_DEBOUNCE_DELAY, status effects arrays

## Gotchas and Pitfalls

- SkillCard components tightly coupled to Identity structure with uptie/atkType/atkWeight assumptions preventing direct reuse
- Not all EGOs have corrosion skills requiring conditional rendering logic to prevent null/undefined errors
- Sin resistance/cost data structure unknown until examining actual EGO JSON, may differ from expectations
- Uptie concept deeply embedded in skill display logic, must verify if EGO has equivalent before removing
- Image paths use completely different conventions: Identity uses numbered skills (skill01), EGO uses descriptive names (awaken_profile)
- Sanity section removal requires careful grid layout adjustments to prevent empty spaces or broken responsive design
- Octagonal clip-path polygon in SkillImageComposite specific to Identity design, likely different shape for EGO skills
- Sin frame selection logic tied to skillSlot mapping, EGO awakening/corrosion may need different frame level logic
- ResistancePanel hardcoded for 3 attack types (slash/pierce/blunt), EGO likely has 7 sin resistances requiring layout changes
- Passive display assumes category labels and support passive structure, EGO has single passive requiring simplified component
- Attack type composite layer in skill images not applicable to EGO, must remove from image composition
- Component props drilling creates deep nesting, refactoring to common components requires significant prop restructuring
- No TypeScript interfaces for EGO data model exist, must define from scratch by examining JSON structure
- Absolute positioning in overlays fragile with different content lengths, test thoroughly with various data
- EGO rank display format unknown, must check available rank images before implementing header component
