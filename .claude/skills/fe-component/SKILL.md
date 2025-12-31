---
name: fe-component
description: React component patterns. React Compiler, TypeScript typing, Suspense, shadcn/ui + Tailwind.
---

# Frontend Component Patterns

## Rules

- **No `React.FC`** - Use explicit props interface
- **No manual memoization** - React Compiler handles `memo`, `useMemo`, `useCallback`
- **No early return with `<Loading />`** - Use Suspense boundary
- **No hardcoded colors** - Use `constants.ts` or CSS variables
- **Use `lg:` (1024px)** - Primary mobile/desktop breakpoint

## Forbidden → Use Instead

| Forbidden | Use Instead |
|-----------|-------------|
| `React.FC<Props>` | `function Component(props: Props)` |
| `memo()`, `useCallback()`, `useMemo()` | Plain code (React Compiler) |
| `if (loading) return <Spinner />` | `<Suspense fallback={...}>` |
| `ring-[#fcba03]` | `SECTION_STYLES.highlight.selected` |
| `hidden md:block` | `hidden lg:block` |

## Component Template

```typescript
interface CardProps {
  item: Item
  isSelected?: boolean
  onSelect?: (item: Item) => void
}

export function Card({ item, isSelected = false, onSelect }: CardProps) {
  return (
    <div
      className={cn(
        'p-4 rounded-lg border',
        SECTION_STYLES.highlight.hover,
        isSelected && SECTION_STYLES.highlight.selected
      )}
      onClick={() => onSelect?.(item)}
    >
      {item.name}
    </div>
  )
}
```

## Composition Patterns

### Compound Components (UI primitives)
```typescript
<Tabs defaultValue="skills">
  <TabsList>
    <TabsTrigger value="skills">Skills</TabsTrigger>
  </TabsList>
  <TabsContent value="skills"><SkillList /></TabsContent>
</Tabs>
```

### Children Slot (wrap with behavior)
```typescript
<TierLevelSelector mode="identity" entityId={id} onConfirm={handleEquip}>
  <IdentityCard identity={identity} />
</TierLevelSelector>
```

### Named Slots (layout regions)
```typescript
<DetailPageLayout
  leftColumn={<Header />}
  rightColumn={<SkillPanel />}
/>
```

### Provider + Consumer (global state)
```typescript
<ThemeProvider>
  <App />  {/* useTheme() anywhere inside */}
</ThemeProvider>
```

### SectionContainer (planner pages)
```typescript
<SectionContainer title={t('deckBuilder')} caption={`${count}/12`}>
  <div className={cn(SECTION_STYLES.grid, SECTION_STYLES.SPACING.gap)}>
    {items.map(item => <Card key={item.id} />)}
  </div>
</SectionContainer>
```

## Suspense Pattern

```typescript
export function DetailPage() {
  const { id } = useParams()
  if (!id) return <ErrorState />
  return (
    <Suspense fallback={<LoadingState />}>
      <DetailContent id={id} />
    </Suspense>
  )
}
```

## Responsive Pattern

```typescript
<div className="flex flex-col lg:flex-row gap-4">
<div className="hidden lg:block">  // Desktop only
<div className="lg:hidden">        // Mobile only
```

## Reference

- Pattern: `IdentityCard.tsx`, `EGOGiftCard.tsx`, `SectionContainer.tsx`
- Constants: `@/lib/constants` (`SECTION_STYLES`)
- Why: `docs/learning/frontend-patterns.md`
