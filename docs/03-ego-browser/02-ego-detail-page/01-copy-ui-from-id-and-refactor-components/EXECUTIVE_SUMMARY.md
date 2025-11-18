# Executive Summary: Identity to EGO Detail Page Refactoring

## Research Completion Status
**Documentation Generated**: 2 comprehensive research files (497 + 340+ lines)
- `/docs/03-ego-browser/02-ego-detail-page/01-copy-ui-from-id-and-refactor-components/research.md`
- `/docs/03-ego-browser/02-ego-detail-page/01-copy-ui-from-id-and-refactor-components/findings.md`

---

## Quick Reference: Component Mapping

### Identity → EGO Component Changes

| Identity Component | EGO Equivalent | Change Type | Effort |
|-------------------|---|---|---|
| **Page Structure** | EGODetailPage | Copy layout | Low |
| **IdentityHeader** | EGOHeader | Remove toggle, use rank | Medium |
| **StatusPanel** | SinCostPanel | New data model | High |
| **ResistancePanel** | SinResistancePanel | New data model | High |
| **StaggerPanel** | *(Remove)* | Not applicable | - |
| **TraitsDisplay** | *(Remove)* | Not applicable | - |
| **Sanity Section** | *(Remove)* | Not applicable | - |
| **SkillCard** | EGOSkillCard | New skill structure | Very High |
| **SkillImageComposite** | EGOSkillImageComposite | Different image paths | High |
| **SkillInfoPanel** | EGOSkillInfoPanel | Remove atkWeight, add sanity cost | High |
| **SkillDescription** | *(Reuse)* | No changes | ✓ |
| **CoinDisplay** | *(Reuse)* | No changes | ✓ |
| **Passive Section** | EGOPassiveDisplay | Remove category/support | Low |

---

## File Structure Comparison

### Identity Detail Page Layout
```
LEFT COLUMN (space-y-6)
├── Header + Name + Rarity
├── 3 Status Panels (grid-cols-3)
│   ├── StatusPanel (HP/Speed/Defense)
│   ├── ResistancePanel (Slash/Pierce/Blunt)
│   └── StaggerPanel (Thresholds)
├── TraitsDisplay
└── Sanity Section (hardcoded)

RIGHT COLUMN (space-y-6)
├── Skill Tabs (4: skill1/2/3/def)
│   └── SkillCard[] (loop through variants)
│       ├── SkillImageComposite
│       ├── SkillInfoPanel
│       └── SkillDescription
└── Passive Panel (category + support)
    ├── Passive[] (loop)
    └── Support Passive
```

### EGO Detail Page Layout (Target)
```
LEFT COLUMN (space-y-6)
├── Header + Name + Rank
├── Status Panels (New Layout TBD)
│   ├── SinCostPanel (NEW)
│   └── SinResistancePanel (NEW)
└── [Removed: Traits, Stagger, Sanity]

RIGHT COLUMN (space-y-6)
├── Skill Tabs (2: awakening/corrosion)
│   └── EGOSkillCard (single per tab, no variants)
│       ├── EGOSkillImageComposite
│       ├── EGOSkillInfoPanel
│       └── SkillDescription (reuse)
└── Passive Panel (simplified)
    └── Single Passive Display
```

---

## Data Structure Comparison

### Identity Data
```typescript
IdentityData {
  grade: number (1-5)
  HP: number
  minSpeed/maxSpeed: number
  defLV: number
  resist: [slash, pierce, blunt]
  stagger: number[]
  traits: string[]
  skills: {
    skill1/2/3/skillDef: SkillData[] ← Multiple variants per slot
  }
  passive: PassiveData[]
  sptPassive: PassiveData ← Support passive
}

SkillData {
  sin: string
  atkType?: string ← Attack type indicator
  quantity: number (EA count)
  coinEA: string ("CCU")
  LV: number
  upties: {
    '3': { basePower, coinPower, atkWeight }
    '4': { basePower, coinPower, atkWeight }
  }
}
```

### EGO Data (Required Structure)
```typescript
EGOData {
  rank: EGORank (Zayin|Teth|He|Waw|Aleph)
  // TBD structure for:
  sinCost: ? ← Sanity cost for activation
  sinResistance: ? ← Resistances to each sin
  skills: {
    awakening: EGOSkillData (?)
    corrosion: EGOSkillData | null (optional)
  }
  passive: PassiveData (single, no support)
  // Removed: HP, Speed, Defense, Stagger, Traits
}

EGOSkillData {
  sin: string
  // No atkType (EGOs don't have attack types)
  quantity: number
  coinEA: string
  // No LV/upties (likely single version)
  sanityCost: number ← New field
}
```

---

## Image Path Changes

### Identity Skill Images
```
/images/identity/{id}/skill01.webp
/images/identity/{id}/skill01-2.webp (variant)
/images/identity/{id}/skill01_4.webp (uptie4)
```

### EGO Skill Images
```
/images/EGO/{id}/awaken_profile.webp
/images/EGO/{id}/erosion_profile.webp
```

### Frame/Icon Changes
```
Identity: /images/UI/identity/rarity{1-5}.webp
EGO:      /images/UI/EGO/{Rank}.webp

Identity: /images/UI/skillFrame/{sin}{level}.webp
EGO:      (TBD - may use similar pattern)

Identity: /images/UI/identity/{atkType}.webp
EGO:      (Not applicable - no attack types)
```

---

## Component Implementation Priority

### Phase 1 (Essential)
1. **EGODetailPage.tsx** - Main container, data loading, layout
2. **EGOHeader.tsx** - Rank display without toggle
3. **EGOSkillCard.tsx** - Skill display (core feature)

### Phase 2 (Important)
4. **EGOSkillImageComposite.tsx** - Skill image composition
5. **SinCostPanel.tsx** - Cost display
6. **SinResistancePanel.tsx** - Resistance display

### Phase 3 (Nice-to-Have)
7. **EGOPassiveDisplay.tsx** - Simplified passive display
8. **EGOTypes.ts** - Extend type definitions
9. **identityUtils.ts** - Add EGO path utilities

---

## Critical Unknowns (Require Data Structure Verification)

1. **Sin Cost Data Structure**
   - Single numeric value or per-sin costs?
   - Where stored in EGO JSON? (`sinCost`, `cost`, nested object?)
   - Display format: icon + number, or custom?

2. **Sin Resistance Data Structure**
   - How many sin types? (7 sins + defense = 8 values?)
   - Format: array, object with sin names, nested?
   - Resistance categories: Same as Identity (Fatal/Weak/Normal/Endure/Ineff.)?
   - Color mapping: Use same text-red-500 scheme?

3. **Skill Data Structure**
   - How many EGO skills? (2: awakening + optional corrosion)
   - Do corrosion skills exist for all EGOs? Some? None?
   - Uptie concept: Exists or removed for EGO?
   - Single image per skill or multiple variants?

4. **Passive Data Structure**
   - Single passive object or array with one element?
   - Same PassiveData interface or extended?
   - Same passiveSin/passiveEA/passiveType pattern?

---

## Reusable Components (No Changes)

These 4 components work for both Identity and EGO without modification:
- **CoinDisplay** - Coin EA format is universal
- **SkillDescription** - Description + coin descriptions pattern is universal
- **SearchBar** - Fully parameterized
- **IconFilter** - Fully parameterized

---

## Code Volume Estimate

### New Files to Create
- EGOHeader.tsx (~80 lines)
- EGOSkillCard.tsx (~60 lines)
- EGOSkillImageComposite.tsx (~120 lines)
- EGOSkillInfoPanel.tsx (~70 lines)
- SinCostPanel.tsx (~50 lines)
- SinResistancePanel.tsx (~80 lines)
- EGOPassiveDisplay.tsx (~40 lines)

**Total New Code**: ~500 lines

### Files to Modify
- EGODetailPage.tsx (expand from 13 → 150+ lines)
- EGOTypes.ts (expand type definitions)
- identityUtils.ts (add 2-3 EGO-specific path functions)

**Total Modified**: ~200 lines

**Grand Total**: ~700 lines of new/modified code

---

## Key Files Referenced

### Source Code Files Read
- `/frontend/src/routes/IdentityDetailPage.tsx` (11,861 bytes)
- `/frontend/src/routes/EGODetailPage.tsx` (414 bytes - placeholder)
- `/frontend/src/components/identity/*.tsx` (10 detail-related files)
- `/frontend/src/components/ego/*.tsx` (5 list-related files)
- `/frontend/src/types/IdentityTypes.ts`, `EGOTypes.ts`
- `/frontend/src/lib/identityUtils.ts`
- `/frontend/src/hooks/useIdentityData.ts`, `useEGOData.ts`

### Documentation Files Analyzed
- `/docs/03-ego-browser/02-ego-detail-page/requirements.md`
- `/docs/03-ego-browser/02-ego-detail-page/01-copy-ui-from-id-and-refactor-components/instructions.md`
- `/docs/03-ego-browser/01-searchable-filterable-list/*/research.md`

---

## Architecture Decisions to Make

### 1. Sin Cost Display
- Single value or multiple (per-sin)?
- New panel or integrated into status area?
- Icon/visual representation?

### 2. Sin Resistance Display
- Column count: 7 (sins) or 8 (sins + defense)?
- Grid vs. horizontal flex layout?
- Color scheme: Reuse Identity resistance colors?
- Responsive behavior on small screens?

### 3. Skill Image Composition
- How many layers for EGO skill image?
- Need octagonal clip-path or circular/default?
- Attack type display: Applicable or removed?
- Sin frame selection: Same logic as Identity?

### 4. Optional Corrosion Skill
- Hide tab if no corrosion skill exists?
- Show disabled tab with message?
- Adjust 2-tab layout to 1-tab if no corrosion?

### 5. Component Reusability
- Make StatusPanel/ResistancePanel generic with props?
- Or create specific Sin* components only?
- Consider future refactoring: common base components?

---

## Testing Considerations

1. **Data Loading**
   - Verify EGO data loading (dynamic import)
   - Handle missing corrosion skills gracefully
   - Error fallback for missing images

2. **Image Rendering**
   - Test image composition layers
   - Verify fallback behavior
   - Responsive image sizing

3. **Skill Tab Navigation**
   - Tab switching works for 2 tabs
   - Corrosion tab disabled when no data
   - State persistence during re-renders

4. **Data Display**
   - Sin cost displays correctly
   - Sin resistance values show with correct colors
   - Passive displays without category label

---

## Next Steps

1. **Extend EGOTypes.ts** with complete data structures
2. **Analyze EGO data file** (20101.json) to confirm structure
3. **Create EGOHeader.tsx** (simplest component)
4. **Create SinCost/SinResistance panels** (understand data structure)
5. **Create EGOSkillCard + children** (most complex)
6. **Integrate into EGODetailPage.tsx**
7. **Test with actual EGO data**

