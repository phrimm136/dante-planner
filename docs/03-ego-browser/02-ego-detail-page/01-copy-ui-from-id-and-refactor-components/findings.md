# Key Findings: Direct Answers to Research Questions

## Question 1: What components are used in IdentityDetailPage?

**Direct Component Dependencies** (in render order):
1. **IdentityHeader** - Title + character image with buttons
2. **StatusPanel** - HP, Speed, Defense indicators
3. **ResistancePanel** - Slash/Pierce/Blunt resistance with categories
4. **StaggerPanel** - Stagger threshold HP values
5. **TraitsDisplay** - Character traits display
6. **Sanity Panel** - Custom inline markup (no component)
7. **Skill Selector** - Custom button group (no component)
8. **SkillCard** (rendered in loop) - Master skill display component
   - SkillImageComposite (child)
   - SkillInfoPanel (child)
     - CoinDisplay (child)
   - SkillDescription (child)
9. **Passive Panel** - Custom inline markup (no component)

**Total: 10 unique components, 3 are composite leaf components**

---

## Question 2: Which components can be made generic/common vs EGO-specific?

### Already Generic (No Changes Needed)
- **CoinDisplay** - Works for any coin EA string format
- **SkillDescription** - Just needs desc + coinDescs data
- **SearchBar** - Fully parameterized with placeholder
- **IconFilter** - Fully parameterized with options + icon resolver

### Light Refactoring (Make Generic)
- **StatusPanel** - Add props for icon paths and labels instead of hardcoding
- **TraitsDisplay** - Optional prop for i18n data or omit entirely for EGO

### Moderate Refactoring (Create EGO Variants)
- **IdentityHeader** → **EGOHeader**
  - Remove image toggle (remove swap button)
  - Replace rarity icon with rank display
  - Keep expand button or remove
  
- **SkillCard** → **EGOSkillCard**
  - Replace 4 tabs (skill1/2/3/def) with 2 tabs (awakening/corrosion)
  - Remove atkWeight display
  - Add sanity cost display after attack level
  - Handle missing corrosion skills gracefully

- **SkillImageComposite** → **EGOSkillImageComposite**
  - Change image paths: `/images/EGO/{id}/awaken_profile.webp` vs `erosion_profile.webp`
  - Remove octagonal clip-path (use default/circular)
  - Simplify layer composition (may not need 5 layers)
  - Remove atkType corner display

- **SkillInfoPanel** → **EGOSkillInfoPanel** OR create generic version
  - Remove atkWeight display line
  - Add sanity cost display
  - Different layout needed?

### Create New Components
- **SinCostPanel** - Display sin cost values
- **SinResistancePanel** - Display multiple sin resistances with color coding
- **EGOPassiveDisplay** - Simplified (no support passive, no category indicator)

### Remove for EGO
- **StaggerPanel** - Remove entirely (EGO has no stagger)
- **Sanity Section** - Remove entirely (EGO has no sanity)
- **Support Passive Section** - Remove entirely (EGO has one passive only)

---

## Question 3: How are skills displayed (skill1/2/3/def) and how to adapt for awakening/corrosion?

### Current Identity Skill Display Pattern

**Data Structure**:
```typescript
skills: {
  skill1: SkillData[],  // Array of variants
  skill2: SkillData[],  // Array of variants
  skill3: SkillData[],  // Array of variants
  skillDef: SkillData[] // Array of variants
}
```

**UI Rendering**:
```
1. Render 4 tab buttons (skill1, skill2, skill3, skillDef)
2. User clicks tab → state: activeSkillSlot = 'skill1' | 'skill2' | 'skill3' | 'skillDef'
3. Loop through skills[activeSkillSlot] array
4. For each variant, render SkillCard with:
   - identityId, skillSlot (1-4), variantIndex (0+), skillData, skillVariantI18n, uptie
5. SkillCard renders:
   - SkillImageComposite: skill{slotNum}{variantSuffix}.webp
   - SkillInfoPanel: Shows levelUp + atkWeight
   - SkillDescription: desc + coinDescs
```

### EGO Skill Adaptation

**New Data Structure** (inferred from requirements):
```typescript
// Option A: Similar structure to Identity
skills: {
  awakening: EGOSkillData[],  // May have variants
  corrosion: EGOSkillData[]    // May have variants or be optional
}

// Option B: Simpler flat structure
awakeningSkills: EGOSkillData
corrosionSkills: EGOSkillData | null // Optional
```

**UI Rendering for EGO**:
```
1. Render 2 tab buttons (Awakening, Corrosion)
   - Disable or hide Corrosion tab if skill doesn't exist
2. User clicks tab → state: activeSkill = 'awakening' | 'corrosion'
3. Render EGOSkillCard (single per tab, no variants loop):
   - egoId, skill type (awakening/corrosion)
   - egoskillData, egoSkillI18n, sanity cost
4. EGOSkillCard renders:
   - EGOSkillImageComposite: {id}/awaken_profile.webp OR erosion_profile.webp
   - EGOSkillInfoPanel: Shows level + sanity cost (replaces atkWeight)
   - SkillDescription: Reuse as-is (same desc + coinDescs format)
```

**Key Differences**:
- Identity: 4 tabs, multiple variants per tab → EGO: 2 tabs, single skill per tab
- Identity: uptie concept (3 vs 4) → EGO: likely no uptie (just one image)
- Identity: atkWeight → EGO: sanity cost
- Identity: No optional skills → EGO: corrosion may not exist
- Image paths: Different naming (awaken_profile.webp vs skill01.webp)

---

## Question 4: How is the sanity section structured (needs to be removed)?

### Current Sanity Section Code

**Location**: IdentityDetailPage.tsx, lines 170-205 (36 lines)

```tsx
<div className="border rounded p-4 space-y-4">
  <div className="font-semibold">Sanity</div>
  
  <div className="flex gap-3">
    <div className="w-8 h-8 bg-red-500 rounded shrink-0" />
    <div className="flex-1">
      <div className="font-medium text-sm">Panic Type</div>
      <div className="text-xs text-muted-foreground">
        Panic description goes here
      </div>
    </div>
  </div>
  
  <div className="flex gap-3">
    <div className="w-8 h-8 bg-orange-500 rounded shrink-0" />
    <div className="flex-1">
      <div className="font-medium text-sm">Sanity Increment Condition</div>
      <div className="text-xs text-muted-foreground">
        Increment condition description
      </div>
    </div>
  </div>
  
  <div className="flex gap-3">
    <div className="w-8 h-8 bg-yellow-500 rounded shrink-0" />
    <div className="flex-1">
      <div className="font-medium text-sm">Sanity Decrement Condition</div>
      <div className="text-xs text-muted-foreground">
        Decrement condition description
      </div>
    </div>
  </div>
</div>
```

**Removal for EGO**:
- Delete this entire section from EGODetailPage
- The bottom-left panel will be empty or replaced with SinCostPanel + SinResistancePanel
- Adjust layout spacing (currently using space-y-6 in LEFT COLUMN)

---

## Question 5: How are resistances/status/traits displayed (needs to be replaced with sin resistance/cost)?

### Current Identity Status/Resistance/Traits Layout

**Current Component Layout** (lines 148-166):
```tsx
{/* Three Horizontal Status Panels */}
<div className="grid grid-cols-3 gap-2">
  <StatusPanel ... />        {/* HP, Speed, Defense */}
  <ResistancePanel ... />    {/* Slash, Pierce, Blunt */}
  <StaggerPanel ... />       {/* Stagger thresholds */}
</div>

{/* Traits Panel */}
<TraitsDisplay traits={identityData.traits} />
```

### EGO Status Replacement

**Current StatusPanel** (52 lines):
```tsx
// Shows 3 columns: HP icon, Speed icon, Defense icon
// Uses: BASE_LEVEL constant for calculation
// Props: hp, minSpeed, maxSpeed, defense

// For EGO, need to replace with:
// - Sin Cost display (how much sanity to activate)
// - Sin Resistance display (resistance to each sin type)
```

**Sin Cost Component** (new):
- Single value or multiple values?
- Icon: TBD based on data structure
- Layout: Simple display like StatusPanel

**Sin Resistance Component** (new):
- Multiple sin types (7 sins + defense?)
- Similar to ResistancePanel but with 7+ columns instead of 3
- Color coding similar to Identity resistance categories
- Format: icon + category + value or just icon + value

### Data Location

Per requirements: "sin resistance and sin cost where its values are described in `/static/data/EGO/{id}.json`"

**Need to verify EGO data structure**:
- How is sin cost stored? Single value or per-sin?
- How many sin resistance values? (7 sins, or all 8 including defense?)
- What are the range/categories for sin resistance values?

---

## Question 6: How are passives displayed (support passives need removal)?

### Current Passive Display Structure

**Location**: IdentityDetailPage.tsx, lines 278-325 (48 lines)

```tsx
<div className="border rounded p-4 space-y-4">
  <div className="font-semibold">Passives</div>

  {/* Regular Passives Section */}
  <div className="space-y-3">
    <div className="text-sm font-medium">Passive</div>
    {identityData.passive.map((passive, idx) => (
      <div key={idx} className="border rounded p-3 space-y-2">
        <div className="bg-muted px-3 py-1 rounded-full text-sm inline-block">
          {identityI18n.passive[idx]?.name || `Passive ${idx + 1}`}
        </div>
        {passive.passiveSin && passive.passiveSin.length > 0 && (
          <div className="text-xs">
            {passive.passiveSin.map((sin, i) => (
              <span key={i} className="mr-2">
                {sin} x{passive.passiveEA?.[i]} {passive.passiveType}
              </span>
            ))}
          </div>
        )}
        <div className="text-sm text-muted-foreground">
          {identityI18n.passive[idx]?.desc || 'Passive effect description'}
        </div>
      </div>
    ))}
  </div>

  {/* Support Passive Section */}
  <div className="space-y-3">
    <div className="text-sm font-medium">Support Passive</div>
    <div className="border rounded p-3 space-y-2">
      <div className="bg-muted px-3 py-1 rounded-full text-sm inline-block">
        {identityI18n.sptPassive.name || 'Support Passive'}
      </div>
      {identityData.sptPassive.passiveSin && ...}
    </div>
  </div>
</div>
```

### EGO Passive Simplification

**Changes Required**:
1. Remove "Passive" category label (the `text-sm font-medium` div)
2. Remove "Support Passive" section entirely
3. Render single passive directly without category wrapper
4. Keep passive card styling (border, padding, spacing)

**Simplified EGO Passive Structure**:
```tsx
<div className="border rounded p-4 space-y-3">
  {/* Single passive card (no category label) */}
  <div className="border rounded p-3 space-y-2">
    <div className="bg-muted px-3 py-1 rounded-full text-sm inline-block">
      {egoI18n.passive.name}
    </div>
    {egoData.passive.passiveSin && (
      <div className="text-xs">
        {egoData.passive.passiveSin.map((sin, i) => (
          <span key={i} className="mr-2">
            {sin} x{egoData.passive.passiveEA?.[i]} {egoData.passive.passiveType}
          </span>
        ))}
      </div>
    )}
    <div className="text-sm text-muted-foreground">
      {egoI18n.passive.desc}
    </div>
  </div>
</div>
```

---

## Question 7: How is rarity/star shown (needs to be replaced with EGO rank)?

### Current Rarity Display

**IdentityHeader Component** (lines 29-35):
```tsx
<div className="flex items-center gap-3">
  <img
    src={getRarityIconPath(grade)}
    alt={`${grade} star`}
    className="w-8 h-8 object-contain"
  />
  <h1 className="text-2xl font-bold">{name}</h1>
</div>
```

**getRarityIconPath Utility**:
```typescript
export function getRarityIconPath(grade: number): string {
  return `/images/UI/identity/rarity{grade}.webp`
  // Examples: rarity1.webp, rarity2.webp, ... rarity5.webp
}
```

### EGO Rank Replacement

**New EGOHeader Component** (similar structure):
```tsx
<div className="flex items-center gap-3">
  <img
    src={getEGORankIconPath(rank)}
    alt={`${rank} rank`}
    className="w-8 h-8 object-contain"
  />
  <h1 className="text-2xl font-bold">{name}</h1>
</div>
```

**getEGORankIconPath Utility** (already exists):
```typescript
export function getEGORankIconPath(rank: string): string {
  return `/images/UI/EGO/{rank}.webp`
  // Examples: Zayin.webp, Teth.webp, He.webp, Waw.webp, Aleph.webp
}
```

**Data Differences**:
- Identity: grade (number 1-5) → EGO: rank (string: Zayin|Teth|He|Waw|Aleph)
- Icon path prefix: `/images/UI/identity/` → `/images/UI/EGO/`
- Icon naming: `rarity{N}.webp` → `{Rank}.webp`

---

## Question 8: How does image toggle work (needs to be removed)?

### Current Image Toggle Implementation

**IdentityHeader Component** (lines 12-17, 54-90):
```tsx
// State management
const is1Star = grade === 1
const [imageVariant, setImageVariant] = useState<ImageVariant>(is1Star ? 'normal' : 'gacksung')

const handleSwapImage = () => {
  setImageVariant((prev) => (prev === 'gacksung' ? 'normal' : 'gacksung'))
}

// UI with two buttons
<div className="absolute top-4 left-4 flex flex-col gap-2">
  {/* Swap button */}
  <button
    onClick={handleSwapImage}
    disabled={is1Star}  {/* Disabled for 1-star identities */}
    className="relative w-12 h-12 disabled:opacity-50 disabled:cursor-not-allowed"
  >
    <img src="/images/UI/common/buttonSwapImage.webp" alt="Swap image" />
  </button>

  {/* Expand button */}
  <button
    onClick={handleExpandImage}
    className="relative w-12 h-12"
  >
    <img src="/images/UI/common/buttonExpandImage.webp" alt="Expand image" />
  </button>
</div>
```

**Image Variants Supported**:
- `gacksung` (default for 2+ star): `/images/identity/{id}/gacksung.webp`
- `normal` (default for 1-star): `/images/identity/{id}/normal.webp`
- Fallback: If gacksung image fails, automatically load normal

### EGO Image Simplification

**Changes Required**:
1. Remove imageVariant state
2. Remove swap button entirely
3. Keep expand button OR remove it too (unclear from requirements)
4. Use single image: `/images/EGO/{id}/cg.webp` (circular EGO image)

**Simplified EGOHeader**:
```tsx
const handleExpandImage = () => {
  const imagePath = getEGOImagePath(egoId)
  window.open(imagePath, '_blank')
}

<div className="relative bg-muted rounded-lg overflow-hidden">
  <img
    src={getEGOImagePath(egoId)}
    alt={name}
    className="w-full h-auto object-contain rounded-full"
  />

  {/* Optional: Single expand button */}
  <div className="absolute top-4 left-4">
    <button onClick={handleExpandImage} className="relative w-12 h-12">
      <img src="/images/UI/common/buttonExpandImage.webp" alt="Expand image" />
    </button>
  </div>
</div>
```

---

## Question 9: What are the styling patterns and component composition approaches?

### Layout Patterns

**Two-Column Grid** (responsive):
```tsx
<div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
  {/* LEFT COLUMN */}
  <div className="space-y-6">
    {/* Sections stacked vertically */}
  </div>
  {/* RIGHT COLUMN */}
  <div className="space-y-6">
    {/* Sections stacked vertically */}
  </div>
</div>
```

**Panel Pattern** (status, resistance, stagger):
```tsx
<div className="border rounded p-3 space-y-2">
  <div className="font-semibold text-sm text-center">Label</div>
  {/* Content with flex/grid layout */}
</div>
```

**Tab Button Pattern** (skill selector):
```tsx
<button
  onClick={() => setActiveSkillSlot('skill1')}
  className={`flex-1 py-2 px-4 rounded ${
    activeSkillSlot === 'skill1'
      ? 'bg-primary text-primary-foreground'  {/* Selected */}
      : 'bg-muted'                              {/* Default */}
  }`}
>
  Skill 1
</button>
```

**Absolute Overlay Pattern** (buttons on image):
```tsx
<div className="relative bg-muted rounded-lg overflow-hidden">
  <img src="..." />
  <div className="absolute top-4 left-4 flex flex-col gap-2 pointer-events-none">
    {/* Buttons positioned absolutely */}
  </div>
</div>
```

### Color Scheme

**Semantic Colors** (from shadcn/tailwind):
- Text: `text-foreground` (primary), `text-muted-foreground` (secondary)
- Background: `bg-background`, `bg-card`, `bg-muted`
- Border: `border-border`, `border-primary`
- Resistance: `text-red-500`, `text-orange-300`, `text-amber-100`, `text-gray-400`, `text-gray-500`

**Icon/Image Sizes**:
- Small icons: `w-4 h-4`, `w-6 h-6`
- Medium icons: `w-8 h-8`
- Large containers: `w-32 h-32` (skill image), `w-40 h-56` (card)

### Component Composition Strategy

**Three-Tier Approach**:

1. **Utility Components** (no state, pure display):
   - CoinDisplay, SkillDescription, StatusPanel
   - Props in, JSX out, no side effects

2. **Composite Components** (combine utility components):
   - SkillCard = SkillImageComposite + SkillInfoPanel + SkillDescription
   - Layout + coordinate child components

3. **Container Components** (manage state, orchestrate):
   - IdentityDetailPage = state + data loading + layout
   - Uses composite and utility components

**Props Drilling Pattern**:
- Parent loads data
- Parent passes data down as props through multiple levels
- Children don't know about data source (JSON files, API, etc.)
- No global state management (no Redux, Zustand)

---

## Summary of Refactoring Effort

| Task | Components | Effort | Files |
|------|-----------|--------|-------|
| Copy layout structure | Container | Low | EGODetailPage.tsx |
| Create EGO headers | EGOHeader | Medium | EGOHeader.tsx (new) |
| Create EGO skill cards | EGOSkillCard, EGOSkillImageComposite, EGOSkillInfoPanel | High | 3 new files |
| Create status replacement | SinCostPanel, SinResistancePanel | High | 2 new files |
| Simplify passives | EGOPassiveDisplay | Low | 1 new file |
| Type definitions | EGOData, EGOSkillData, EGOI18n | Medium | EGOTypes.ts (update) |
| Utilities | EGO-specific path functions | Low | identityUtils.ts (update) |
| **Total** | | **Very High** | **8+ files** |

