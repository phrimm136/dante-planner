---
paths:
  - "frontend/src/components/**/*.tsx"
  - "frontend/src/components/**/*.ts"
---

# Component Composition Patterns

## Compound Components (shadcn/ui primitives)

```typescript
<Tabs defaultValue="skills">
  <TabsList>
    <TabsTrigger value="skills">Skills</TabsTrigger>
  </TabsList>
  <TabsContent value="skills"><SkillList /></TabsContent>
</Tabs>
```

## Children Slot (wrap with behavior)

```typescript
<TierLevelSelector mode="identity" entityId={id} onConfirm={handleEquip}>
  <IdentityCard identity={identity} />
</TierLevelSelector>
```

## Named Slots (layout regions)

```typescript
<DetailPageLayout
  leftColumn={<Header />}
  rightColumn={<SkillPanel />}
/>
```

## SectionContainer (planner pages)

```typescript
<SectionContainer title={t('deckBuilder')} caption={`${count}/12`}>
  <div className={cn(SECTION_STYLES.grid, SECTION_STYLES.SPACING.gap)}>
    {items.map(item => <Card key={item.id} />)}
  </div>
</SectionContainer>
```

**Reference:** `SectionContainer.tsx`
