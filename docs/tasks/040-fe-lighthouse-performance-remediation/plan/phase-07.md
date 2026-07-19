# Phase 07 (local-tdd)

## Rows
- readonly-note-renders-without-editor-mount: frontend/src/pages/planner/components/plannerViewer/__tests__/GuideModeViewer.test.tsx#readonly-note-renders-without-mounting-tiptap — it renders

## Touches
- frontend/src/pages/planner/PlannerMDGesellschaftDetailPage.tsx
- frontend/src/pages/planner/PlannerMDNewPage.tsx
- frontend/src/pages/planner/components/plannerViewer/**

## Validation
- `yarn --cwd frontend vitest run src/pages/planner`

## Seams (drives)
- read-only note display -> renders converted HTML without mounting the tiptap editor

## DECISION SLOTS
SLOT ordering-rationale: Single seam — the note-editor mount in the plan viewer + the two edit routes. Wrap the tiptap editor in `React.lazy` behind the edit interaction; the read-only display path renders pre-converted HTML. Assertion-red because the current display path mounts the editor synchronously.
SLOT batch-boundary: One row, one red-green cycle. The bundle-level proof (editor chunk absent from the display route) is verified at phase 09 (the route-chunk sweep) — this phase proves the mount behavior at unit tier.
SLOT fallback-scope: No stub surface. Phase 08 depends on this phase; if blocked, the deferred-render row in 08 falls back to shell-first WITHOUT the editor split — still improves render, but leaves the 105KB chunk on `gesellschaft-detail` + `planner-md-new`.
