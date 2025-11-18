# Implementation Plan: Copy UI From Identity and Refactor Components

## Clarifications Needed

No clarifications needed - requirements are clear after examining EGO data structure

## Task Overview

Create EGODetailPage by adapting IdentityDetailPage UI structure, replacing Identity-specific components with EGO equivalents. Remove sanity section, stagger, attack type resistance, and traits. Replace with sin cost and sin resistance panels. Convert skill display from 4-tab system to 2-tab awakening/corrosion. Simplify passive display by removing categories and support passive section. Replace rarity stars with EGO rank display and remove image toggle functionality.

## Steps to Implementation

1. **Define TypeScript interfaces**: Complete EGOData and EGOI18n interfaces in EGOTypes.ts based on 20101.json structure (skills with threadspins, costs/resistances arrays, passive array)
2. **Create EGO utility functions**: Add skill image path functions for awaken_profile.webp and erosion_profile.webp to identityUtils.ts or create egoUtils.ts
3. **Build EGO header component**: Create EGOHeader.tsx with rank display (no image toggle, no swap button, single character image)
4. **Build sin cost and resistance panels**: Create SinCostPanel.tsx and SinResistancePanel.tsx components displaying 7 values in grid layout
5. **Create EGO skill components**: Build EGOSkillCard.tsx, EGOSkillImageComposite.tsx, EGOSkillInfoPanel.tsx with sanity cost display and threadspin support
6. **Build EGO passive display**: Create EGOPassiveDisplay.tsx showing passive array without category labels or support passive section
7. **Implement EGODetailPage**: Build main page with two-column layout, 2-tab skill system (awakening/corrosion), data/i18n loading, threadspin level state
8. **Test with sample data**: Verify layout with 20101.json, conditional corrosion rendering, sin resistance/cost panels, and responsive design

## Success Criteria

- EGODetailPage displays character image with capitalized EGO rank (Zayin/Teth/He/Waw/Aleph), no image toggle button
- Sin cost panel displays 7 sin costs in grid layout, sin resistance panel displays 7 resistances with color coding
- Two skill tabs (awakening/corrosion) with threadspin level 4 displayed by default
- Skill images load from awaken_profile.webp and erosion_profile.webp paths in /static/images/EGO/{id}/
- Sanity cost displays alongside atkWeight in skill info panel
- Passive section shows all passives from array without category labels, no support passive section
- Sanity section, stagger panel, attack type resistance panel, and traits display all removed from layout
- Responsive two-column grid layout maintained matching Identity page structure
- Data loads from /static/data/EGO/{id}.json and /static/i18n/EN/EGO/{id}.json with proper error handling

## Assumptions Made

- EGO data has threadspins (levels 3 and 4) similar to Identity upties, display threadspin 4 by default
- Sin costs and resistances are arrays of 7 numbers indexed by sin type, display all 7 in grid layout
- EGO skills have atkType field so can reuse sin frame and attack type badge logic from Identity components
- Passive field is array but typically contains single passive, display first passive only or loop through all
- Sanity cost displays alongside atkWeight in skill info panel, both values shown together
- Rank value in JSON uses lowercase (zayin) but display capitalizes for UI (Zayin)
