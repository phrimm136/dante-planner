# Research: Settings Page - Username Keyword Change

## Clarifications Resolved

- **Post-save dropdown behavior:** Close dropdown after save
- **Toast duration:** Use sonner defaults

---

## Spec-to-Code Mapping

| Requirement | File | Status |
|-------------|------|--------|
| Public page at `/settings` | `routes/SettingsPage.tsx` | CREATE |
| Fetch 11 keywords | GET `/api/user/associations` | CREATE |
| Update keyword | PUT `/api/user/me/username-keyword` | CREATE |
| Dropdown single-select | `components/settings/UsernameSection.tsx` | CREATE |
| Pre-select current keyword | `useAuthQuery()` | EXISTS |
| Live preview | Local state in component | CREATE |
| Unauthenticated sign-in | Conditional render | CREATE |
| Header Settings link | `Header.tsx` line 201 | MODIFY |
| OAuth flow | `handleGoogleLogin` in Header | EXISTS |

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `SettingsPage.tsx` | `routes/IdentityPage.tsx` | Page structure, Suspense |
| `useUserSettingsQuery.ts` | `hooks/useAuthQuery.ts` | Query + mutation pattern |
| `UserSettingsSchemas.ts` | `schemas/IdentitySchemas.ts` | Zod validation |
| `UsernameSection.tsx` | `components/common/AssociationDropdown.tsx` | Single-select dropdown |
| `UserController.java` endpoints | Existing `UserController.java` | Rate limit, auth injection |
| `UserService.updateKeyword()` | Existing `UserService.java` | Transactional update |

---

## Existing Utilities (REUSE)

| Category | Location | Functions |
|----------|----------|-----------|
| Auth query | `hooks/useAuthQuery.ts` | `useAuthQuery()`, `authQueryKeys.me` |
| API client | `lib/api.ts` | `ApiClient.get<T>()`, `ApiClient.put<T>()` |
| Dropdown UI | `components/ui/dropdown-menu` | `DropdownMenuRadioItem` |
| Toast | `sonner` | `toast.success()`, `toast.error()` |
| Keyword validation | `UsernameConfig.java` | `isValidAssociation()` |
| OAuth helpers | `lib/oauth.ts` | `generateState()`, `generateCodeVerifier()` |

---

## Gap Analysis

**Backend CREATE:**
- `dto/user/AssociationDto.java`
- `dto/user/AssociationListResponse.java`
- `dto/user/UpdateUsernameKeywordRequest.java`

**Backend MODIFY:**
- `UsernameConfig.java` - add `getAssociationsWithInfo()`
- `UserService.java` - add `updateUsernameKeyword()`
- `UserController.java` - add GET/PUT endpoints
- `SecurityConfig.java` - whitelist GET associations

**Frontend CREATE:**
- `routes/SettingsPage.tsx`
- `hooks/useUserSettingsQuery.ts`
- `schemas/UserSettingsSchemas.ts`
- `types/UserSettingsTypes.ts`
- `components/settings/UsernameSection.tsx`

**Frontend MODIFY:**
- `lib/router.tsx` - add `/settings` route
- `components/Header.tsx` - link Settings button

---

## Testing Requirements

**Manual UI:**
- `/settings` unauthenticated → page loads, sign-in shown
- OAuth login → dropdown with current keyword
- Select new → preview updates
- Save → dropdown closes, success toast, Header updates

**Automated:**
- GET associations: 11 items, public (no 401)
- PUT validates keyword, 400 for invalid
- Mutation invalidates auth cache

**Edge Cases:**
- Same keyword (idempotent)
- Network error → toast, selection preserved
- Logout mid-session → sign-in prompt

---

## Implementation Order

1. Backend DTOs (3 records)
2. `UsernameConfig.getAssociationsWithInfo()`
3. `UserService.updateUsernameKeyword()`
4. `UserController` endpoints
5. `SecurityConfig` whitelist
6. Frontend types + schemas
7. `useUserSettingsQuery` hook
8. `SettingsPage` + `UsernameSection`
9. Router update
10. Header link update
