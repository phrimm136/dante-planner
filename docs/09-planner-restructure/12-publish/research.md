# Publish Functionality - Research Findings

## Spec Ambiguities

**NONE FOUND** - All requirements clearly specified and align with existing patterns.

---

## Spec-to-Code Mapping

### A. Toggle Publish API Method
- **File:** `/frontend/src/lib/plannerApi.ts`
- **Location:** After `import()` method (line 102)
- **Action:** Add new `togglePublish(id)` method

### B. Publish Handler Implementation
- **File:** `/frontend/src/routes/PlannerMDNewPage.tsx`
- **Location:** Lines 626-640 (replace TODO)
- **Action:** Implement save-then-publish flow with error handling

### C. i18n Keys
- **Files:** `/static/i18n/{EN,KR,JP,CN}/common.json`
- **Location:** Under `pages` object
- **Action:** Add `pages.plannerMD.publish.*` section (4 keys per language)

### D. Button Loading State
- **File:** `PlannerMDNewPage.tsx` (line 692-694)
- **Status:** Already implemented - uses `isPublishing` flag

### E. Auto-save Integration
- **File:** `usePlannerSave.ts`
- **Status:** Already implemented - `save()` returns boolean for flow control

---

## Spec-to-Pattern Mapping

### API Method Pattern
- **Reference:** `plannerApi.ts:76-82` (`update()` method)
- **Pattern:** `ApiClient.put(endpoint)` + `ServerPlannerResponseSchema.parse(data)`
- **Apply to:** New `togglePublish()` method

### Save-then-Publish Flow Pattern
- **Reference:** `PlannerMDNewPage.tsx:613-619` (`handleSave()`)
- **Pattern:** Sequential await with early return on failure
- **Apply to:** `handlePublish()` implementation

### Toggle Button State Pattern
- **Reference:** `PlannerMDNewPage.tsx:688-695` (Save button)
- **Pattern:** Flag-based state (`isPublishing`) + conditional text rendering
- **Apply to:** Publish button behavior

### Toast Notification Pattern
- **Reference:** `PlannerMDNewPage.tsx:613-619`
- **Pattern:** `toast.success(t('key'))` for success, `toast.error(t('key'))` for errors
- **Apply to:** Publish success/failure feedback

---

## Pattern Enforcement

### Files to Read Before Implementation

| Target File | Must Read First | Pattern to Copy |
|-------------|-----------------|-----------------|
| `plannerApi.ts` | `plannerApi.ts:76-82` | PUT endpoint, Zod validation, ServerPlannerResponse return |
| `PlannerMDNewPage.tsx` | Lines 613-619 | Async handler, await save(), toast pattern, try-catch |
| `common.json` (all 4) | Current file structure | Nested `pages.plannerMD.{feature}.{key}` convention |

---

## Pattern Copy Deep Analysis

### Reference: plannerApi update() Method

**Structure (lines 76-82):**
- Method signature: `async (id, request): Promise<ServerPlannerResponse>`
- API call: `ApiClient.put(endpoint, payload)`
- Validation: `ServerPlannerResponseSchema.parse(data)`
- Return: Typed response

**For togglePublish() mapping:**
- Endpoint: `${PLANNERS_BASE}/${id}/publish`
- Payload: None (idempotent toggle)
- Response: `ServerPlannerResponse` (includes `published` boolean)
- Error handling: Automatic via ApiClient

### Reference: handleSave() Pattern

**Structure (lines 613-619):**
- Async handler triggered by button
- Await operation: `const success = await save()`
- Success feedback: `if (success) toast.success(i18n_key)`
- Error handling: Caught by hook's internal error state

**For handlePublish() mapping:**
- Pre-check: `const success = await save()` → early return if false
- API call: `await plannerApi.togglePublish(plannerId)`
- Success feedback: Toast with i18n key
- Error handling: Try-catch with error toast

### Reference: i18n Structure

**Current pattern:**
```
pages → info/planner/community → title/description/actions
```

**New section:**
```
pages.plannerMD.publish.button        → "Publish" / "Unpublish"
pages.plannerMD.publish.publishing    → "Publishing..."
pages.plannerMD.publish.success       → "Planner published successfully"
pages.plannerMD.publish.failed        → "Failed to publish planner"
```

---

## Existing Utilities

### API Client
- **Location:** `/frontend/src/lib/api.ts`
- **Available:** `ApiClient.put()`, `ApiClient.post()`, `ApiClient.get()`, `ApiClient.delete()`
- **Error handling:** Automatic HTTP error throwing, consumed by try-catch

### Validation
- **Location:** `/frontend/src/schemas/PlannerSchemas.ts`
- **Available:** `ServerPlannerResponseSchema` (validates API responses)
- **Pattern:** Parse with `.parse()` for runtime validation

### Toast
- **Location:** `sonner` library (already imported)
- **Available:** `toast.success()`, `toast.error()`
- **Pattern:** Direct call with i18n message

### State Management
- **Location:** Component useState
- **Available:** `isPublishing` flag (already declared)
- **Pattern:** Set true on start, false in finally block

---

## Gap Analysis

### Currently Missing (Requires Implementation)
- `plannerApi.togglePublish()` method
- Complete `handlePublish()` logic (save + API call + toast)
- i18n keys: 16 entries (4 languages × 4 keys)
- Published state tracking in component (optional - for button text toggle)

### Needs Modification
- `handlePublish()` in `PlannerMDNewPage.tsx` - replace TODO block

### Can Reuse (No Changes)
- `usePlannerSave.ts` - save() method with boolean return
- Button UI structure - isPublishing flag integration
- Toast mechanism - sonner already configured
- Error handling patterns - established in component

---

## Technical Constraints

### Save Before Publish
- **Constraint:** Must save latest state before toggling publish
- **Implementation:** `handlePublish()` awaits `save()` first, aborts on failure

### Idempotent Endpoint
- **Constraint:** Backend toggles state; no explicit publish/unpublish distinction
- **Implementation:** Frontend tracks `published` field from response for button text

### Authentication Required
- **Constraint:** Anonymous users get 401, non-owners get 403
- **Implementation:** Backend enforces; frontend shows error toast

### Rate Limiting
- **Constraint:** Multiple rapid publishes trigger 429
- **Implementation:** `isPublishing` flag prevents UI race, backend has Bucket4j limit

### No SSE Notification
- **Constraint:** Other devices won't auto-update
- **Implementation:** Expected per spec; manual refresh required

### Zod Validation
- **Constraint:** Response must match schema or throw
- **Implementation:** Use existing `ServerPlannerResponseSchema`

---

## Testing Requirements

### Manual UI Tests

**Happy Path:**
1. Create planner, click Publish
2. Verify "Publishing..." text during operation
3. Verify success toast
4. Navigate to Gesellschaft page - planner visible
5. Return, click Publish again (toggle unpublish)
6. Navigate to Gesellschaft - planner removed

**Save-Before-Publish:**
1. Create planner, make edits without clicking Save
2. Click Publish
3. Verify auto-save occurs first
4. Verify publish completes with toast

**Error Scenarios:**
1. Publish planner you don't own → 403 error toast
2. Offline mode → network error toast
3. Rapid clicks → button disabled, no race

### Automated Functional Verification

**API Integration:**
- togglePublish() uses PUT to /api/planner/md/{id}/publish
- Response validated with ServerPlannerResponseSchema
- Method accepts PlannerId | string

**Handler Logic:**
- handlePublish() calls save() before togglePublish()
- Early return if save() returns false
- isPublishing flag lifecycle correct
- Success/error toasts with i18n keys

**i18n Keys:**
- All 4 keys in all 4 languages
- Nested pattern pages.plannerMD.publish.*

**Button Behavior:**
- Disabled while isPublishing is true
- Text changes during operation
- Returns to normal after completion

### Integration Points

**Planner List:**
- After publish → appears in Gesellschaft
- After unpublish → removed from Gesellschaft
- Personal list shows published indicator

**Multi-Device Sync:**
- No SSE notification (expected)
- Other devices require manual refresh

**Rate Limiting:**
- Counted in CRUD bucket
- Rapid publishes trigger 429

**Authentication:**
- Anonymous: 401
- Non-owner: 403

---

## Implementation Order

1. Add `togglePublish()` to plannerApi.ts
2. Add i18n keys to all 4 common.json files
3. Implement `handlePublish()` in PlannerMDNewPage.tsx
4. Manual testing of end-to-end flow
5. Add automated tests if applicable

---

## Domain Context

**Feature Domain:** Planner CRUD + Publishing
**Skills Required:** fe-component, fe-data
**Cross-Cutting:** API client, i18n, state management
**Risk Level:** LOW (additive, follows patterns, backend complete)
**Complexity:** MEDIUM (3 systems coordination)
**Scope:** 150-200 lines across 5-6 files
