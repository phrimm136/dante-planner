# Research: Format Description Text

## Clarifications Resolved

| Question | Decision |
|----------|----------|
| Popover implementation | Add shadcn Popover via `yarn run shadcn add popover` |
| Icon location | Use existing `/static/images/battleKeywords/` folder |
| Skill tag behavior | Styled text only, no click/popover |

---

## Spec-to-Code Mapping

- Parse `[keyword]` patterns: New `lib/keywordFormatter.ts`
- Skill tag resolution: New `hooks/useSkillTagI18n.ts` + `schemas/SkillTagSchemas.ts`
- Battle keyword resolution: Extend existing `hooks/useBattleKeywords.ts`
- Color resolution: Use existing `hooks/useColorCodes.ts`
- Icon rendering: Battle keywords only, path `/images/battleKeywords/{iconId}.webp`
- Click popover: Battle keywords only via shadcn Popover
- Integration targets: `SkillDescription.tsx`, `EGOSkillCard.tsx`, EGO Gift components

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `schemas/SkillTagSchemas.ts` | `schemas/BattleKeywordsSchemas.ts` | Zod record schema, strict validation |
| `hooks/useSkillTagI18n.ts` | `hooks/useBattleKeywords.ts` | Query pattern, language reactivity |
| `hooks/useKeywordFormatter.ts` | `lib/sanityConditionFormatter.ts` | Formatter hook structure |
| `lib/keywordFormatter.ts` | `lib/formatSanityCondition.ts` | Pure function, regex parsing |
| `components/common/FormattedKeyword.tsx` | `components/identity/SkillDescription.tsx` | Presentational component |
| `components/common/FormattedDescription.tsx` | N/A | Wrapper using formatter hook |

---

## Existing Utilities

| Category | Location | Found |
|----------|----------|-------|
| Data hooks | `hooks/useBattleKeywords.ts`, `hooks/useColorCodes.ts` | Reuse for keyword/color data |
| Formatters | `lib/formatSanityCondition.ts` | Pattern for regex parsing |
| Asset paths | `lib/assetPaths.ts` | Add `getBattleKeywordIconPath()` |
| Schemas | `schemas/BattleKeywordsSchemas.ts` | Pattern for skill tag schema |

---

## Gap Analysis

**Missing:**
- Skill tag schema and hook
- Keyword parsing/formatting functions
- FormattedKeyword component (icon + colored text + popover)
- FormattedDescription wrapper component
- Popover component (install via shadcn)

**Modify:**
- `lib/assetPaths.ts` - Add icon path helper
- `components/identity/SkillDescription.tsx` - Use FormattedDescription
- `components/ego/EGOSkillCard.tsx` - Inherits from SkillDescription changes

**Reuse:**
- `useBattleKeywords()` hook and schema
- `useColorCodes()` hook
- Regex pattern from `formatSanityCondition.ts`

---

## Icon Path Correction

- Spec said: `/images/icon/battleKeywords/`
- Actual path: `/images/battleKeywords/`
- Available icons: ~100+ (Sinking, Vibration, Burst, Charge, etc.)
- Resolution: Use `iconId` from battleKeywords.json, fallback to keyword key

---

## Rendering Logic

**Battle Keywords:**
- Lookup in `battleKeywords.json` by key
- Render: Icon (if iconId exists) + translated name
- Color: `colorCode[buffType]` (Positive/Negative/Neutral)
- Interaction: Click opens Popover with name + description

**Skill Tags:**
- Lookup in `skillTag.json` by key
- Render: Display text only (no icon)
- Color: `colorCode[tagKey]` or fallback to `colorCode["Critical"]`
- Interaction: None (styled text only)

**Unknown Keywords:**
- Not found in either source
- Render: Plain text with brackets preserved `[UnknownKeyword]`
- Color: Inherit (no styling)

---

## Testing Requirements

### Unit Tests
- `parseKeywords()`: Extract all bracketed keywords from text
- `resolveKeywordType()`: Distinguish battle keyword vs skill tag
- Color resolution with fallback chain
- Icon path generation

### Integration Tests
- FormattedDescription renders mixed text + keywords
- Popover opens/closes on click
- Language switch updates keyword translations

### Manual UI Tests
- Identity detail page with complex descriptions (10813 - Hanafuda)
- EGO skill descriptions
- EGO Gift descriptions
- Light/dark theme color visibility

---

## Technical Constraints

- Popover requires shadcn installation
- Icon path uses `/images/battleKeywords/` not `/images/icon/battleKeywords/`
- Skill tags are text-only (no popover)
- Language reactivity via i18n hooks with 7-day staleTime
