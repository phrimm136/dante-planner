---
paths:
  - "frontend/src/components/**/*.tsx"
---

# Tailwind Utility Patterns

## Layout

### Flexbox
```typescript
<div className="flex gap-2 items-center">          // Row with gap
<div className="flex flex-col gap-4">             // Column
<div className="flex justify-between items-center"> // Space between
<div className="flex justify-center items-center">  // Centered
```

### Grid
```typescript
<div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
  {items.map(item => <ItemCard key={item.id} item={item} />)}
</div>
```

## Spacing

```typescript
p-4      // Padding all sides
px-4     // Padding horizontal
py-4     // Padding vertical
pt-4     // Padding top

m-4      // Margin all sides
mx-auto  // Center horizontally
gap-2    // Gap (flex/grid)
```

## Typography

```typescript
<h1 className="text-3xl font-bold">Title</h1>
<h2 className="text-xl font-semibold">Subtitle</h2>
<p className="text-sm text-muted-foreground">Description</p>
```

## Theme Colors

```typescript
// Background
bg-background, bg-card, bg-muted, bg-primary, bg-destructive

// Text
text-foreground, text-muted-foreground, text-primary, text-destructive

// Border
border-border, border-primary, border-destructive

// Ring (focus/selection)
ring-primary, ring-destructive
```

## Responsive (Mobile-First)

```typescript
<div className="p-2 sm:p-4 md:p-6 lg:p-8">  // Breakpoints
<div className="hidden lg:block">             // Desktop only
<div className="lg:hidden">                   // Mobile only
```

| Breakpoint | Min Width |
|-----------|-----------|
| (none) | 0px |
| sm: | 640px |
| md: | 768px |
| **lg:** | **1024px** (primary) |
| xl: | 1280px |
