# Execution Plan: Format Description Text

## Execution Overview

Implement keyword formatting system that parses `[BracketedKeywords]` in skill/passive descriptions:
- Battle keywords: Icon + colored name + click popover with description
- Skill tags: Colored display text only (no interaction)
- Unknown keywords: Preserved as plain text with brackets

Follows established `sanityConditionFormatter` pattern: pure functions + hook wrapper + presentational components.

---

## Execution Order

### Phase 1: Data Layer

1. **`schemas/SkillTagSchemas.ts`**: Create Zod schema for skillTag.json
   - Depends on: none
   - Enables: F2
   - Pattern: `BattleKeywordsSchemas.ts`

2. **`types/KeywordTypes.ts`**: Create type definitions
   - Depends on: Step 1
   - Enables: F1-F4
   - Types: ResolvedKeyword, KeywordType, FormatterContext

3. **`hooks/useSkillTagI18n.ts`**: Create hook to load skillTag.json
   - Depends on: Step 1
   - Enables: F2
   - Pattern: `useBattleKeywords.ts`

4. **`schemas/index.ts`**: Export new schemas
   - Depends on: Step 1
   - Enables: Integration

### Phase 2: Logic Layer

5. **`lib/assetPaths.ts`**: Add `getBattleKeywordIconPath()`
   - Depends on: none
   - Enables: F5
   - Path: `/images/battleKeywords/{iconId or key}.webp`

6. **`lib/keywordFormatter.ts`**: Create pure parsing/resolution functions
   - Depends on: Steps 2, 5
   - Enables: F1-F4
   - Functions: parseKeywords, resolveKeyword, getKeywordColor
   - Pattern: `formatSanityCondition.ts`

7. **`hooks/useKeywordFormatter.ts`**: Create hook combining data sources
   - Depends on: Steps 3, 6
   - Enables: All features
   - Pattern: `sanityConditionFormatter.ts`

### Phase 3: Interface Layer

8. **Install shadcn Popover**: `yarn run shadcn add popover`
   - Depends on: none
   - Enables: F6
   - Creates: `components/ui/popover.tsx`

9. **`components/common/FormattedKeyword.tsx`**: Single keyword component
   - Depends on: Steps 5, 6, 8
   - Enables: F5, F6
   - Renders: Icon + colored text + Popover (battle keywords only)

10. **`components/common/FormattedDescription.tsx`**: Description wrapper
    - Depends on: Steps 7, 9
    - Enables: All integration
    - Uses: useKeywordFormatter + maps to FormattedKeyword

### Phase 4: Integration

11. **`components/identity/SkillDescription.tsx`**: Use FormattedDescription
    - Depends on: Step 10
    - Enables: I1, I2
    - Replace plain text rendering

12. **EGO Gift components**: Use FormattedDescription
    - Depends on: Step 10
    - Enables: I4
    - Update EnhancementPanel or relevant description renderers

13. **EGOSkillCard**: Verify inheritance from SkillDescription
    - Depends on: Step 11
    - Enables: I3
    - May require no changes if already uses SkillDescription

### Phase 5: Tests

14. **`lib/__tests__/keywordFormatter.test.ts`**: Unit tests
    - Depends on: Step 6
    - Tests: Parsing, resolution, color lookup, edge cases

15. **`components/common/__tests__/FormattedDescription.test.tsx`**: Component tests
    - Depends on: Step 10
    - Tests: Render mixed content, popover interaction

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| 4 | Types compile | `yarn typecheck` |
| 7 | Parsing works | Run unit tests |
| 8 | Popover installed | Check file exists |
| 10 | Components render | Component test |
| 11 | Identity skills formatted | Navigate to /identity/10813 |
| 12 | EGO Gifts formatted | Navigate to EGO Gift detail |
| 15 | All tests pass | `yarn test` |

---

## Rollback Strategy

**Safe Stopping Points:**
- After Phase 2: Logic complete, no visible changes
- After Phase 3: Components ready, not integrated

**If integration fails:**
- Revert integration changes only
- Keep new components unused
- Debug before re-attempting

**If popover fails:**
- Degrade to text-only (no click behavior)
- Log issue for later fix
