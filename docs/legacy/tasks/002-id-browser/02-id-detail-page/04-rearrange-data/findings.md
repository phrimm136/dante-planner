# Learning Findings: Modular Detail Page Layout

## What Was Easy

- Constants centralization pattern (DETAIL_PAGE object) made values discoverable and reusable
- shadcn/ui ScrollArea and Slider had straightforward APIs with Tailwind-friendly styling
- Composition via props (children, slots) enabled reusable components without tight coupling
- Spec-to-pattern mapping identified exact source files, making pattern adaptation faster

## What Was Challenging

- Breakpoint trade-off: 768px spec violated by actual content needs, required documented deviation
- React Compiler constraint: useEffect state sync pattern only surfaced as violation during review
- HP calculation duplication: formula appeared inline, not flagged for centralization until review
- Tailwind JIT static class requirement: template literals incompatible, required cn() refactor

## Key Learnings

- Document breakpoint rationale with actual pixel math (410px at 1024px adequate, 307px at 768px too cramped)
- Identify React Compiler constraints BEFORE design phase to prevent rework
- Reusable calculations (HP formula) belong in lib/, not components - extract early
- Composition with prop-based slots works cleanly across entity types without modification
- Sticky positioning needs explicit z-index constants, not magic numbers

## Spec-Driven Process Feedback

- Research.md spec-to-code mapping was accurate and identified right pattern sources
- Plan execution order (Foundation → Components → Layout → Integration) worked well
- Spec ambiguity: 768px breakpoint didn't account for available viewport calculations
- Entity-specific adaptations (labels) not detailed enough - discovered during pattern research

## Pattern Recommendations

- Add "Responsive Breakpoint Validation" pattern: validate minimum content widths at target breakpoints
- Document "Calculation Utility Extraction" trigger: formulas used across entities go to lib/
- Establish "Constants Grouping" convention: related config in single object (DETAIL_PAGE pattern)
- Create React Compiler anti-patterns checklist: useEffect sync, manual memo, template literals

## Next Time

- Validate breakpoint assumptions with pixel math before implementation
- Extract formulas to lib/calculations.ts during foundation step, not as debt later
- Add React Compiler constraint review to spec phase
- Clarify entity-specific label mappings in instructions upfront
