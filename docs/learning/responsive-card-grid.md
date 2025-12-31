# ResponsiveCardGrid - CSS Grid Auto-Fill Pattern

This document explains the CSS Grid technique used for responsive card layouts that dynamically adjust column count while maintaining centered alignment.

---

## Problem: Fixed Breakpoint Grids

The traditional Tailwind approach uses fixed breakpoint columns:

```tsx
// Old approach - problems with fixed widths
<div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8 gap-4">
  {items.map(item => <Card key={item.id} />)}  {/* Card has w-40 (160px) */}
</div>
```

**Issues:**
| Problem | Explanation |
|---------|-------------|
| **Overlap** | If breakpoint forces 8 columns in 1000px container, each cell is ~125px but card is 160px |
| **Gaps inconsistent** | Cards don't fill space evenly at breakpoint boundaries |
| **Not truly responsive** | Jumps between fixed column counts, not fluid |
| **Alignment issues** | `justify-items-center` centers items within cells, not the grid itself |

---

## Solution: CSS Grid Auto-Fill

```css
display: grid;
grid-template-columns: repeat(auto-fill, 160px);
justify-content: center;
gap: 16px;
width: 100%;
```

### How It Works

1. **`auto-fill`**: Creates as many 160px columns as fit in container width
2. **Fixed column size (160px)**: Cards don't stretch (unlike `minmax(160px, 1fr)`)
3. **`justify-content: center`**: Centers entire grid, creating dynamic padding
4. **`width: 100%`**: Required for auto-fill to calculate available space

### Visual Example

```
Container: 800px wide

With auto-fill + 160px columns + 16px gap:
- Columns that fit: floor((800 + 16) / (160 + 16)) = 4 columns
- Grid width: 4 × 160 + 3 × 16 = 688px
- Dynamic padding: (800 - 688) / 2 = 56px each side

┌────────────────────────────────────────────────┐
│  56px  │ Card │ Card │ Card │ Card │  56px    │
│ padding│ 160  │ 160  │ 160  │ 160  │ padding  │
└────────────────────────────────────────────────┘
```

---

## Implementation: ResponsiveCardGrid Component

```tsx
// components/common/ResponsiveCardGrid.tsx
interface ResponsiveCardGridProps {
  cardWidth: number
  gap?: number
  children: React.ReactNode
  className?: string
}

export function ResponsiveCardGrid({
  cardWidth,
  gap = CARD_GRID.DEFAULT_GAP,
  children,
  className,
}: ResponsiveCardGridProps) {
  return (
    <div
      className={cn('grid w-full', className)}
      style={{
        gridTemplateColumns: `repeat(auto-fill, ${String(cardWidth)}px)`,
        gap: `${String(gap)}px`,
        justifyContent: 'center',
      }}
    >
      {children}
    </div>
  )
}
```

### Why Inline Style Instead of Tailwind?

Tailwind arbitrary values work (`grid-cols-[repeat(auto-fill,160px)]`) but:
- Card width is dynamic (passed as prop)
- Constants centralize values in `CARD_GRID.WIDTH.*`
- Inline style keeps component flexible

---

## Constants

```typescript
// lib/constants.ts
export const CARD_GRID = {
  WIDTH: {
    IDENTITY: 160,  // w-40 = 160px
    EGO: 160,       // w-40 = 160px
    EGO_GIFT: 96,   // w-24 = 96px
  },
  DEFAULT_GAP: 16,  // gap-4 = 16px
} as const
```

---

## Common Pitfalls

### 1. Missing `w-full`

```tsx
// Wrong - grid width = content width, auto-fill can't calculate columns
<div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fill, 160px)' }}>

// Correct - grid takes container width
<div className="grid w-full" style={{ gridTemplateColumns: 'repeat(auto-fill, 160px)' }}>
```

### 2. Using `auto-fit` Instead of `auto-fill`

| `auto-fill` | `auto-fit` |
|-------------|------------|
| Creates empty tracks if space remains | Collapses empty tracks |
| Preserves grid structure | Stretches items to fill |

For fixed-width cards, use `auto-fill`.

### 3. Using `minmax(160px, 1fr)`

```css
/* Cards stretch to fill available space - often unwanted */
grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));

/* Cards stay at exact width */
grid-template-columns: repeat(auto-fill, 160px);
```

### 4. `grid-flow-col` Forces Single Row

```tsx
// Wrong - forces horizontal layout, overflow instead of wrap
<div className="grid grid-flow-col auto-cols-max gap-4">

// Correct - wraps to multiple rows
<ResponsiveCardGrid cardWidth={96}>
```

---

## Usage Examples

```tsx
// Identity page (160px cards)
<ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.IDENTITY}>
  {identities.map(id => <IdentityCardLink key={id.id} identity={id} />)}
</ResponsiveCardGrid>

// EGO Gift selection (96px cards)
<ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.EGO_GIFT}>
  {gifts.map(gift => <EGOGiftCard key={gift.id} gift={gift} />)}
</ResponsiveCardGrid>

// Custom gap
<ResponsiveCardGrid cardWidth={120} gap={8}>
  {items.map(item => <SmallCard key={item.id} />)}
</ResponsiveCardGrid>
```

---

## Before vs After

| Aspect | Before (Fixed Breakpoints) | After (Auto-Fill) |
|--------|---------------------------|-------------------|
| Column count | Jumps at breakpoints | Fluid based on width |
| Card width | Can overflow cell | Always exact size |
| Grid alignment | Items centered in cells | Entire grid centered |
| Padding | Fixed per breakpoint | Dynamic |
| Maintenance | Update all breakpoints | Single cardWidth prop |

---

## Files Changed

| File | Change |
|------|--------|
| `lib/constants.ts` | Added `CARD_GRID` constants |
| `components/common/ResponsiveCardGrid.tsx` | New component |
| `components/identity/IdentityList.tsx` | Uses ResponsiveCardGrid |
| `components/ego/EGOList.tsx` | Uses ResponsiveCardGrid |
| `components/egoGift/EGOGiftList.tsx` | Uses ResponsiveCardGrid |
| `components/egoGift/EGOGiftSelectionList.tsx` | Uses ResponsiveCardGrid |

---

## Further Reading

- [CSS Grid auto-fill vs auto-fit](https://css-tricks.com/auto-sizing-columns-css-grid-auto-fill-vs-auto-fit/)
- [A Complete Guide to CSS Grid](https://css-tricks.com/snippets/css/complete-guide-grid/)
