# Learning Reflection: Extraction Probability Calculator

## What Was Easy

- Data layer ordering (constants → types → schemas) eliminated dependency issues
- Test-driven calculator math made edge cases visible early (77 tests)
- Spec-driven component structure matched execution plan exactly
- Rate-up math straightforward once formula understood (1.45% ÷ N)
- i18n namespace registration followed existing patterns

## What Was Challenging

- "All EGO Collected" semantic mismatch - meant "픽뚫 pool empty" not "no EGO available"
- Sampling model confusion - EGO uses 비복원추출, Identity uses 복원추출
- Multi-pity hidden requirement - spec didn't state floor(pulls÷200) model
- Input disable logic inversion - disabled inputs when they should stay active
- Coupon Collector dead code required cleanup pass

## Key Learnings

- Spec ambiguity requires domain research against actual game behavior
- Multi-trigger pity is floor-based (200, 400, 600), not single fixed trigger
- Rate modifiers shift pools, not hide inputs - "All EGO Collected" changes rates, doesn't disable
- Test coverage exposes logic inversions that cause silent failures
- Hypergeometric (EGO) vs Coupon Collector (ID) distinction produces different results

## Spec-Driven Process Feedback

- Research.md incomplete on extraction mechanics (비복원 vs 복원추출)
- Plan.md execution order excellent - zero blocked steps
- Instructions.md testing thorough but missed multi-pity floor(÷200) assertions

## Pattern Recommendations

- Document extraction mechanics explicitly with concrete rate examples
- Add "non-obvious rate modifier" pattern to skill docs
- Establish without/with replacement distinction in probability calculators
- Create multi-pity testing checklist (verify 0, 1, 2, 3+ triggers)

## Next Time

- Ask clarifying questions on game-specific terms (픽뚫, 복원추출)
- Extract sampling model upfront during research phase
- Verify "disabled vs modified" behavior explicitly in spec
- Add edge case assertions to plan (pity boundaries: 199, 200, 201 pulls)
