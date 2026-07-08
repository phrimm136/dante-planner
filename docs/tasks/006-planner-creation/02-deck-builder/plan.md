# Deck Builder - Implementation Plan

## Clarifications Resolved

- Deck code import/export deferred to next implementation phase
- Deployment order numbers (#1-#12) display on sinner cards
- Tier/level selectors appear as popup dialog
- All 5 EGO rank slots always visible; empty slots show corresponding rank character

## Task Overview

Build a deck builder component for the Limbus Company planner that allows users to equip identities and EGOs per sinner, configure uptie/threadspin tiers and levels via popup dialog, manage deployment order (7 active + 5 backup), and view aggregated affinity/keyword statistics. The component integrates with existing identity/EGO list components and adds to PlannerMDNewPage.

## Steps to Implementation

1. **Rename BASE_LEVEL to MAX_LEVEL**: Update constants.ts and all references throughout the codebase
2. **Create Deck State Types**: Define TypeScript interfaces for deck state (equipped identity/EGOs per sinner, tiers, levels, deployment order)
3. **Build Sinner Card Component**: Create compact card showing identity image, 3 skill affinity icons, 5 EGO slots (all ranks visible), and deployment order number
4. **Build Sinner Grid Viewer**: Arrange 12 sinner cards in grid with click-to-toggle deployment order functionality
5. **Create Tier/Level Selector Dialog**: Build popup with uptie selector (1-4), threadspin selector (1-5), and level input with +/- buttons
6. **Add Selected Indicator to Lists**: Modify IdentityCard and EGOCard to show equipped state when used in deck builder context
7. **Build Status Viewer**: Create affinity EA calculator (sum skill affinities + EGO costs) and keyword EA counter
8. **Add Identity/EGO Toggle**: Create toggle switch to swap between identity list and EGO list views
9. **Integrate into PlannerMDNewPage**: Add DeckBuilder component to the planner creation flow

## Success Criteria

- Each sinner displays equipped identity with skill 1/2/3 affinity icons
- Each sinner shows all 5 EGO rank slots; empty slots display rank character
- Deployment order number visible on each sinner card (#1-#7 deployed, #8-#12 backup)
- Clicking sinner card toggles deployment order
- Clicking identity in list opens popup dialog with tier/level selector then equips
- Clicking EGO fills empty slot or replaces existing EGO of same rank
- Equipped items show visual indicator and appear at top of list
- Affinity EA displays total sin resources generated/consumed
- Keyword EA displays count of identities per keyword

## Assumptions Made

- **Default Equipment IDs**: Following pattern {sinner_index}01 for defaults (e.g., 10101, 10201)
- **Uptie Range**: 1-4 for identities (current game state)
- **Threadspin Range**: 1-5 for EGOs (per instruction about expansion)
- **Level Range**: 1 to MAX_LEVEL (55) with numeric input validation
- **State Management**: Using React useState for deck state (not global store)
