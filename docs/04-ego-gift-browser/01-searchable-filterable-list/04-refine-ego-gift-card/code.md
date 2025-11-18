# Code: Refine EGO Gift Card

## Implementation Overview

Refactored EGOGiftCard component from simple tier badge layout to rich visual card with four icon layers using absolute positioning. Extended EGOGift type interface with enhancement field and created three asset path helper functions following existing conventions. Final card displays 128x128px gift icon at top with name below, grade icon in upper-left corner, enhancement icon conditionally in upper-right corner, and keyword icons in lower-right corner.

## Files Modified

### frontend/src/types/EGOGiftTypes.ts

Added enhancement field to EGOGift interface for tracking gift enhancement level.

```typescript
export interface EGOGift {
  id: string
  name: string
  keywords: string[]
  themePack: string[]
  cost: number
  tier: string
  enhancement: number  // Added for enhancement level tracking
}
```

**Rationale**: Enhancement level needed as type field to support conditional rendering of enhancement icon in card. Defaults to 0 in list view but can be populated by other components for detail views or future features.

### frontend/src/hooks/useEGOGiftData.ts

Updated gift object construction to include enhancement field defaulting to 0.

```typescript
return Object.entries(egoGiftSpecList).map(([id, spec]) => ({
  id,
  name: egoGiftNames[id] || id,
  keywords: spec.keywords,
  themePack: spec.themePack,
  cost: spec.cost,
  tier: spec.tier,
  enhancement: 0, // Default to 0 for list view
}))
```

**Rationale**: Provides consistent enhancement value for all gifts in list view. Other components can override this value when displaying enhanced gifts in different contexts.

### frontend/src/lib/assetPaths.ts

Added three helper functions for EGO Gift icon paths following existing naming and documentation conventions.

```typescript
/**
 * Gets EGO Gift icon path (128x128px gift image)
 * @param giftId - Gift ID
 * @returns Gift icon path
 */
export function getEGOGiftIconPath(giftId: string): string {
  return `/images/egoGift/${giftId}.webp`
}

/**
 * Gets EGO Gift grade icon path
 * @param tier - Gift tier (e.g., "1", "2", "3", "EX")
 * @returns Grade icon path
 */
export function getEGOGiftGradeIconPath(tier: string): string {
  return `/images/icon/egoGift/grade${tier}.webp`
}

/**
 * Gets EGO Gift enhancement icon path
 * @param level - Enhancement level (0, 1, 2, etc.)
 * @returns Enhancement icon path
 */
export function getEGOGiftEnhancementIconPath(level: number): string {
  return `/images/icon/egoGift/enhancement${level}.webp`
}
```

**Rationale**: Centralizes path logic matching existing pattern for other asset types. Tier parameter as string accommodates special values like "EX". Enhancement level as number supports numeric comparison for conditional rendering.

### frontend/src/components/egoGift/EGOGiftCard.tsx

Complete refactor from tier badge layout to icon-layered vertical layout.

**Removed**:
- Tier badge in upper-left
- Cost display under name
- Large font size for name (text-lg)
- Padding p-4

**Added**:
- Import for getEGOGiftIconPath, getEGOGiftGradeIconPath, getEGOGiftEnhancementIconPath
- Grade icon in upper-left using absolute positioning with -top-2 -left-2
- Enhancement icon in upper-right using conditional rendering when enhancement > 0
- Gift icon 128x128px in center using flex container
- Vertical flex layout with items-center and gap-2
- Name below gift icon with text-sm font size
- pointer-events-none on all corner icons
- Reduced padding to p-3

**Implementation**:

```typescript
import { Link } from '@tanstack/react-router'
import {
  getStatusEffectIconPath,
  getEGOGiftIconPath,
  getEGOGiftGradeIconPath,
  getEGOGiftEnhancementIconPath,
} from '@/lib/assetPaths'
import { getKeywordDisplayName } from '@/lib/utils'
import type { EGOGift } from '@/types/EGOGiftTypes'

interface EGOGiftCardProps {
  gift: EGOGift
}

export function EGOGiftCard({ gift }: EGOGiftCardProps) {
  const { id } = gift

  return (
    <Link
      to="/ego-gift/$id"
      params={{ id }}
      className="block relative border rounded-lg p-3 hover:shadow-md transition-shadow bg-white"
    >
      {/* Grade Icon - Upper-left */}
      <img
        src={getEGOGiftGradeIconPath(gift.tier)}
        alt={`Grade ${gift.tier}`}
        className="absolute -top-2 -left-2 w-10 h-10 pointer-events-none"
      />

      {/* Enhancement Icon - Upper-right (only if enhanced) */}
      {gift.enhancement > 0 && (
        <img
          src={getEGOGiftEnhancementIconPath(gift.enhancement)}
          alt={`Enhancement +${gift.enhancement}`}
          className="absolute -top-2 -right-2 w-10 h-10 pointer-events-none"
        />
      )}

      {/* Main content - vertical layout */}
      <div className="flex flex-col items-center gap-2">
        {/* Gift Icon - 128x128px */}
        <div className="w-32 h-32 flex items-center justify-center">
          <img
            src={getEGOGiftIconPath(id)}
            alt={gift.name}
            className="w-full h-full object-contain"
          />
        </div>

        {/* Name below icon */}
        <h3 className="font-semibold text-sm text-center line-clamp-2 w-full">
          {gift.name}
        </h3>
      </div>

      {/* Keywords - Icon only in lower-right corner */}
      {gift.keywords.length > 0 && (
        <div className="absolute bottom-2 right-2 flex gap-1">
          {gift.keywords.map((keyword, index) => {
            const displayName = getKeywordDisplayName(keyword)
            const iconPath = getStatusEffectIconPath(keyword)

            return (
              <img
                key={index}
                src={iconPath}
                alt={displayName}
                title={displayName}
                className="w-6 h-6 pointer-events-none"
                onError={(e) => {
                  // Text fallback for missing icons
                  const target = e.currentTarget
                  const span = document.createElement('span')
                  span.textContent = displayName
                  span.className = 'px-1 py-0.5 text-xs bg-blue-100 text-blue-800 rounded'
                  span.title = displayName
                  target.replaceWith(span)
                }}
              />
            )
          })}
        </div>
      )}
    </Link>
  )
}
```

**Key Design Decisions**:

1. **Vertical Layout**: Changed from horizontal tier+name to vertical icon+name for better visual hierarchy focusing on gift image
2. **Absolute Positioning for Corners**: All four corners use absolute positioning with negative offsets creating overflow effect matching game UI aesthetic
3. **pointer-events-none**: Applied to all corner icons preventing click interference with Link wrapper ensuring entire card remains clickable
4. **Conditional Enhancement**: Enhancement icon renders only when enhancement > 0 hiding unnecessary decoration for base-level gifts
5. **Container for Gift Icon**: 128x128px flex container ensures consistent dimensions even when image fails to load
6. **Reduced Text Size**: Changed name from text-lg to text-sm preventing excessive card height variation
7. **Tighter Padding**: Reduced from p-4 to p-3 creating more compact cards for grid layout

## Technical Notes

### Icon Positioning Strategy

Corner icons use absolute positioning with negative offsets:
- **Upper-left** (grade): `-top-2 -left-2` creates top-left corner overflow
- **Upper-right** (enhancement): `-top-2 -right-2` creates top-right corner overflow
- **Lower-right** (keywords): `bottom-2 right-2` stays within card bounds

### Image Fallback Handling

Gift icon container maintains 128x128px dimensions through explicit sizing on wrapper div. Browser handles missing images with default broken image icon maintaining layout. Keyword icons have onError handler creating text fallback.

### Enhancement Conditional Logic

Enhancement icon renders conditionally using `{gift.enhancement > 0 && ...}` pattern. List view defaults to enhancement 0 hiding icon. Future components can populate enhancement field for detail views or enhanced gift displays.

### TypeScript Type Safety

Enhancement field addition to EGOGift interface provides compile-time safety. All components using EGOGift type now required to provide enhancement value preventing runtime undefined errors.

## Build Verification

Build completed successfully with no TypeScript errors:

```
✓ 2029 modules transformed.
✓ built in 8.86s
```

Warnings present are informational:
- Route file warnings expected for TanStack Router setup
- Dynamic import warnings about code splitting (non-blocking)
- Chunk size optimization suggestions (non-blocking)

All icon paths resolve correctly despite missing image files. Browser will handle missing images with default broken image display.

## Testing Notes

**Visual Verification Needed**:
1. Card displays 128x128px gift icon centered
2. Grade icon appears in upper-left corner overlapping card border
3. Enhancement icon hidden for gifts with enhancement 0
4. Name displays below icon with proper truncation
5. Keyword icons functional in lower-right corner
6. Entire card clickable navigating to detail page
7. Hover shadow effect works correctly

**Future Considerations**:
- Image files need to be added to /static/images/egoGift/ and /static/images/icon/egoGift/
- Enhancement field population needed in detail view or other components
- Consider loading states for gift images if image loading is slow
