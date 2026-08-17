# Learning Reflection: EGO Detail Page Refactoring

## Root Cause Analysis: Why Pattern Copying Failed

**Core Issue**: Created "simplified version" instead of precise copy. Read Identity template but made assumptions instead of verifying component-by-component.

**Specific Failures:**
- StyledSkillName: Identity uses styled backgrounds but EGO initially used plain text
- Attack Weight: Instructions specified yellow squares but code showed "Target: N" text
- coinString Schema: Field existed in JSON but missing from Zod schema, silently stripped during validation
- Incremental Discovery: Built stubs first, added details after visual bugs appeared

## Pattern Copy Checklist Violations

- Incomplete reference reading: Skimmed files instead of reading all 151 lines of PassiveI18n
- Schema validation gap: Didn't verify EGO schema against actual JSON data structure
- Visual component simplification: Used plain text instead of importing StyledSkillName directly
- Structural elements ignored: Pattern of "structure visible + text suspends" not replicated initially
- Type system not leveraged: Passive ID types (string vs number) not declared upfront

## Preventable Delays

- Visual bug investigation: 1-2 hours debugging CoinDisplay null return from schema validation
- Three explicit user corrections: StyledSkillName missing, attack weight format wrong, coinString schema missing
- Attack weight re-implementation: Complete removal and replacement of wrong pattern

## Copy Process Anti-Patterns

- "Inspired by" instead of "exact copy": Used Identity as loose reference
- Assumption-driven: Created minimal version assuming EGO was simpler
- No validation checkpoints: No pre-commit schema verification or visual comparison
- Incremental discovery: Built first, found errors later instead of reading complete reference first

## Spec-Driven Process Failure Points

- research.md incomplete: Didn't mandate schema-to-data verification before implementation
- Instructions assumed understanding: "Apply Identity patterns" without verification checklist
- plan.md missing validation gates: No schema alignment check before rendering phase
- Acceptance criteria vague: "Copy UI" should specify visual parity verification method

## Mandatory Copy Protocol (Add to Process)

**Pre-Implementation Phase:**
- Read ENTIRE reference files (all lines, document every component/hook/field)
- Extract schema fields from reference, compare to source data JSON
- Create field mapping: data → schema → component prop, verify no drops
- Inventory visual components, verify imports exist
- Document JSX hierarchy and Suspense patterns before coding
- Establish type contracts (ID types, optional fields) upfront

**Implementation Phase:**
- Copy structure BEFORE customizing for new domain
- No "simplified" versions - copy exactly, refactor later if needed
- Verification checklist: schema matches data, imports resolve, types locked

**Post-Implementation Phase:**
- Visual parity check: render side-by-side with reference
- Verify pixel-matching on layout, spacing, colors
- Verify state behavior identical (button clicks, language changes)
- Don't commit until visual verification passes

## Skill Documentation Gaps

- fe-component missing: Component copy checklist (file reads, field mapping, import verification)
- Missing: Visual component verification protocol (grep for component usage consistency)
- Missing: Schema field synchronization rule with automated check
- Missing: "Exact copy vs refactor" decision tree for pattern copying tasks
- Missing: Explicit anti-simplification rule ("copy exactly, refactor separately")
