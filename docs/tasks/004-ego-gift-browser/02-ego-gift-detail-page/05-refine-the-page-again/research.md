# Research: EGO Gift Detail Page Refinement

## Clarifications Resolved

| Question | User Decision |
|----------|---------------|
| Tier badge placement | Image already shows tier (no separate badge needed) |
| Empty theme pack display | Show "General" placeholder |
| Data field layout | Vertical stack (label above, value below) |
| Mobile tabs | Single scroll (no tabs - stack all content) |

---

## Spec-to-Code Mapping

| Requirement | File | Action |
|-------------|------|--------|
| Layout structure (4:6) | `EGOGiftDetailPage.tsx` | Major revision - use `DetailPageLayout` |
| Image + name row | Reuse `GiftImage.tsx`, `GiftName.tsx` | Wrap in flex container |
| Data enumeration | New `EGOGiftMetadata.tsx` | Create vertical stack component |
| Enhancement selector | `DetailEntitySelector.tsx` | Reuse (supports egoGift) |
| Click-to-reveal description | New state in page | Replace parallel `EnhancementLevels` |
| Theme Pack display | New component or inline | Show pack name or "General" |
| Mobile layout | `DetailPageLayout` | Use single-column stack (no tabs) |

---

## Pattern Enforcement

| New/Modified File | MUST Read First | Pattern to Copy |
|-------------------|-----------------|-----------------|
| `EGOGiftDetailPage.tsx` | `IdentityDetailPage.tsx` | Suspense, DetailPageLayout, selector state |
| `EGOGiftMetadata.tsx` | `StatusPanel.tsx` | Vertical stack, label-value structure |

---

## Existing Utilities to Reuse

| Category | Available | Use For |
|----------|-----------|---------|
| **Layout** | `DetailPageLayout`, `DetailLeftPanel`, `DetailRightPanel` | Page structure |
| **Selector** | `DetailEntitySelector` (entityType="egoGift") | Enhancement picker |
| **Description** | `FormattedDescription`, `FormattedKeyword` | Keyword parsing |
| **Assets** | `GiftImage.tsx`, `GiftName.tsx`, `CostDisplay.tsx` | Header elements |
| **Constants** | `ENHANCEMENT_LEVELS: [0,1,2]`, `ENHANCEMENT_LABELS` | Enhancement state |
| **Hooks** | `useEGOGiftDetailData.ts` | Data fetching |

---

## Gap Analysis

**Missing (must create):**
- Vertical data enumeration component (metadata fields)
- Theme Pack display logic (array → name or "General")
- Enhancement click-to-reveal state management
- Keyword badge styling in metadata context

**Can Reuse Directly:**
- `DetailPageLayout` (4:6 ratio)
- `DetailEntitySelector` (enhancement selector)
- `DetailRightPanel` (sticky selector + scroll)
- `FormattedDescription` (upgrade highlights)
- `GiftImage`, `GiftName`, `CostDisplay`

**Needs Modification:**
- `EGOGiftDetailPage.tsx` (complete rewrite of layout)

---

## Data Structure Notes

**EGO Gift Spec (`static/data/egoGift/{id}.json`):**
- `attributeType`: Affinity type
- `tag`: Array containing tier (e.g., `["TIER_2"]`)
- `keyword`: String or null
- `price`: Number
- `themePack`: Array of pack IDs (empty = "General")
- `hardOnly`, `extremeOnly`: Boolean flags

**EGO Gift i18n (`static/i18n/EN/egoGift/{id}.json`):**
- `name`: Display name
- `descs`: Array of 3 descriptions (enhancement levels 0, 1, 2)
- `obtain`: Acquisition source (not used in new design)

---

## Technical Constraints

- `DetailEntitySelector` already handles enhancement levels 0-2
- `FormattedDescription` needs Suspense ancestor (page already wrapped)
- Mobile breakpoint at 1024px (`DETAIL_PAGE.BREAKPOINT_LG`)
- Image already contains tier overlay (no separate badge component needed)

---

## Testing Requirements

### Manual UI Tests
1. Navigate to `/ego-gift` → click gift → verify 4:6 layout on desktop
2. Left column: image+name row, vertical metadata below
3. Right column: Enhancement selector at top, description below
4. Click +/++ → description updates
5. Scroll → selector stays sticky
6. Resize to mobile → single column, all content stacked (no tabs)
7. Empty theme pack gift → shows "General"
8. Gift without keyword → row hidden or shows placeholder

### Automated Tests
- [ ] Layout renders 4:6 on desktop, single column on mobile
- [ ] Enhancement selector shows 3 levels
- [ ] Clicking level updates `descs[level]` display
- [ ] Base (level 0) shown on page load
- [ ] `themePack: []` displays "General"
- [ ] Keywords render via `FormattedDescription`

### Edge Cases
- [ ] `keyword: null` → hide keyword row
- [ ] `themePack: []` → show "General"
- [ ] Identical descriptions across levels → display normally
- [ ] Long descriptions → ScrollArea handles overflow
