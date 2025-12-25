# Styling Guide

Tailwind CSS + shadcn/ui styling patterns for LimbusPlanner.

---

## Stack Overview

| Tool | Purpose |
|------|---------|
| Tailwind CSS | Utility-first CSS framework |
| shadcn/ui | Pre-built accessible components |
| `cn()` | Conditional class merging utility |

---

## cn() Utility

The `cn()` utility combines `clsx` and `tailwind-merge` for conditional classes:

```typescript
import { cn } from '@/lib/utils'

// Basic usage
<div className={cn('p-4', 'bg-white')} />

// Conditional classes
<Card className={cn(
  'p-4 transition-all',
  isSelected && 'ring-2 ring-primary',
  isDisabled && 'opacity-50 pointer-events-none'
)} />

// With dynamic values
<div className={cn(
  'flex gap-2',
  direction === 'vertical' ? 'flex-col' : 'flex-row'
)} />
```

---

## shadcn/ui Components

### Installation

```bash
npx shadcn@latest add button card dialog
```

### Usage

```typescript
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog'

<Card>
  <CardHeader>
    <CardTitle>Title</CardTitle>
  </CardHeader>
  <CardContent>
    <p>Content here</p>
  </CardContent>
</Card>

<Button variant="outline" size="sm">
  Click me
</Button>
```

### Common Components

| Component | Use Case |
|-----------|----------|
| `Button` | Actions, links |
| `Card` | Content containers |
| `Dialog` | Modals, popups |
| `Input` | Form inputs |
| `Select` | Dropdowns |
| `Tabs` | Tab navigation |
| `Tooltip` | Hover hints |

---

## Tailwind Patterns

### Flexbox Layout

```typescript
// Row with gap
<div className="flex gap-2 items-center">

// Column layout
<div className="flex flex-col gap-4">

// Space between
<div className="flex justify-between items-center">

// Centered
<div className="flex justify-center items-center">
```

### Grid Layout

```typescript
// Responsive grid
<div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
  {items.map(item => <ItemCard key={item.id} item={item} />)}
</div>

// Fixed columns
<div className="grid grid-cols-3 gap-2">
```

### Spacing

```typescript
// Padding
p-4      // all sides
px-4     // horizontal
py-4     // vertical
pt-4     // top only

// Margin
m-4      // all sides
mx-auto  // center horizontally
my-4     // vertical
mt-4     // top only

// Gap (in flex/grid)
gap-2    // all directions
gap-x-4  // horizontal
gap-y-2  // vertical
```

### Typography

```typescript
// Text sizes
<h1 className="text-3xl font-bold">Title</h1>
<h2 className="text-xl font-semibold">Subtitle</h2>
<p className="text-sm text-muted-foreground">Description</p>

// Font weights
font-bold      // 700
font-semibold  // 600
font-medium    // 500

// Colors
text-primary           // Primary color
text-muted-foreground  // Muted text
text-destructive       // Error/destructive
```

### Responsive Design

```typescript
// Mobile-first breakpoints
<div className="
  p-2 sm:p-4 md:p-6 lg:p-8
  text-sm md:text-base
  grid-cols-1 sm:grid-cols-2 lg:grid-cols-4
">
```

| Prefix | Min Width |
|--------|-----------|
| (none) | 0px (mobile) |
| `sm:` | 640px |
| `md:` | 768px |
| `lg:` | 1024px |
| `xl:` | 1280px |

---

## Theme Colors

Use semantic color classes from the theme:

```typescript
// Background
bg-background      // Main background
bg-card            // Card background
bg-muted           // Muted/secondary background
bg-primary         // Primary color
bg-destructive     // Error state

// Text
text-foreground           // Main text
text-muted-foreground     // Muted text
text-primary              // Primary color
text-primary-foreground   // Text on primary bg
text-destructive          // Error text

// Border
border-border     // Default border
border-primary    // Primary border
border-destructive

// Ring (focus/selection)
ring-primary      // Primary ring
ring-destructive  // Error ring
```

---

## Common Patterns

### Card with Hover

```typescript
<Card className={cn(
  'cursor-pointer transition-all',
  'hover:shadow-md hover:scale-[1.02]',
  isSelected && 'ring-2 ring-primary'
)}>
  <CardContent className="p-4">
    {/* content */}
  </CardContent>
</Card>
```

### Container Layout

```typescript
<div className="container mx-auto p-8">
  <h1 className="text-3xl font-bold mb-4">Page Title</h1>
  {/* content */}
</div>
```

### Error State Box

```typescript
<div className="bg-destructive/10 border border-destructive rounded-lg p-6 text-center">
  <h2 className="text-xl font-bold text-destructive mb-2">Error Title</h2>
  <p className="text-muted-foreground">Error message</p>
</div>
```

### Loading State

```typescript
<div className="flex items-center justify-center p-8">
  <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
</div>
```

### Button Group

```typescript
<div className="flex gap-2">
  <Button variant="default">Primary</Button>
  <Button variant="outline">Secondary</Button>
  <Button variant="ghost">Tertiary</Button>
</div>
```

---

## Best Practices

### DO

```typescript
// ✅ Use Tailwind utilities
<div className="flex gap-4 p-4">

// ✅ Use cn() for conditional classes
<Card className={cn('p-4', isActive && 'ring-2 ring-primary')}>

// ✅ Use semantic colors
<p className="text-muted-foreground">

// ✅ Use shadcn/ui components
<Button variant="outline">Click</Button>
```

### DON'T

```typescript
// ❌ Inline styles
<div style={{ padding: '16px' }}>

// ❌ Hardcoded colors
<div className="text-[#666666]">

// ❌ Custom CSS when Tailwind exists
<div className="my-custom-flex-class">

// ❌ String concatenation for classes
<div className={'p-4 ' + (isActive ? 'ring-2' : '')}>
```

---

## Code Style

### 2-Space Indentation

```typescript
<Card>
  <CardContent>
    <p>Content</p>
  </CardContent>
</Card>
```

### Single Quotes

```typescript
import { cn } from '@/lib/utils'
const color = 'primary'
```

### Trailing Commas

```typescript
const items = [
  'item1',
  'item2',
]
```

---

## Summary

| Pattern | Example |
|---------|---------|
| Conditional classes | `cn('base', condition && 'active')` |
| Spacing | `p-4`, `gap-2`, `m-auto` |
| Responsive | `sm:`, `md:`, `lg:` prefixes |
| Colors | `text-primary`, `bg-muted` |
| Components | shadcn/ui `Button`, `Card`, etc. |

**See Also:**
- [component-patterns.md](component-patterns.md) - Component structure
- [file-organization.md](file-organization.md) - Directory structure
