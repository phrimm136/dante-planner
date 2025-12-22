# Research: Deck Code Import/Export

## Overview of Codebase

- **Framework**: React 18 with TypeScript, Vite build tool, TanStack Router
- **UI Library**: shadcn/ui components built on Radix UI primitives
- **State**: React hooks (useState, useCallback) with startTransition for concurrent features
- **Notifications**: Sonner toast library - use `toast.success()`, `toast.error()` for non-intrusive feedback
- **i18n**: react-i18next with translations in `/static/i18n/{EN,JP,KR,CN}/common.json`
- **Icons**: lucide-react - use `Download`, `Upload`, `Copy` for import/export actions
- **DeckBuilder state**: `equipment` (Record<string, SinnerEquipment>), `deploymentOrder` (number[])
- **SinnerEquipment structure**: Contains `identity` (id, uptie, level) and `egos` (EGOSlots by rank)
- **ID format**: 5-digit string `T SS II` (Type, Sinner index 01-12, Entity index)
- **EGO ranks**: ZAYIN, TETH, HE, WAW, ALEPH stored in EGOSlots Record
- **Button styling**: Use `variant="outline" size="sm"` to match Reset Order button
- **Dialog component**: Available at `/frontend/src/components/ui/dialog.tsx` for confirmation modals
- **Clipboard API**: No existing patterns - use native `navigator.clipboard.writeText/readText`

## Codebase Structure

- **Main component**: `/frontend/src/components/deckBuilder/DeckBuilder.tsx`
- **Button placement**: After Reset Order button (line 305-309) in Formation section flex container
- **Button component**: `/frontend/src/components/ui/button.tsx` with variants/sizes
- **Dialog component**: `/frontend/src/components/ui/dialog.tsx` for confirmation popup
- **Type definitions**: `/frontend/src/types/DeckTypes.ts` (DeckState, SinnerEquipment, EGOSlots)
- **Utilities**: `/frontend/src/lib/utils.ts` contains `cn()` for classnames, `getSinnerFromId()`
- **i18n setup**: `/frontend/src/lib/i18n.ts` - use `useTranslation()` hook
- **Toast setup**: `/frontend/src/main.tsx` line 23 - Toaster positioned top-right
- **Translation files**: Add keys under `deckBuilder.import`, `deckBuilder.export` in common.json

## Gotchas and Pitfalls

- **Clipboard permissions**: Browser requires HTTPS or localhost; wrap in try-catch for SecurityError
- **Bit encoding**: 560 bits total = 46 bits/sinner x 12 sinners; identity ID starts from 1, not 0
- **Base64+Gzip+Base64**: Encoding chain is Binary -> Base64 -> Gzip -> Base64 (double encoding)
- **EGO bit lengths vary**: ZAYIN/TETH/HE/WAW use 7 bits; ALEPH uses 6 bits
- **Deployment order**: 4 bits per sinner (values 0-15); 0 likely means not deployed
- **Translation coverage**: Must add keys to all 4 language files (EN, JP, KR, CN)
- **Toast import**: Use `import { toast } from 'sonner'` not from UI components
- **startTransition**: Wrap state updates in startTransition for non-blocking UI
- **Empty slots**: Handle undefined EGO slots - encode as 0 in binary
- **ID extraction**: Sinner index is middle 2 digits of 5-digit ID (01-12 maps to array index 0-11)
