# Findings: Planner Card Reconstruction

## What Was Easy

- Keyword extraction pattern: Reused existing logic from PlannerCard (slice + "+N" overflow)
- Constants addition: RECOMMENDED_THRESHOLD and color updates were pure additions
- Type system flexibility: Adding optional selectedKeywords maintained backward compatibility
- Component extraction: Moving PersonalPlannerCard to separate file was low-risk
- Indicator logic: Mutually exclusive state conditions were clearly specified

## What Was Challenging

- Spec-to-implementation gap: Research stated "no backend changes" but keyword persistence required DTOs
- Type guarding complexity: Extracting keywords required RR/MD differentiation
- Layout stability: Star indicator space reservation needed explicit handling
- Left-click navigation pattern: Emerged during implementation, not in spec
- Testing gaps: Pre-existing stale mocks created false confidence

## Key Learnings

- Research-to-code validation: Backend changes discovered mid-implementation
- Type guards are essential: Defensive programming for multi-planner-type systems
- Layout shift prevention: Reserve space for dynamic indicators upfront
- Scope creep discipline: Beyond-plan additions should be documented as separate features
- Backend-frontend coupling: Frontend type changes can force backend DTO updates

## Spec-Driven Process Feedback

- Research mapping: 90% accurate but missed backend scope - need DTO impact analysis
- Plan execution order: Phase 1→2→3 structure worked well
- Spec clarifications: Reserved space for indicator area was documented but not obvious
- Beyond-plan scope: Icon keywords, left-click nav weren't in spec but became core features

## Pattern Recommendations

- Multi-type data extraction: Create guard utility (e.g., extractMDKeywords with fallback)
- Dynamic indicator patterns: Always reserve space for indicators to prevent layout shift
- Backend impact checklist: Trace frontend type changes through API/DTOs/services
- Left-click/right-click disambiguation: Document dual interaction pattern for cards

## Next Time

- Validate backend scope upfront before plan.md
- Update research.md when discoveries emerge, don't wait for review
- Add visual mockup to spec for layout changes
- Pre-test stale mock cleanup when working on sync-related code
- Document "beyond plan" as separate commits with own testing scope
