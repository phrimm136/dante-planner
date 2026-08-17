# Note Editor Execution Plan

## Planning Gaps
None - research addressed all major decisions.

## Execution Overview
Bottom-up dependency order: types/schemas → adapters → extensions → core editor → toolbar/dialog → integration. Each phase enables incremental testing. Client-only component due to Tiptap DOM requirements.

## Execution Order

### Phase 1: Setup and Foundation (Steps 1-4)

1. **Install Tiptap packages**
   - Run: `yarn add @tiptap/react @tiptap/starter-kit @tiptap/extension-link @tiptap/extension-image @tiptap/html`
   - Depends on: none
   - Enables: F1, F2, F5, F6

2. **`types/NoteEditorTypes.ts`**: TypeScript interfaces
   - Define: NoteContent, NoteEditorProps, ToolbarProps, LinkDialogProps, IImageUploadAdapter
   - Depends on: Step 1
   - Enables: F1, F3, F4, F5, F6

3. **`schemas/NoteEditorSchemas.ts`**: Zod schemas
   - Define: NoteContentSchema for JSONContent validation
   - Depends on: Step 2
   - Enables: F8

4. **`lib/constants.ts`**: Add constants
   - Add: MAX_NOTE_EDITORS = 20, NOTE_TOOLBAR_ITEMS
   - Depends on: none
   - Enables: F7, F2

### Phase 2: Core Editor Infrastructure (Steps 5-7)

5. **`components/noteEditor/adapters/IImageUploadAdapter.ts`**: Interface
   - Define: upload(file) → Promise<string>, delete(url) → Promise<void>
   - Depends on: Step 2
   - Enables: F6

6. **`components/noteEditor/adapters/MockImageAdapter.ts`**: Mock implementation
   - Implement: URL.createObjectURL with cleanup tracking
   - Depends on: Step 5
   - Enables: F6

7. **`components/noteEditor/extensions/SpoilerExtension.ts`**: Custom mark
   - Create: Tiptap Mark with toggleSpoiler command
   - Depends on: Step 1
   - Enables: F2 (spoiler tags)

### Phase 3: Editor Components (Steps 8-11)

8. **`components/noteEditor/NoteEditor.tsx`**: Main component
   - Implement: Controlled component (value + onChange)
   - Configure: useEditor with StarterKit, Link, Image, Spoiler
   - Handle: Focus/blur toggle for editable + toolbar
   - Depends on: Steps 5-7, Steps 2-3
   - Enables: F1, F3, F4

9. **`components/noteEditor/LinkDialog.tsx`**: Link insertion
   - Implement: shadcn Dialog with URL + display text
   - Populate: Selected text as default
   - Depends on: Step 8
   - Enables: F5

10. **`components/noteEditor/Toolbar.tsx`**: Formatting toolbar
    - Implement: Buttons for all formatting options
    - Handle: Active state highlighting
    - Depends on: Steps 8-9
    - Enables: F2

11. **`static/i18n/*/common.json`**: Add i18n keys
    - Add: pages.plannerMD.noteEditor namespace
    - Depends on: none (parallel)
    - Enables: All UI text

### Phase 4: Integration (Steps 12-13)

12. **`routes/PlannerMDNewPage.tsx`**: Integration
    - Add: noteSections state array
    - Render: Map NoteEditor instances
    - Handle: Add/remove section controls
    - Depends on: Steps 8-10
    - Enables: F7, F8

13. **Performance verification**
    - Test: 20 concurrent editors
    - Measure: Load time, memory
    - Optimize: Lazy init if needed
    - Depends on: Step 12
    - Enables: F7

## Verification Checkpoints
- After Step 1: `yarn build` succeeds
- After Step 3: Types/schemas compile
- After Step 7: SpoilerExtension toggleSpoiler command works
- After Step 8: Single editor focus/blur works, content updates
- After Step 9: LinkDialog applies URL to selected text
- After Step 10: All toolbar buttons apply formatting
- After Step 12: 5 sections save independently
- After Step 13: 20 sections render under 3s

## Rollback Strategy
- Step 1 fails: Remove packages, run yarn install
- Steps 2-3 conflict: Delete type/schema files
- Step 7 errors: Remove SpoilerExtension, use StarterKit only
- Step 8 crashes: Wrap in ErrorBoundary, fallback textarea
- Step 9 issues: Disable link button, use prompt() fallback
- Step 12 breaks: Revert PlannerMDNewPage, editor is isolated
- Step 13 slow: Reduce MAX_NOTE_EDITORS, virtualize
