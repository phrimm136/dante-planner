# Task: Format Description Text with Keyword Highlighting

## Description

Parse and render bracketed keywords in skill/passive descriptions with styled formatting. The system transforms raw text like `[Burst]`, `[WhenUse]`, `[Sinking]` into visually distinct, interactive elements.

### Keyword Types

**Battle Keywords** (status effects, game mechanics):
- Source: `static/i18n/{lang}/battleKeywords.json`
- Structure: `{ name, desc, iconId, buffType }`
- Rendering: Icon (if available) + translated name
- Color: Based on `buffType` (Positive/Negative/Neutral) from `colorCode.json`
- Example: `[Burst]` -> icon + "Tremor Burst" in red

**Skill Tags** (trigger conditions):
- Source: `static/i18n/{lang}/skillTag.json`
- Structure: Simple key -> display text mapping
- Rendering: Display text only (no icon)
- Color: Direct key lookup in `colorCode.json`
- Example: `[WhenUse]` -> "[On Use]" in blue

### Color Resolution

1. For skill tags: `colorCode[tagKey]` (e.g., `colorCode["WhenUse"]`)
2. For battle keywords: `colorCode[buffType]` (e.g., `colorCode["Positive"]`)
3. Fallback: `colorCode["Critical"]` (#93f13e)

### Icon Resolution

- Path: `/images/icon/battleKeywords/{iconId or key}.webp`
- Use `iconId` from battleKeywords.json if non-null, otherwise use the keyword key
- Skip icon rendering if not in known icon set (render text only)

### Tooltip Behavior

- Trigger: Click on keyword (not hover)
- Content: Keyword name (header) + full description (body)
- Component: Use shadcn `<Popover>`
- Only battle keywords have descriptions; skill tags show display text only

### Application Scope

Apply formatting to:
- Identity skill descriptions (desc + coinDescs)
- Identity passive descriptions
- EGO skill descriptions (awaken + erosion)
- EGO Gift descriptions

## Research

- Pattern: `lib/sanityConditionFormatter.ts` + `hooks/useSanityConditionData.ts`
- Existing hooks: `useBattleKeywords`, `useColorCodes` (verify existence)
- Existing schemas: `BattleKeywordsSchemas.ts`, `ColorCodeSchemas.ts`
- Icon availability: Check which keywords have icons in `static/images/icon/statusEffect/`
- Component: `components/identity/SkillDescription.tsx` current implementation

## Scope

Read for context:
- `frontend/src/lib/sanityConditionFormatter.ts`
- `frontend/src/lib/formatSanityCondition.ts`
- `frontend/src/hooks/useSanityConditionData.ts`
- `frontend/src/components/identity/SkillDescription.tsx`
- `frontend/src/components/ego/EGOSkillCard.tsx`
- `frontend/src/lib/assetPaths.ts`
- `static/i18n/EN/battleKeywords.json`
- `static/i18n/EN/skillTag.json`
- `static/data/colorCode.json`

## Target Code Area

New files:
- `frontend/src/schemas/SkillTagSchemas.ts`
- `frontend/src/hooks/useSkillTagI18n.ts`
- `frontend/src/types/KeywordTypes.ts`
- `frontend/src/lib/keywordFormatter.ts`
- `frontend/src/hooks/useKeywordFormatter.ts`
- `frontend/src/components/common/FormattedKeyword.tsx`
- `frontend/src/components/common/FormattedDescription.tsx`

Modify:
- `frontend/src/lib/assetPaths.ts` (add getKeywordIconPath)
- `frontend/src/lib/constants.ts` (add KNOWN_KEYWORD_ICONS)
- `frontend/src/components/identity/SkillDescription.tsx`
- `frontend/src/components/ego/EGOSkillCard.tsx`
- EGO Gift description components

## Testing Guidelines

### Manual UI Testing

1. Navigate to Identity Detail page (e.g., /identity/10813)
2. Locate skill description with brackets (e.g., "[WhenUse]", "[Sinking]")
3. Verify bracketed keywords are replaced with colored text
4. Verify skill tags like "[WhenUse]" show in blue color
5. Verify battle keywords like "[Sinking]" show icon + name in buff-type color
6. Click on a battle keyword
7. Verify popover appears with keyword name and description
8. Click outside popover to dismiss
9. Verify skill tags without descriptions still show popover with display text
10. Navigate to EGO Detail page
11. Verify awaken/erosion skill descriptions are formatted
12. Navigate to EGO Gift Detail page
13. Verify gift descriptions are formatted
14. Switch language (if supported)
15. Verify keywords update to new language

### Automated Functional Verification

- [ ] Parsing: Regex correctly extracts all `[BracketedKeywords]`
- [ ] Skill tag lookup: Keys found in skillTag.json render with display text
- [ ] Battle keyword lookup: Keys found in battleKeywords.json render with name
- [ ] Unknown keywords: Render as plain text with brackets preserved
- [ ] Color resolution: Correct color applied per buffType/tagKey
- [ ] Color fallback: Unknown keys fallback to Critical color
- [ ] Icon rendering: Icons appear for keywords in known set
- [ ] Icon skip: No broken images for keywords without icons
- [ ] Popover: Click opens, click-outside closes
- [ ] Newline handling: Descriptions with `\n` render correctly
- [ ] Multiple keywords: `[A][B]` consecutive keywords both render

### Edge Cases

- [ ] Empty string: Returns empty, no errors
- [ ] No brackets: Plain text passes through unchanged
- [ ] Empty brackets `[]`: Treated as plain text
- [ ] Nested brackets `[[A]]`: Outer brackets treated as text
- [ ] Keyword at string start: `[WhenUse] Do thing` parses correctly
- [ ] Keyword at string end: `Do thing [OnHit]` parses correctly
- [ ] Very long descriptions: No performance degradation
- [ ] Missing i18n key: Fallback to raw keyword text

### Integration Points

- [ ] SkillDescription: Renders formatted coinDescs and desc
- [ ] EGOSkillCard: Awaken and erosion descriptions formatted
- [ ] EGOGift components: Gift descriptions formatted
- [ ] Language switch: Keywords re-render with new translations
- [ ] Theme: Colors visible in both light and dark mode
