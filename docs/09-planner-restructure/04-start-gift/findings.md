# Findings: Start Gift Summary + EditPane

## What Was Easy

- Pattern reference clarity: StartBuffSection + StartBuffEditPane provided exact template
- Existing utility reuse: useStartGiftPools, useEGOGiftListData, useStartBuffData already available
- Component composition: EGOGiftCard and StartGiftRow required zero modification
- Test fixture predictability: Clear responsibility split made mocking straightforward

## What Was Challenging

- Callback closure stability: useEffect dependency on callbacks surfaced stale closure risk
- Locale i18n incompleteness: Only EN common.json updated; CN/JP/KR missing key
- Edge case EA decrease: Selection trimming behavior not explicitly detailed in spec
- Prop count growth: EditPane approached 8-prop threshold

## Key Learnings

- Spec-to-pattern mapping works when reference is complete (2+ working examples)
- i18n is silent failure until manual testing—scan all locale folders before completion
- DialogContent accessibility: shadcn/ui Dialog tests flagged missing aria-describedby
- State lifting discipline pays off: Autosave integration required zero changes
- Pure function extraction: EA calculation stayed testable via pure calculateMaxGiftSelection

## Spec-Driven Process Feedback

- research.md mapping was 100% accurate—no discovering-as-building needed
- Execution order (7-phase plan) worked with no backtracking
- Verification checkpoints sufficient: Compile → Integration → Tests
- Callback stability not flagged in research—add closure analysis to research template

## Pattern Recommendations

- Add "Locale Coverage Checklist" to fe-component skill (verify CN/JP/KR, not just EN)
- Document DialogContent aria-describedby pattern for shadcn/ui testing
- Codify callback wrapping rule: require useCallback for parent callbacks in EditPane
- Add "conditional max + state trimming" pattern to fe-data skill

## Next Time

- Add locale scan to post-implementation checklist (grep new i18n keys across all langs)
- Extract prop-heavy components earlier (before 8-prop threshold)
- Surface edge cases explicitly in research.md (not buried in verification)
- Use spec ambiguity section more aggressively even for "no ambiguities"
