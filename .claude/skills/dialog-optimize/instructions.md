# Dialog Performance Optimization Pattern

Apply this pattern to Dialog components (especially EditPanes) that need instant reopen.

## ⚠️ CRITICAL: forceMount={contentReady}

**NEVER use static `forceMount`** - it causes ALL dialogs to mount on page load!

### Large Panes (with contentReady state)
```tsx
// Use forceMount={contentReady}
<DialogContent forceMount={contentReady} onPointerDownOutside={(e) => e.preventDefault()}>
```

**Result:**
- Page load: contentReady=false → Dialog doesn't mount (lazy) ✓
- First open: contentReady=true after delay
- Close: forceMount=true → DOM preserved ✓
- Reopen: Instant ✓

### Small Panes (without contentReady)
```tsx
// Remove forceMount entirely
<DialogContent onPointerDownOutside={(e) => e.preventDefault()}>
```

**Why:** Small panes mount fast enough that preservation isn't needed.

## Pattern

```tsx
// 1. Add custom backdrop
{open && (
  <div
    className="fixed inset-0 z-50 bg-black/50 animate-in fade-in-0"
    onClick={() => onOpenChange(false)}
  />
)}

// 2. Apply modal={false} + conditional forceMount
<Dialog open={open} onOpenChange={onOpenChange} modal={false}>
  <DialogContent
    forceMount={contentReady}  // CRITICAL: conditional, not static!
    onPointerDownOutside={(e) => e.preventDefault()}
  >
    {/* content */}
  </DialogContent>
</Dialog>
```

## When to Apply

- Edit panes that reopen frequently
- Dialogs with expensive render (lists, complex forms)
- User reports lag on second open

## Steps

1. **Check Dialog already has forceMount support** in `components/ui/dialog.tsx`
2. **Wrap Dialog in Fragment** (`<>...</>`)
3. **Add custom backdrop** before Dialog (only renders when open)
4. **Add modal={false}** to Dialog
5. **CRITICAL: Add conditional forceMount** to DialogContent:
   - If pane has `contentReady` state: `forceMount={contentReady}`
   - If pane is small/simple: Remove `forceMount` entirely
6. **Add onPointerDownOutside={(e) => e.preventDefault()}** to DialogContent
7. **Keep contentReady persistence** (use `if (open && !contentReady)` pattern, NOT `if (open)`)

## Selection Handler Optimization

If Dialog has selection:

```tsx
// Add startTransition import
import { startTransition } from 'react'

// Wrap handler
const handleSelect = useCallback((id: string) => {
  startTransition(() => {
    onSelectionChange((prev) => {
      // ... functional update
    })
  })
}, [onSelectionChange])
```

## List Optimization (if applicable)

For card lists in Dialog:

```tsx
// Wrapper with custom comparison
const CardWrapper = memo(function CardWrapper({
  id,
  isVisible,
  isSelected,
  onSelect,
  children
}: Props) {
  return (
    <div className={isVisible ? '' : 'hidden'}>
      {children}
    </div>
  )
}, function arePropsEqual(prev, next) {
  return (
    prev.id === next.id &&
    prev.isVisible === next.isVisible &&
    prev.isSelected === next.isSelected &&
    prev.onSelect === next.onSelect
    // children excluded, isSelectable excluded to prevent mass re-renders
  )
})
```

## Reference Implementation

`src/components/egoGift/EGOGiftObservationEditPane.tsx`
