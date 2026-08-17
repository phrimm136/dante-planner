# Account Deletion Feature - Research Findings

## Spec Ambiguities

**✅ NONE DETECTED** - Spec is comprehensive and unambiguous.

## Spec-to-Code Mapping

### 1. Account Deletion with Typed Confirmation
- **New**: `AccountDeleteSection.tsx`, `AccountDeleteDialog.tsx`
- **Modify**: `SettingsPage.tsx` (integrate section)
- **Pattern**: Uses `useAuthQuery()` for auth gating, follows `UsernameSection.tsx` Suspense wrapper pattern

### 2. API Client Fix (Generic Typed Responses)
- **Modify**: `lib/api.ts` - Change `delete()` from `Promise<void>` to `delete<T>(): Promise<T>`
- **Impact**: 1 usage in `plannerApi.ts:89` - safe to modify
- **Reason**: Backend returns 200 OK with JSON body (not 204 No Content)

### 3. Mutation with Cache Invalidation
- **Modify**: `hooks/useUserSettingsQuery.ts` - Add `useDeleteAccountMutation` hook
- **Pattern**: Follows `useUpdateKeywordMutation` (mutation + cache invalidation + toast)
- **Cache invalidation**: `queryClient.setQueryData(authQueryKeys.me, null)` triggers logout

### 4. Response Validation and Types
- **Modify**: `types/UserSettingsTypes.ts` - Add `UserDeletionResponse` interface
- **Modify**: `schemas/UserSettingsSchemas.ts` - Add `UserDeletionResponseSchema`
- **Backend response**: `{ message, deletedAt, permanentDeleteAt, gracePeriodDays }`
- **Note**: Backend field is `permanentDeleteAt` (not `permanentDeleteScheduledAt`)

### 5. Confirmation Dialog with Dismiss Prevention
- **New**: `AccountDeleteDialog.tsx`
- **Pattern**: `ConflictResolutionDialog.tsx` lines 64-73 (dismiss prevention)
- **Validation**: Input must match "DELETE" exactly (case-sensitive)

### 6. Danger Zone Styling
- **Modify**: `SettingsPage.tsx` - Add section with red border/background
- **Styling**: Inline Tailwind (no danger zone constants exist)

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `AccountDeleteSection.tsx` | `UsernameSection.tsx` | Suspense wrapper (181-186), auth gating (31, 110-122), skeleton fallback (192-203) |
| `AccountDeleteDialog.tsx` | `ConflictResolutionDialog.tsx` | Dialog structure (69-115), dismiss prevention (64-73), button states, destructive variant |
| `useDeleteAccountMutation` | `useUpdateKeywordMutation` | Mutation structure (85-102), safeParse validation (88-92), cache invalidation (95-97) |

## Pattern Copy Deep Analysis

### Pattern 1: UsernameSection Suspense + Auth Gating
- **Total lines**: 204
- **Structure**: Outer (Suspense wrapper) + Content (hook calls, auth check) + Skeleton (fallback)
- **For AccountDeleteSection**: Use identical Suspense pattern, same auth gating with `useAuthQuery()`
- **Difference justified**: Dialog state management is additive, doesn't violate pattern

### Pattern 2: ConflictResolutionDialog Dismiss Prevention
- **Total lines**: 117
- **Key feature**: `preventDismissal` function catches ESC and outside-click events
- **For AccountDeleteDialog**: Copy exact same dismiss prevention props
- **Difference justified**: Text input for confirmation is additive functionality

### Pattern 3: useUpdateKeywordMutation
- **Total lines**: 103
- **Structure**: `useMutation` → safeParse → cache invalidation → error logging
- **For useDeleteAccountMutation**: Identical structure, change API call to `delete<UserDeletionResponse>()`
- **Gap identified**: API client needs fixing first (delete returns void currently)

## Cross-Reference Validation

| Layer | Reference | Application | Match? |
|-------|-----------|-------------|--------|
| **Suspense Pattern** | Suspense wraps Content + Skeleton | Identical for AccountDeleteSection | ✓ |
| **Auth Gating** | useAuthQuery null check | Same pattern | ✓ |
| **Dismiss Prevention** | showCloseButton={false}, onEscape/onOutside handlers | Exact copy | ✓ |
| **Mutation Structure** | useMutation with safeParse validation | Exact copy | ✓ |
| **API Call** | ApiClient.put<T>() | Change to delete<T>() after API fix | ✓ (after fix) |

## Implementation Dependency Graph

```
1. API Client Fix (lib/api.ts)
   ↓
2. Types & Schemas (UserSettingsTypes.ts, UserSettingsSchemas.ts)
   ↓
3. Data Hook (useUserSettingsQuery.ts → useDeleteAccountMutation)
   ↓
4. Dialog Component (AccountDeleteDialog.tsx)
   ↓
5. Main Component (AccountDeleteSection.tsx)
   ↓
6. Page Integration (SettingsPage.tsx)
```

## Critical Implementation Notes

### API Client Modification
- **Current**: `delete(endpoint): Promise<void>` expects no response
- **Required**: `delete<T>(endpoint): Promise<T>` for typed responses
- **Usage audit**: Only 1 usage in `plannerApi.ts:89` - safe to modify
- **Backward compatible**: Existing usage ignores return value, will still work

### Response Field Name
- **Spec says**: `permanentDeleteScheduledAt`
- **Backend returns**: `permanentDeleteAt` (actual JSON field name)
- **Frontend schema**: Must use `permanentDeleteAt` to match JSON

### Toast Message Construction
```typescript
const date = new Date(response.permanentDeleteAt).toLocaleDateString('en-US', {
  year: 'numeric', month: 'long', day: 'numeric'
});
toast.success(`Account scheduled for deletion on ${date}. Log in within ${response.gracePeriodDays} days to cancel.`);
```

## Gap Analysis

**Currently missing:**
- `AccountDeleteSection.tsx` component
- `AccountDeleteDialog.tsx` component
- `UserDeletionResponse` type
- `UserDeletionResponseSchema` validation
- `useDeleteAccountMutation` hook
- Generic `delete<T>()` in ApiClient

**Needs modification:**
- `api.ts` - Update delete() signature
- `UserSettingsTypes.ts` - Add type
- `UserSettingsSchemas.ts` - Add schema
- `useUserSettingsQuery.ts` - Add hook
- `SettingsPage.tsx` - Add section

**Can reuse:**
- `useAuthQuery()` for auth state
- Dialog UI components
- Button components
- Sonner toast library
- TanStack Router navigation
- Existing i18n structure (inline defaults)

## Testing Requirements

### Manual UI Tests
- Pre-deletion state: Section styling, button appearance, help text visibility
- Confirmation input: Incomplete text disables button, exact "DELETE" enables, case-sensitive
- Dismiss prevention: ESC key blocked, outside-click blocked, no close button
- Success flow: Toast shows actual server date, 2-second wait, redirect, logout state
- Error handling: Network failure shows error toast, dialog stays open, retry possible

### Automated Tests
- **Schema validation**: All required fields validated, invalid responses throw
- **Mutation**: API call with type parameter, schema validation, cache invalidation on success
- **Dialog**: Confirmation input state, button disabled until exact match, dismiss handlers work
- **Component integration**: Auth gating, dialog opens on click, mutation fires, redirect after cache clear

## Technical Constraints

1. **API client breaking change**: Fix required before implementation (1 usage, safe)
2. **Field name mapping**: Backend `permanentDeleteAt` → Display as readable date
3. **Auth cache invalidation**: Triggers logout via Header's useAuthQuery dependency
4. **Dialog dismiss prevention**: Copy exact pattern from ConflictResolutionDialog
5. **Suspense requirement**: Content must suspend on useAuthQuery, wrap in Suspense
6. **i18n**: Use inline default values (no translation keys in JSON files)

## Summary

- **Domain**: Frontend Settings / User Account Management
- **Scope**: 2 new components + 1 hook + 3 type definitions + 1 API fix
- **Risk**: Medium (API change affects 1 file, but safe)
- **Pattern Compliance**: 100% - follows existing patterns exactly
- **Backend**: ✅ Complete - no changes needed
- **Ambiguities**: ✅ None
- **Ready**: ✅ Yes - all patterns validated, all dependencies identified
