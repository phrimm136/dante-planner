# Execution Plan: Comprehensive Gift Section → Summary + Pane

## Planning Gaps

**NONE** - Research complete. All patterns, utilities, and i18n keys verified.

---

## Execution Overview

Convert inline gift selection section to Summary + Pane pattern:
1. Create Summary component (displays selected gifts, clickable)
2. Create Pane component (Dialog with filters, selection list, cascade logic)
3. Modify page (wire new components, add pane state)
4. Delete old section (after verification)

**Key principle:** Move cascade logic FROM current section TO new pane.

---

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| `EGOGiftComprehensiveListSection.tsx` | Low | egoGiftEncoding, EGOGiftSelectionList | PlannerMDNewPage only |
| `PlannerMDNewPage.tsx` | High | Many sections | Page shell - no consumers |
| `ComprehensiveGiftSummary.tsx` (NEW) | Low | useEGOGiftListData, EGOGiftCard | PlannerMDNewPage |
| `ComprehensiveGiftSelectorPane.tsx` (NEW) | Low | Same as current section + Dialog | PlannerMDNewPage |

### Ripple Effect Map

- `EGOGiftComprehensiveListSection.tsx` → only imported by `PlannerMDNewPage.tsx`
- **No external ripple effects** - change is isolated to planner page

### High-Risk Modifications

| Area | Mitigation |
|------|------------|
| PlannerMDNewPage (900+ lines) | Surgical changes: import swap + state add + JSX swap |
| Cascade logic transfer | Copy verbatim from current section (lines 68-101) |

---

## Execution Order

### Phase 1: Create New Components (Safe - No Breaking Changes)

**Step 1: Create `ComprehensiveGiftSummary.tsx`**
- Pattern: `FloorGiftViewer.tsx`
- Additions: PlannerSection wrapper (like ObservationSummary)
- Props: `selectedGiftIds`, `onClick`
- Enables: F1 (summary display), F2 (empty state)

**Step 2: Create `ComprehensiveGiftSelectorPane.tsx`**
- Pattern: `FloorGiftSelectorPane.tsx` (Dialog structure)
- Logic: Copy from `EGOGiftComprehensiveListSection.tsx` (lines 68-101)
- Difference: No `giftIdFilter`, has cascade logic
- Props: `open`, `onOpenChange`, `selectedGiftIds`, `onGiftSelectionChange`
- Enables: F3 (pane opens), F4 (enhancement), F5 (cascade), F6 (filters)

### Phase 2: Integrate Into Page

**Step 3: Modify `PlannerMDNewPage.tsx`**
- Add state: `isComprehensivePaneOpen`
- Remove import: `EGOGiftComprehensiveListSection`
- Add imports: `ComprehensiveGiftSummary`, `ComprehensiveGiftSelectorPane`
- Replace JSX at lines 841-845
- Preserve: Suspense boundary, NoteEditor placement
- Enables: All features integrated

### Phase 3: Verify and Cleanup

**Step 4: Manual Testing**
- Empty state, pane open/close, filters, enhancement, cascade, persistence

**Step 5: Delete `EGOGiftComprehensiveListSection.tsx`**
- Only after Step 4 passes

---

## Verification Checkpoints

| After Step | Verification | Pass Criteria |
|------------|--------------|---------------|
| Step 2 | TypeScript compile | No type errors |
| Step 3 | Page load | No errors, summary visible |
| Step 3 | Pane interaction | Dialog opens on click |
| Step 4 | Full feature test | All F1-F6 work |
| Step 5 | Cleanup | No broken imports |

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Cascade logic bug | 2 | Copy verbatim; test with recipe gift |
| Tooltip positioning | 1 | Use exact pattern from FloorGiftViewer |
| Pane scroll issues | 2 | Use `max-h-[85vh] overflow-hidden flex flex-col` |
| Filter state persistence | 2 | Filters are local to pane (reset on reopen) |

---

## Rollback Strategy

**If Step 3 fails:**
- Revert `PlannerMDNewPage.tsx`
- Keep new files for debugging
- Original section still works

**Critical:** Do NOT delete old section until Step 4 passes completely.

---

## Pattern Sources

| New File | Read First | Copy From |
|----------|------------|-----------|
| ComprehensiveGiftSummary | FloorGiftViewer.tsx | Layout, tooltips, empty state |
| ComprehensiveGiftSelectorPane | FloorGiftSelectorPane.tsx | Dialog structure |
| ComprehensiveGiftSelectorPane | EGOGiftComprehensiveListSection.tsx:68-101 | Cascade logic |
