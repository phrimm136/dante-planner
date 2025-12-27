# Styling Guide

Styling patterns using Tailwind CSS, shadcn/ui components, and the `cn()` utility for conditional class merging.

---

## Core Tools

| Tool | Purpose |
|------|---------|
| Tailwind CSS | Utility-first CSS framework |
| shadcn/ui | Pre-built accessible components |
| `cn()` utility | Conditional class merging (clsx + tailwind-merge) |
| CSS Variables | Theming and color management |

---

## The cn() Utility

The `cn()` function combines `clsx` and `tailwind-merge` for conditional class handling:

```typescript
// @/lib/utils.ts
import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
```

### Basic Usage

```typescript
import { cn } from '@/lib/utils'

// Conditional classes
<div className={cn(
  'p-4 rounded-lg',
  isSelected && 'ring-2 ring-primary',
  isDisabled && 'opacity-50 pointer-events-none'
)}>
  Content
</div>

// Merge with props
interface CardProps {
  className?: string
  children: React.ReactNode
}

function Card({ className, children }: CardProps) {
  return (
    <div className={cn('rounded-lg border bg-card p-4', className)}>
      {children}
    </div>
  )
}
```

### Why cn() Instead of Template Literals

```typescript
// ❌ AVOID - Duplicates don't merge properly
<div className={`p-4 ${isLarge ? 'p-8' : ''}`}>
  // Results in "p-4 p-8" - conflicting classes!

// ✅ CORRECT - tailwind-merge handles conflicts
<div className={cn('p-4', isLarge && 'p-8')}>
  // Results in "p-8" when isLarge is true
```

---

## Tailwind CSS Patterns

### Spacing

```typescript
// Padding
p-4        // All sides (1rem = 16px)
px-4       // Horizontal (left + right)
py-4       // Vertical (top + bottom)
pt-4       // Top only
pr-4 pb-4  // Right, bottom

// Margin
m-4 mx-4 my-4 mt-4 mr-4 mb-4 ml-4

// Gap (for flex/grid)
gap-4      // All directions
gap-x-4    // Horizontal gap
gap-y-4    // Vertical gap

// Spacing scale: 1 = 0.25rem = 4px
// p-1 = 4px, p-2 = 8px, p-4 = 16px, p-8 = 32px
```

### Flexbox

```typescript
// Row layout (default)
<div className="flex items-center gap-4">

// Column layout
<div className="flex flex-col gap-2">

// Space between
<div className="flex items-center justify-between">

// Centered content
<div className="flex items-center justify-center h-full">

// Wrap items
<div className="flex flex-wrap gap-4">
```

### Grid

```typescript
// Responsive columns
<div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
  {items.map(item => <Card key={item.id} />)}
</div>

// Fixed columns
<div className="grid grid-cols-3 gap-4">

// Spanning columns
<div className="col-span-2">  // Takes 2 columns
```

### Responsive Design

```typescript
// Mobile-first breakpoints
// sm: 640px, md: 768px, lg: 1024px, xl: 1280px, 2xl: 1536px

<div className={cn(
  'p-2',      // Mobile default
  'sm:p-4',   // ≥640px
  'md:p-6',   // ≥768px
  'lg:p-8'    // ≥1024px
)}>

// Responsive grid
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">

// Hide/show at breakpoints
<div className="hidden md:block">    // Hidden on mobile, visible on md+
<div className="md:hidden">          // Visible on mobile, hidden on md+
```

### Typography

```typescript
// Text sizes
text-xs text-sm text-base text-lg text-xl text-2xl

// Font weight
font-normal font-medium font-semibold font-bold

// Text color using theme colors
text-foreground           // Primary text
text-muted-foreground     // Secondary/muted text
text-primary              // Brand color
text-destructive          // Error/danger

// Line clamp (truncation)
<p className="line-clamp-2">  // Limit to 2 lines with ellipsis
```

### Interactive States

```typescript
// Hover effects
<button className="hover:bg-primary/90">

// Focus states (accessibility)
<input className="focus:outline-none focus:ring-2 focus:ring-primary">

// Active/pressed state
<button className="active:scale-95">

// Disabled state
<button className="disabled:opacity-50 disabled:pointer-events-none">

// Transitions
<div className="transition-colors duration-200">
<div className="transition-all">
```

---

## Theme Colors (CSS Variables)

shadcn/ui uses CSS variables for theming defined in `globals.css`:

```typescript
// Semantic color usage
bg-background        // Page background
bg-card              // Card/container background
bg-primary           // Primary action background
bg-secondary         // Secondary action background
bg-muted             // Muted/subtle background
bg-destructive       // Error/danger background

text-foreground      // Primary text
text-muted-foreground // Secondary text
text-primary         // Primary brand color
text-destructive     // Error text

border-border        // Default border color
border-input         // Input border
border-primary       // Primary color border

ring-ring            // Focus ring color
ring-primary         // Primary focus ring
```

### Color with Opacity

```typescript
// Add opacity to any color
bg-primary/50        // 50% opacity
bg-black/20          // 20% black overlay
text-foreground/80   // 80% text opacity
```

---

## shadcn/ui Components

### Installation

```bash
yarn run shadcn@latest add button card dialog input label
```

### Common Components

```typescript
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
```

### Button Variants

```typescript
<Button>Default</Button>
<Button variant="secondary">Secondary</Button>
<Button variant="outline">Outline</Button>
<Button variant="ghost">Ghost</Button>
<Button variant="destructive">Destructive</Button>
<Button variant="link">Link</Button>

// Sizes
<Button size="sm">Small</Button>
<Button size="default">Default</Button>
<Button size="lg">Large</Button>
<Button size="icon"><Icon /></Button>
```

### Card Pattern

```typescript
<Card>
  <CardHeader>
    <CardTitle>Title</CardTitle>
    <CardDescription>Description text</CardDescription>
  </CardHeader>
  <CardContent>
    Main content here
  </CardContent>
  <CardFooter>
    <Button>Action</Button>
  </CardFooter>
</Card>
```

### Form Elements

```typescript
<div className="space-y-2">
  <Label htmlFor="email">Email</Label>
  <Input id="email" type="email" placeholder="Enter email" />
  {error && (
    <p className="text-xs text-destructive">{error}</p>
  )}
</div>
```

---

## Common Patterns

### Card Grid

```typescript
<div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
  {items.map((item) => (
    <Card key={item.id} className="hover:shadow-md transition-shadow">
      <CardContent className="pt-4">
        <p className="font-medium">{item.title}</p>
        <p className="text-sm text-muted-foreground">{item.description}</p>
      </CardContent>
    </Card>
  ))}
</div>
```

### Selection Highlight

```typescript
// Standard border highlight (no color/opacity shift)
<div
  className={cn(
    'p-4 rounded-lg cursor-pointer transition-all',
    'hover:border-2 hover:border-[#fcba03]',
    isSelected && 'border-2 border-[#fcba03]'
  )}
  onClick={handleSelect}
>
  {content}
</div>

// With border image (if available)
<div
  className={cn(
    'p-4 rounded-lg cursor-pointer transition-all',
    isSelected && 'border-2 border-[#fcba03]',
    isSelected && hasHighlightImage && 'border-image-source-[url(/path/to/highlight-border.png)]'
  )}
  onClick={handleSelect}
>
  {content}
</div>

// Alternative: Using outline instead of border (doesn't affect layout)
<div
  className={cn(
    'p-4 rounded-lg cursor-pointer transition-all',
    'hover:outline-2 hover:outline-[#fcba03]',
    isSelected && 'outline-2 outline-[#fcba03]'
  )}
  onClick={handleSelect}
>
  {content}
</div>
```

### Icon with Text

```typescript
import { CheckCircle } from 'lucide-react'

<div className="flex items-center gap-2">
  <CheckCircle className="h-4 w-4 text-green-500" />
  <span>Completed</span>
</div>
```

### Loading Placeholder

```typescript
// Skeleton loading
<div className="space-y-2">
  <div className="h-4 w-3/4 bg-muted animate-pulse rounded" />
  <div className="h-4 w-1/2 bg-muted animate-pulse rounded" />
</div>

// Using shadcn Skeleton
import { Skeleton } from '@/components/ui/skeleton'

<Skeleton className="h-4 w-[200px]" />
```

### Overlay/Backdrop

```typescript
// Semi-transparent overlay
<div className="fixed inset-0 bg-black/50 z-50">
  <div className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2">
    {/* Modal content */}
  </div>
</div>
```

---

## Best Practices

### Do's

```typescript
// ✅ Use semantic color variables
<p className="text-muted-foreground">

// ✅ Use cn() for conditional classes
<div className={cn('p-4', isActive && 'bg-primary')}>

// ✅ Accept className prop for customization
function MyComponent({ className }: { className?: string }) {
  return <div className={cn('default-styles', className)} />
}

// ✅ Use gap instead of margins for spacing in flex/grid
<div className="flex gap-4">

// ✅ Mobile-first responsive design
<div className="text-sm md:text-base lg:text-lg">
```

### Don'ts

```typescript
// ❌ Don't use arbitrary values when Tailwind has equivalents
<div className="p-[16px]">   // Use p-4 instead
<div className="mt-[32px]">  // Use mt-8 instead

// ❌ Don't hardcode colors
<div className="bg-[#1a1a1a]">   // Use bg-background or CSS variable
<div className="text-[#666]">    // Use text-muted-foreground

// ❌ Don't mix margin approaches
<div className="flex">
  <div className="mr-4">  // Use gap-4 on parent instead
  <div className="mr-4">

// ❌ Don't use inline styles
<div style={{ padding: '16px' }}>  // Use Tailwind classes
```

---

## Dark Mode

shadcn/ui supports dark mode via CSS variables:

```typescript
// Colors automatically adjust based on theme
<div className="bg-background text-foreground">
  // Light: white background, black text
  // Dark: black background, white text

// Force specific mode on element
<div className="dark">
  {/* Always dark mode inside */}
</div>
```

---

## Summary

| Aspect | Approach |
|--------|----------|
| Utility classes | Tailwind CSS |
| Class merging | `cn()` utility |
| Components | shadcn/ui |
| Theming | CSS variables |
| Responsive | Mobile-first breakpoints |
| Colors | Semantic theme colors |
| Conditionals | cn() with boolean expressions |

**See Also:**
- [component-patterns.md](component-patterns.md) - Component structure
- [complete-examples.md](complete-examples.md) - Full styling examples
