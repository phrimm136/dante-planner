# Note Editor Research

## Clarifications Resolved
- **Storage**: Embedded in planner JSON (simpler, atomic, matches existing patterns)
- **State Management**: Parent-lifted (PlannerMDNewPage holds notes array)
- **Scope**: Frontend only (backend persistence separate task)
- **Images**: Mock blob URLs for development

## Spec Ambiguities (Addressed)
- Tiptap not installed → Add as prerequisite step
- Spoiler extension → Requires custom Tiptap Mark implementation
- Link dialog behavior → Selected text becomes default; user confirms via dialog
- Image cleanup → Revoke blob URLs on editor unmount or content change

## Spec-to-Code Mapping
- **NoteEditor component** → `components/noteEditor/NoteEditor.tsx`
- **Toolbar component** → `components/noteEditor/Toolbar.tsx`
- **Link dialog** → `components/noteEditor/LinkDialog.tsx`
- **Spoiler extension** → `components/noteEditor/extensions/SpoilerExtension.ts`
- **Image adapter interface** → `components/noteEditor/adapters/IImageUploadAdapter.ts`
- **Mock adapter** → `components/noteEditor/adapters/MockImageAdapter.ts`
- **Types** → `types/NoteEditorTypes.ts`
- **Schemas** → `schemas/NoteEditorSchemas.ts`
- **PlannerMDNewPage integration** → Add noteSections state, map to NoteEditor instances
- **i18n** → Add keys to `static/i18n/*/common.json`
- **Constants** → Add MAX_NOTE_EDITORS, toolbar enums to `lib/constants.ts`

## Spec-to-Pattern Mapping
- Controlled component → Match StartBuffSectionProps pattern (value + onChange)
- State lifting → Match equipment state lifting in PlannerMDNewPage
- Dialog → Copy SkillExchangeModal structure
- Zod validation → Follow IdentitySchemas.ts pattern
- i18n → Use `pages.plannerMD.*` namespace
- Conditional render → `isFocused && editable` for toolbar visibility
- Error boundary → Wrap editor container in ErrorBoundary

## Gap Analysis
- **Missing dependencies**: @tiptap/react, @tiptap/starter-kit, @tiptap/extension-link, @tiptap/extension-image
- **No existing editor**: Greenfield component
- **Custom extension**: Spoiler mark extension (no reference pattern)
- **i18n strings**: Not yet added to locale files
- **Performance untested**: 20 concurrent editors need monitoring

## Technical Constraints
- Tiptap must support React 19
- JSONContent for storage (not Markdown)
- Blob URLs valid only in session (convert on final save)
- CSS scoping: Tiptap styles must not conflict with shadcn/ui
- SSR-ready: NoteEditor cannot run on server (client-only)

## Implementation Prerequisites
1. Install Tiptap packages: `yarn add @tiptap/react @tiptap/starter-kit @tiptap/extension-link @tiptap/extension-image @tiptap/html`
2. Verify React 19 compatibility
3. Research Tiptap custom Mark extension pattern for Spoiler
