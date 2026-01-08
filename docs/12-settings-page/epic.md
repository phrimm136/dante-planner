# Epic: Settings Page

## Description

A unified settings page where authenticated users can manage their account preferences. The page contains three sections: username customization, notification preferences, and account management (including deletion).

## Features

### 1. Username Keyword Selection

Users can customize their display username by selecting from 11 in-game faction keywords. The username format is `Faust-{KEYWORD}-{suffix}`, where:
- **Faust**: Fixed prefix (represents the player character)
- **KEYWORD**: User-selectable from available factions (W_CORP, LOBOTOMY_BRANCH, etc.)
- **suffix**: 5-character immutable identifier (ensures uniqueness)

The section displays:
- Current username at the top
- Grid of selectable keyword options (radio buttons)
- Live preview showing how the new username will appear
- Save button to confirm changes

Available keywords:
| Keyword | Display Name | Faction |
|---------|--------------|---------|
| LIMBUS_COMPANY_LCB | LCB | Limbus Company |
| W_CORP | WCorp | W Corp |
| LOBOTOMY_BRANCH | Lobotomy | Lobotomy Corporation |
| N_CORP | NCorp | N Corp |
| ZWEI | Zwei | Zwei Association |
| SEVEN | Seven | Seven Association |
| BLADE_LINEAGE | Blade | Blade Lineage |
| WUTHERING_HEIGHTS | Butler | Wuthering Heights |
| MULTI_CRACK | Multicrack | Multi Crack Office |
| H_CORP | Heishou | H Corp |
| SHI | Shi | Shi Association |

### 2. Notification Settings

Users can configure their notification preferences for the platform. Settings are stored per-user and persist across sessions.

Options:
- **Email notifications**: Toggle for receiving email updates
  - New comments on published planners
  - Weekly digest of community activity
  - System announcements
- **Browser notifications**: Toggle for push notifications (if supported)
  - Real-time updates when planners receive votes/comments

Each toggle saves immediately on change (optimistic update pattern). A toast confirms the change.

### 3. Account Deletion

Users can request account deletion with a 30-day grace period for recovery. This section appears at the bottom with a danger zone styling.

Flow:
1. User clicks "Delete Account" button
2. Confirmation dialog appears explaining:
   - Account will be soft-deleted immediately
   - User cannot log in during grace period
   - Re-authenticating via OAuth within 30 days reactivates account
   - After 30 days, account and all data permanently deleted
   - Published planners will be removed
   - Vote history preserved but anonymized
3. User confirms deletion
4. Backend performs soft-delete, returns scheduled permanent deletion date
5. User is logged out and redirected to home

The existing backend already supports this via `DELETE /api/user/me`.

## Page Layout

```
┌─────────────────────────────────────────────────────────────┐
│  ⚙️ Settings                                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ USERNAME                                             │   │
│  │ ───────────────────────────────────────────────────  │   │
│  │ Current: Faust-W_CORP-abc23                         │   │
│  │                                                      │   │
│  │ Select your faction:                                 │   │
│  │ ┌──────────────────────────────────────────────┐    │   │
│  │ │ ○ LCB        ○ NCorp      ○ Zwei             │    │   │
│  │ │ ● WCorp      ○ Lobotomy   ○ Seven            │    │   │
│  │ │ ○ Blade      ○ Butler     ○ Multicrack       │    │   │
│  │ │ ○ Heishou    ○ Shi                           │    │   │
│  │ └──────────────────────────────────────────────┘    │   │
│  │                                                      │   │
│  │ Preview: Faust-W_CORP-abc23                         │   │
│  │                                     [ Save ]         │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ NOTIFICATIONS                                        │   │
│  │ ───────────────────────────────────────────────────  │   │
│  │                                                      │   │
│  │ Email Notifications                                  │   │
│  │ ├─ Comments on my planners          [━━━○]          │   │
│  │ ├─ Weekly community digest          [○━━━]          │   │
│  │ └─ System announcements             [━━━○]          │   │
│  │                                                      │   │
│  │ Browser Notifications                                │   │
│  │ └─ Real-time vote/comment updates   [○━━━]          │   │
│  │                                                      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ DANGER ZONE                                    ⚠️    │   │
│  │ ───────────────────────────────────────────────────  │   │
│  │                                                      │   │
│  │ Delete your account and all associated data.         │   │
│  │ You have 30 days to recover by logging in again.    │   │
│  │                                                      │   │
│  │                          [ Delete Account ]          │   │
│  │                          (destructive button)        │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## API Endpoints

### Existing

| Method | Path | Purpose | Status |
|--------|------|---------|--------|
| GET | `/api/auth/me` | Get current user | ✅ Exists |
| DELETE | `/api/user/me` | Soft-delete account | ✅ Exists |

### New Required

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/user/associations` | Fetch keyword options |
| PUT | `/api/user/me/username-keyword` | Update username keyword |
| GET | `/api/user/me/notifications` | Get notification settings |
| PUT | `/api/user/me/notifications` | Update notification settings |

## Backend Changes

### New DTOs

```
dto/user/
├── AssociationDto.java              # keyword, displayName, addedDate
├── AssociationListResponse.java     # List<AssociationDto>
├── UpdateUsernameKeywordRequest.java # keyword (validated)
├── NotificationSettingsDto.java     # email*, browser* toggles
└── UserDeletionResponse.java        # (exists)
```

### Entity Changes

Add to `User.java`:
```java
// Notification preferences (stored as JSON or separate columns)
private boolean notifyComments = true;
private boolean notifyWeeklyDigest = false;
private boolean notifyAnnouncements = true;
private boolean notifyBrowserUpdates = false;
```

### Controller Changes

Extend `UserController.java`:
- GET `/associations` - public, returns all keywords
- PUT `/me/username-keyword` - authenticated, updates keyword
- GET `/me/notifications` - authenticated, returns settings
- PUT `/me/notifications` - authenticated, updates settings

### Service Changes

Extend `UserService.java`:
- `updateUsernameKeyword(userId, keyword)` - validates and updates
- `getNotificationSettings(userId)` - returns DTO
- `updateNotificationSettings(userId, dto)` - updates preferences

Add to `UsernameConfig.java`:
- `getAssociationsWithInfo()` - returns List<AssociationDto>

## Frontend Changes

### New Files

```
frontend/src/
├── routes/
│   └── SettingsPage.tsx
├── hooks/
│   └── useUserSettingsQuery.ts
├── schemas/
│   └── UserSettingsSchemas.ts
├── types/
│   └── UserSettingsTypes.ts
└── components/
    └── settings/
        ├── UsernameSection.tsx
        ├── NotificationSection.tsx
        └── DangerZoneSection.tsx
```

### Router Update

Add to `lib/router.tsx`:
```typescript
const settingsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/settings',
  component: lazyRouteComponent(() => import('@/routes/SettingsPage')),
})
```

### Header Update

Replace Settings button (line 200-207) with Link to `/settings`.

## Acceptance Criteria

### Username Keyword
- [ ] User can see all 11 keyword options
- [ ] User can select a different keyword
- [ ] Live preview updates on selection
- [ ] Save persists to database
- [ ] Header dropdown reflects change immediately

### Notification Settings
- [ ] User can toggle each notification type
- [ ] Toggles save immediately (optimistic update)
- [ ] Settings persist across sessions
- [ ] Toast confirms each change

### Account Deletion
- [ ] Delete button shows confirmation dialog
- [ ] Dialog explains 30-day grace period
- [ ] Confirmation triggers soft-delete
- [ ] User is logged out after deletion
- [ ] Re-login within 30 days reactivates account

### General
- [ ] Page requires authentication
- [ ] Unauthenticated users redirected to login
- [ ] Settings accessible via header link and direct URL
- [ ] Mobile-responsive layout
- [ ] i18n support for all text

## Technical Notes

### Authentication Flow
- Page wrapped in auth-required boundary
- Uses existing `useAuthQuery()` for user data
- Mutations invalidate auth query on success

### Error Handling
- Invalid keyword: 400 response, show error toast
- Network error: Show retry option
- Deletion error: Keep dialog open, show error

### Caching Strategy
- Associations: `staleTime: Infinity` (static data)
- User data: Default stale time, invalidate on mutation
- Notifications: Invalidate on toggle

## Future Extensions

- Theme settings (dark/light mode)
- Language preference (move from header)
- Data export (GDPR compliance)
- Two-factor authentication
- Connected accounts management
