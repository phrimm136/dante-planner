# Task: Refine EGO Gift Detail Page Layout

## Description

Revise the EGO Gift detail page to follow a similar structure to the Identity detail page while adapting for EGO Gift's different data density. The key changes are:

### Layout Structure (4:6 Ratio)
- **Left Column (40%)**: Header row with image + name/tier, followed by enumerated data fields below
- **Right Column (60%)**: Enhancement selector (sticky) with description content below

### Left Column Design
- **Header Row**: Gift image and name displayed horizontally in the same row
  - Image should be responsive but constrained (around 80-96px)
  - Name with tier badge displayed beside image
- **Data Enumeration**: Below the header, display metadata as label-value pairs:
  - Keyword (with badge styling)
  - Price (with icon)
  - Theme Pack (which floor/theme this gift belongs to)
  - Enhancement availability
  - Difficulty badge (if applicable: Hard/Extreme)

### Right Column Design
- **Enhancement Selector**: Click-to-reveal pattern using `DetailEntitySelector`
  - Shows enhancement levels: Base, +, ++
  - Selector should be sticky when scrolling
  - Selecting a level displays that level's description
- **Description Content**: Full description for selected enhancement level
  - Support for upgrade highlighting (styled differently for changed values)
  - Use `FormattedDescription` or similar for keyword parsing

### Mobile Layout
- Enhancement tab + Theme Pack tab structure
- Image + name header stacks vertically on mobile
- Data enumeration remains as list
- Tabbed content below for Enhancement descriptions

### Key Design Decisions
1. **Enhancement Display**: Click-to-reveal (not parallel) - user selects enhancement level to view
2. **Image Sizing**: Responsive within constraints
3. **Mobile Tabs**: Enhancement + Theme Pack

## Research

### Patterns to Study
- Identity detail page layout structure (`IdentityDetailPage.tsx`)
- `DetailPageLayout` component usage and props
- `DetailEntitySelector` component - verify `entityType="egoGift"` support
- `DetailLeftPanel` and `DetailRightPanel` components
- `FormattedDescription` component for keyword parsing

### Data Structure
- EGO Gift JSON schema (`EGOGiftSchemas.ts`)
- Sample EGO Gift data files (`static/data/egoGift/*.json`)
- i18n structure for EGO Gift (`static/i18n/EN/egoGift/*.json`) - especially `descs` array structure
- Theme Pack data structure for floor/theme association

### Existing Components
- Current `EGOGiftDetailPage.tsx` implementation
- `components/egoGift/` folder for existing EGO Gift components
- Enhancement-related components (if any exist)

## Scope

Files to READ for context:
- `frontend/src/routes/IdentityDetailPage.tsx` - target pattern reference
- `frontend/src/routes/EGOGiftDetailPage.tsx` - current implementation to revise
- `frontend/src/components/common/DetailPageLayout.tsx`
- `frontend/src/components/common/DetailEntitySelector.tsx`
- `frontend/src/components/common/DetailLeftPanel.tsx`
- `frontend/src/components/common/DetailRightPanel.tsx`
- `frontend/src/components/egoGift/*.tsx` - existing EGO Gift components
- `frontend/src/schemas/EGOGiftSchemas.ts`
- `frontend/src/types/EGOGiftTypes.ts`
- `frontend/src/lib/constants.ts` - for ENHANCEMENT_LEVELS, DETAIL_PAGE constants
- `static/data/egoGift/9001.json` - sample data structure
- `static/i18n/EN/egoGift/9001.json` - sample i18n structure

## Target Code Area

Files to CREATE or MODIFY:
- `frontend/src/routes/EGOGiftDetailPage.tsx` - major revision
- `frontend/src/components/egoGift/GiftDataRow.tsx` - new component for label-value pairs (or use generic `DataRow`)
- `frontend/src/components/egoGift/GiftHeader.tsx` - new component for image + name row
- `frontend/src/components/egoGift/GiftMetadataPanel.tsx` - new component for data enumeration
- `frontend/src/components/egoGift/EnhancementDescription.tsx` - new/revised component for description display

## Testing Guidelines

### Manual UI Testing

1. Navigate to `/ego-gift` list page
2. Click on any EGO Gift card to open detail page
3. Verify layout shows 4:6 column split on desktop
4. Verify left column shows:
   - Image and name in horizontal row at top
   - Tier badge visible near name
   - Data fields enumerated below (Keyword, Price, Theme Pack, etc.)
5. Verify right column shows:
   - Enhancement selector at top (Base, +, ++)
   - Base enhancement description displayed by default
6. Click "+" enhancement level
7. Verify description updates to show +1 enhancement text
8. Verify upgrade highlights are styled differently (if present in description)
9. Click "++" enhancement level
10. Verify description updates to show +2 enhancement text
11. Scroll down on page with long description
12. Verify enhancement selector remains sticky/visible
13. Resize browser to mobile width (<768px)
14. Verify layout collapses to single column
15. Verify mobile tabs appear (Enhancement / Theme Pack)
16. Switch between mobile tabs
17. Verify content updates appropriately

### Automated Functional Verification

- [ ] Layout ratio: Left column is 40%, right column is 60% on desktop
- [ ] Image + name row: Both elements visible and horizontally aligned
- [ ] Data enumeration: All metadata fields (Keyword, Price, Theme Pack) display correctly
- [ ] Enhancement selector: Shows 3 levels (Base, +, ++)
- [ ] Enhancement switching: Clicking level updates displayed description
- [ ] Default state: Base enhancement (level 0) shown on page load
- [ ] Sticky selector: Enhancement selector stays visible when scrolling
- [ ] Mobile responsive: Layout changes to single column below breakpoint
- [ ] Mobile tabs: Tab navigation works for Enhancement/Theme Pack

### Edge Cases

- [ ] Missing keyword: Gift without keyword shows appropriate placeholder or omits row
- [ ] Missing theme pack: Gift without theme pack omits that data row or shows "General"
- [ ] Long description: Descriptions with many lines scroll properly in right panel
- [ ] No enhancement text change: Some gifts may have identical text across levels - handle gracefully
- [ ] Hard/Extreme only gifts: Difficulty badge displays correctly
- [ ] Special characters in description: Keyword markup `[Burn]` renders as styled badge

### Integration Points

- [ ] DetailPageLayout: Integrates properly with shared layout component
- [ ] DetailEntitySelector: Enhancement selection state syncs with displayed content
- [ ] i18n: Descriptions load correctly for current language
- [ ] URL params: Gift ID from route params loads correct gift data
- [ ] Navigation: Back button returns to EGO Gift list page
