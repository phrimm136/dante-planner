# EGO Gift Implementation - Architecture & Flow Diagrams

## 1. Component Hierarchy Diagram

```
EGOGiftPage (routes)
│
├─ State Management
│  ├─ selectedSinners: Set<string>
│  ├─ selectedKeywords: Set<string>
│  └─ searchQuery: string
│
└─ Layout Structure
   ├─ <h1> Title
   ├─ <p> Description
   │
   └─ Main Content Container (bg-background, rounded-lg, p-6)
      │
      ├─ Filter & Search Row (flex justify-between gap-4)
      │  │
      │  ├─ Left Filters Section (flex gap-4)
      │  │  ├─ EGOGiftSinnerFilter
      │  │  │  └─ <IconFilter>
      │  │  │     ├─ Clear All Button
      │  │  │     └─ Sinner Icon Options (scrollable)
      │  │  │
      │  │  └─ EGOGiftKeywordFilter
      │  │     └─ <IconFilter>
      │  │        ├─ Clear All Button
      │  │        └─ Keyword Icon Options (scrollable)
      │  │
      │  └─ Right Search Section (shrink-0)
      │     └─ EGOGiftSearchBar
      │        └─ <SearchBar>
      │           ├─ Search Icon
      │           └─ Text Input (debounced)
      │
      └─ Content Section
         └─ EGOGiftList
            ├─ useEGOGiftData() → filtered data
            ├─ useSearchMappings() → reverse keyword maps
            ├─ Filtering Algorithm
            │  ├─ Sinner Filter (OR logic)
            │  ├─ Keyword Filter (AND logic)
            │  └─ Search Filter (OR logic)
            │
            └─ Responsive Grid Layout
               └─ EGOGiftCard[] (grid cols-2 to cols-8)
                  ├─ Gift Image
                  ├─ Tier Icon
                  ├─ Cost Display
                  └─ Link to Detail Page
```

---

## 2. Data Flow Diagram

```
Data Sources
│
├─ /static/data/EGOGiftSpecList.json
│  └─ {id: {keywords, themePack, cost, tier}}
│
├─ /static/i18n/EN/EGOGiftNameList.json
│  └─ {id: name}
│
├─ /static/i18n/EN/gift/{id}.json
│  └─ {name, descs[], obtain}
│
└─ /static/i18n/EN/keywordMatch.json
   └─ {[keyword]: naturalLanguage}
   
   │
   ├─ useEGOGiftData Hook
   │  ├─ Import spec data
   │  ├─ Import i18n names
   │  ├─ Merge together
   │  └─ Return EGOGift[]
   │
   └─ useSearchMappings Hook
      ├─ Import keyword match
      ├─ Build reverse map
      └─ Return {keywordToValue Map}
      
      │
      ├─ EGOGiftList Component
      │  ├─ Receives: selectedSinners, selectedKeywords, searchQuery
      │  ├─ Calls useEGOGiftData() → gifts[]
      │  ├─ Calls useSearchMappings() → mappings
      │  ├─ Applies Filters
      │  │  ├─ Sinner Filter
      │  │  ├─ Keyword Filter
      │  │  └─ Search Filter
      │  ├─ Optional: Applies Sorting
      │  └─ Returns filtered/sorted data
      │
      └─ Render EGOGiftCard[] for each item
```

---

## 3. Filter Logic Decision Tree

```
For each EGOGift item:

START
│
├─ Sinner Filter Active?
│  ├─ YES: Item.sinner in selectedSinners?
│  │  ├─ YES → Continue
│  │  └─ NO → EXCLUDE
│  └─ NO: Continue (no sinner filter applied)
│
├─ Keyword Filter Active?
│  ├─ YES: Item has ALL selectedKeywords?
│  │  ├─ YES → Continue
│  │  └─ NO → EXCLUDE
│  └─ NO: Continue (no keyword filter applied)
│
├─ Search Filter Active?
│  ├─ YES: Matches name OR keyword?
│  │  ├─ Name: Case-insensitive partial match
│  │  │  └─ item.name.toLowerCase().includes(query)
│  │  │
│  │  ├─ Keyword: Via reverse mapping
│  │  │  ├─ keywordToValue.get(query)?
│  │  │  ├─ For each matched bracketed keyword
│  │  │  └─ Check if item.keywords includes it
│  │  │
│  │  ├─ Match found?
│  │  │  ├─ YES → Include
│  │  │  └─ NO → EXCLUDE
│  │  │
│  │  └─ NO: Include (no search filter applied)
│
└─ INCLUDE item in results
```

---

## 4. Reverse Mapping Process Flow

```
Input Data: keywordMatch.json
├─ "[combustion]" → "burn"
├─ "[laceration]" → "bleed"
├─ "[vibration]" → "tremor"
├─ "[rupture]" → "rupture"
├─ "[sinking]" → "sinking"
├─ "[poise]" → "poise"
└─ "[charge]" → "charge"

         ↓
    
Reverse Mapping Algorithm:

For each [bracketedKey: naturalLanguage] pair:
│
├─ Convert naturalLanguage to lowercase
│  └─ "burn" → "burn"
│
├─ Check if "burn" exists in Map
│  ├─ YES → Append [combustion] to existing array
│  └─ NO → Create new array with [combustion]
│
└─ Result in Map:
   └─ "burn" → ["[combustion]"]
   └─ "bleed" → ["[laceration]"]
   └─ "tremor" → ["[vibration]"]
   └─ "rupture" → ["[rupture]"]
   └─ "sinking" → ["[sinking]"]
   └─ "poise" → ["[poise]"]
   └─ "charge" → ["[charge]"]

         ↓

Search Example:

User searches: "burn"
│
├─ Query: "burn"
├─ Lookup: keywordToValue.get("burn")
├─ Result: ["[combustion]"]
├─ Check: Does gift.keywords include "[combustion]"?
└─ Match!
```

---

## 5. Filter Combination Logic (Truth Table)

```
Given: selectedSinners, selectedKeywords, searchQuery

Scenario 1: No Filters Selected
├─ Sinner: empty Set
├─ Keywords: empty Set
├─ Search: empty string
└─ Result: Show ALL items (pass all optional checks)

Scenario 2: Sinner Only
├─ Filter: item.sinner in selectedSinners
└─ Result: Show only items matching selected sinners

Scenario 3: Keywords Only
├─ Filter: item has ALL selected keywords
└─ Result: Show items with matching keyword combinations

Scenario 4: Search Only
├─ Filter: item matches name OR keyword
└─ Result: Show items matching search query

Scenario 5: Sinner + Keywords
├─ Sinner Filter: item.sinner matches
├─ AND Keywords Filter: item has all selected keywords
└─ Result: Intersection of both filters (most restrictive)

Scenario 6: Sinner + Search
├─ Sinner Filter: item.sinner matches
├─ AND Search Filter: item matches search
└─ Result: Items from selected sinners matching search

Scenario 7: Keywords + Search
├─ Keywords Filter: item has all selected keywords
├─ AND Search Filter: item matches search
└─ Result: Items with keywords that match search

Scenario 8: Sinner + Keywords + Search (All Filters)
├─ Sinner Filter: item.sinner matches
├─ AND Keywords Filter: item has all selected keywords
├─ AND Search Filter: item matches search
└─ Result: Most restrictive - items passing ALL filters
```

---

## 6. Component Props & State Flow

```
EGOGiftPage Component
│
├─ State
│  ├─ selectedSinners: Set<string>
│  ├─ selectedKeywords: Set<string>
│  └─ searchQuery: string
│
└─ Passes Props Down
   │
   ├─ EGOGiftSinnerFilter
   │  ├─ Props:
   │  │  ├─ selectedSinners: Set<string>
   │  │  └─ onSelectionChange: (sinners: Set<string>) => void
   │  │
   │  └─ Uses: setSelectedSinners state updater
   │
   ├─ EGOGiftKeywordFilter
   │  ├─ Props:
   │  │  ├─ selectedKeywords: Set<string>
   │  │  └─ onSelectionChange: (keywords: Set<string>) => void
   │  │
   │  └─ Uses: setSelectedKeywords state updater
   │
   ├─ EGOGiftSearchBar
   │  ├─ Props:
   │  │  ├─ searchQuery: string
   │  │  └─ onSearchChange: (query: string) => void
   │  │
   │  └─ Uses: setSearchQuery state updater
   │
   └─ EGOGiftList
      ├─ Props:
      │  ├─ selectedSinners: Set<string>
      │  ├─ selectedKeywords: Set<string>
      │  └─ searchQuery: string
      │
      └─ Internal: useEGOGiftData() & useSearchMappings()
         └─ Computes: filteredGifts[]
```

---

## 7. Data Transformation Pipeline

```
Raw JSON Data
   ↓
useEGOGiftData Hook
├─ Step 1: Load EGOGiftSpecList
│  └─ Object.entries() to array
│
├─ Step 2: Load EGOGiftNameList (i18n)
│  └─ Map ID → localized name
│
├─ Step 3: Merge Data
│  └─ Combine spec + name for each ID
│
└─ Output: EGOGift[]
   ├─ id: string
   ├─ name: string (from i18n)
   ├─ tier: number
   ├─ cost: number
   ├─ keywords: string[] (bracket notation)
   └─ themePack: string[]
   
   ↓
   
EGOGiftList Component
├─ Receives: EGOGift[]
├─ Receives: Filter states
├─ Receives: Search mappings
│
├─ Step 1: Apply Filters
│  └─ Array.filter() with multi-criteria logic
│
├─ Step 2: Apply Sorting (optional)
│  └─ Array.sort() with tier/cost/name criteria
│
└─ Output: Filtered & Sorted EGOGift[]
   
   ↓
   
Render Layer
├─ Map to EGOGiftCard components
├─ Display in responsive grid
└─ Each card links to detail page
```

---

## 8. Search Debounce Timeline

```
User Types: "b" "u" "r" "n"

Time  Input  Debounce Timer  Action
──────────────────────────────────────
0ms   "b"    Start (100ms)
10ms  "u"    Reset (100ms)
20ms  "r"    Reset (100ms)
30ms  "n"    Reset (100ms)
130ms         Timer expires   → Call onSearchChange("burn")
             
Result: Single search triggered after user stops typing for 100ms
```

---

## 9. Recommended File Structure

```
frontend/src/
│
├─ routes/
│  └─ EGOGiftPage.tsx (main page component)
│
├─ components/
│  └─ egogift/
│     ├─ EGOGiftList.tsx (filtering logic)
│     ├─ EGOGiftCard.tsx (individual item)
│     ├─ EGOGiftSinnerFilter.tsx
│     ├─ EGOGiftKeywordFilter.tsx
│     ├─ EGOGiftSearchBar.tsx
│     └─ EGOGiftTierFilter.tsx (NEW - for sorting)
│
├─ hooks/
│  └─ useEGOGiftData.ts (data loading)
│
└─ (Reuse from common/)
   ├─ components/common/IconFilter.tsx
   ├─ components/common/SearchBar.tsx
   └─ hooks/useSearchMappings.ts

static/
│
└─ data/
│  └─ EGOGiftSpecList.json
│
└─ i18n/
   └─ EN/
      ├─ EGOGiftNameList.json
      ├─ gift/
      │  └─ {id}.json
      └─ keywordMatch.json (already exists)
```

---

## 10. Performance Optimization Diagram

```
Performance Bottlenecks & Solutions:

1. Data Loading
   ├─ Problem: Reload on every render
   ├─ Solution: useMemo with i18n.language dependency
   └─ Result: Only recalc when language changes

2. Keyword Reverse Mapping
   ├─ Problem: Rebuild on every search
   ├─ Solution: useSearchMappings with useMemo
   └─ Result: O(1) lookup, recalc only on language change

3. Filtering Array
   ├─ Problem: Re-filter entire array on state change
   ├─ Solution: JavaScript Array.filter() (optimized)
   └─ Result: Acceptable for ~100 items; consider useMemo if > 1000

4. Search Input
   ├─ Problem: Trigger filter on every keystroke
   ├─ Solution: Debounce with 100ms delay
   └─ Result: 90%+ keystroke reduction

5. Icon Rendering
   ├─ Problem: Load all icon images
   ├─ Solution: Lazy load via CSS/img alt text
   └─ Result: Browser handles async image loading

6. Re-renders
   ├─ Problem: All filters update page state
   ├─ Solution: React batching + state lift
   └─ Result: Single re-render per user action
```

---

## 11. Sorting State Management (Optional Enhancement)

```
Add to EGOGiftPage State:

type SortField = 'tier' | 'cost' | 'name'
type SortDirection = 'asc' | 'desc'

interface SortConfig {
  field: SortField
  direction: SortDirection
}

const [sortConfig, setSortConfig] = useState<SortConfig>({
  field: 'tier',
  direction: 'desc'  // Higher tier first
})

Sort Buttons UI:
├─ Sort by Tier (↓)
├─ Sort by Cost (↑)
└─ Sort by Name (↑)

Toggle Sort Direction:
└─ Click same sort button again to toggle asc/desc

Apply Sorting:
└─ In EGOGiftList: 
   applySorting(filteredGifts, sortConfig)
   
Before display to user
```

---

## 12. i18n Language Support Flow

```
useEGOGiftData Hook:

Current Language: 'EN'
   ├─ Load enEGOGiftNameList
   ├─ Load enKeywordMatch
   └─ Build data arrays

Language Changes: 'EN' → 'KO' (future)
   ├─ Dependency change detected
   ├─ Re-execute useMemo
   ├─ Load koEGOGiftNameList (if exists)
   ├─ Fallback to ID if missing
   └─ Return updated data

Result:
├─ Users see localized names
├─ Search works in native language
└─ Graceful fallback to IDs if translation missing
```

---

## 13. Error Handling Scenarios

```
Scenario 1: Missing Gift Data
├─ Issue: EGOGiftSpecList missing ID
├─ Impact: Gift not in list
└─ Solution: useEGOGiftData validates keys

Scenario 2: Missing Translation
├─ Issue: EGOGiftNameList missing ID
├─ Impact: Name falls back to ID
└─ Solution: Already implemented in hook

Scenario 3: Missing i18n Detail
├─ Issue: gift/{id}.json doesn't exist
├─ Impact: No description/obtain info
└─ Solution: Show basic info, handle gracefully

Scenario 4: Malformed Keywords
├─ Issue: Keyword not in bracket notation
├─ Impact: Reverse mapping fails
└─ Solution: Log warning, treat as regular string

Scenario 5: Empty Results
├─ Issue: No items match all filters
├─ Impact: Empty grid
└─ Solution: Show "No items found" message
```

