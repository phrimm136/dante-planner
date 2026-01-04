# Task: Settings Page - Username Keyword Change

## Description

Create a settings page where users can customize their display username by selecting from 11 in-game faction keywords. This is the first feature of the Settings Page epic.

**Username Format:** `Faust-{KEYWORD}-{suffix}`
- **Faust**: Fixed prefix (represents the player character)
- **KEYWORD**: User-selectable from available factions
- **suffix**: 5-character immutable identifier (ensures uniqueness)

**Authentication Model:**
- Page is publicly accessible at `/settings`
- Username section visible ONLY for authenticated users
- Unauthenticated users see a "Sign in to customize your username" message with login button
- No redirect - page stays visible with gated content

**UI Layout (Authenticated):**
- Section header "USERNAME"
- Current username display: `Faust-{keyword}-{suffix}`
- Dropdown menu (Select component) showing all 11 faction options
- Dropdown pre-selects current user's keyword
- Live preview below dropdown showing new username format
- Save button to confirm changes (disabled if unchanged from original)

**UI Layout (Unauthenticated):**
- Section header "USERNAME"
- Message: "Sign in to customize your username"
- Google login button (reuse existing OAuth flow from Header)

**Dropdown Behavior:**
- Shows display name in trigger (e.g., "WCorp")
- Options list shows all 11 display names
- Current keyword auto-detected and pre-selected on load
- Selecting new option updates local state and preview immediately

**Available Keywords:**
| Keyword | Display Name |
|---------|--------------|
| LIMBUS_COMPANY_LCB | LCB |
| W_CORP | WCorp |
| LOBOTOMY_BRANCH | Lobotomy |
| N_CORP | NCorp |
| ZWEI | Zwei |
| SEVEN | Seven |
| BLADE_LINEAGE | Blade |
| WUTHERING_HEIGHTS | Butler |
| MULTI_CRACK | Multicrack |
| H_CORP | Heishou |
| SHI | Shi |

**Behaviors:**
- Dropdown selection updates local state immediately (live preview)
- Save button triggers PUT request to update backend
- On success: invalidate auth query so Header updates, show success toast
- On error: show error toast, keep selection (allow retry)
- Settings accessible via header Settings button and direct URL `/settings`

## Research

- Backend username system: `UsernameConfig.java`, `User.java`
- Existing auth hooks: `useAuthQuery.ts`
- OAuth flow in Header: `Header.tsx` (handleGoogleLogin function)
- API client patterns: `lib/api.ts`
- Route patterns: `lib/router.tsx`
- Header Settings button: `Header.tsx` (lines 200-207)
- shadcn Select component: `components/ui/select.tsx`

## Scope

Files to READ for context:
- `backend/src/main/java/org/danteplanner/backend/config/UsernameConfig.java`
- `backend/src/main/java/org/danteplanner/backend/controller/UserController.java`
- `backend/src/main/java/org/danteplanner/backend/service/UserService.java`
- `frontend/src/hooks/useAuthQuery.ts`
- `frontend/src/lib/api.ts`
- `frontend/src/lib/router.tsx`
- `frontend/src/components/Header.tsx`
- `frontend/src/components/ui/select.tsx`

## Target Code Area

**Backend CREATE:**
- `dto/user/AssociationDto.java`
- `dto/user/AssociationListResponse.java`
- `dto/user/UpdateUsernameKeywordRequest.java`

**Backend MODIFY:**
- `config/UsernameConfig.java` - add `getAssociationsWithInfo()`
- `service/UserService.java` - add `updateUsernameKeyword()`
- `controller/UserController.java` - add GET/PUT endpoints
- `config/SecurityConfig.java` - whitelist GET associations

**Frontend CREATE:**
- `types/UserSettingsTypes.ts`
- `schemas/UserSettingsSchemas.ts`
- `hooks/useUserSettingsQuery.ts`
- `routes/SettingsPage.tsx`

**Frontend MODIFY:**
- `lib/router.tsx` - add /settings route
- `components/Header.tsx` - link Settings button to /settings

## Testing Guidelines

### Manual UI Testing
1. Navigate to `/settings` while logged out
2. Verify page loads (no redirect)
3. Verify USERNAME section shows "Sign in" message
4. Click login button in the section
5. Complete Google OAuth flow
6. Verify section now shows dropdown UI
7. Verify dropdown shows current keyword's display name
8. Click dropdown to expand options
9. Verify all 11 options are visible
10. Select a different keyword
11. Verify live preview updates immediately
12. Verify Save button becomes enabled
13. Click Save button
14. Verify success toast appears
15. Verify dropdown still shows newly saved keyword
16. Navigate to Header dropdown - username should reflect new keyword
17. Click Settings button in header
18. Verify it navigates to /settings page

### Automated Functional Verification
- [ ] Public access: /settings loads for unauthenticated users
- [ ] Gated content: Dropdown hidden when not authenticated
- [ ] Login prompt: Sign-in button visible for unauthenticated users
- [ ] Associations load: All 11 keywords in dropdown for authenticated users
- [ ] Current detection: Dropdown pre-selects user's current keyword
- [ ] Live preview: Updates on dropdown selection change
- [ ] Save mutation: PUT request sent with correct payload
- [ ] Cache invalidation: Auth query refetched on success

### Edge Cases
- [ ] Same keyword selected: Save still works (idempotent)
- [ ] Invalid keyword from API: 400 error, toast shown
- [ ] Network error: Toast shown, selection preserved for retry
- [ ] User logs out mid-session: Section reverts to sign-in prompt
- [ ] OAuth popup blocked: Graceful error handling

### Integration Points
- [ ] Header dropdown: Displays updated username after save
- [ ] Auth context: User data refreshed after mutation
- [ ] OAuth flow: Reuses existing Google login from Header
