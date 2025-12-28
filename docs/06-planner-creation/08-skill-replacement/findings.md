# Skill Replacement - Learning Reflection

## What Was Easy

- Reusing responsive grid pattern from SinnerGrid.tsx - Tailwind breakpoints translated directly
- Extracting skill image layers from SkillImageComposite.tsx - component structure was clear
- State lifting pattern from existing sections like EGOGiftObservationSection
- Dialog/Modal implementation - shadcn Dialog pattern from DeckBuilder directly transferable
- Constants integration - DEFAULT_SKILL_EA and OFFENSIVE_SKILL_SLOTS already existed

## What Was Challenging

- Discovering useIdentityListData returns `spec` not `identities` - spec mapping wasn't explicit about return naming
- State architecture mismatch - skillEAState outside DeckState creates dual sources of truth
- DeckBuilder breaking change scope - refactoring mid-stream exposed consumers before impact understood
- Skill exchange constraint modeling - EA as resource required clarifying exchange vs reset semantics
- Data fetching layer inconsistency - section fetches internally while other sections receive props

## Key Learnings

- React Compiler eliminates manual optimization - useRef+useEffect patterns unnecessary with React Compiler
- Type consolidation prevents ripple effects - SkillInfo duplicated in 3 files showed early extraction value
- Spec-to-Pattern mapping requires semantic understanding - not just "use this" but understanding why
- State placement decisions have long-term debt implications - not integrating skillEAState into DeckState = future debt
- Component responsibility clarity matters - deciding fetch vs receive-props affects downstream architecture
- Pattern checks before writing code are essential - prevented several violations

## Spec-Driven Process Feedback

- Research.md was 95% accurate but missed data fetching layer assumptions
- Plan execution order worked until mid-stream architectural change - DeckBuilder refactor should be pre-implementation
- Spec had good constraint documentation but left implementation strategy to interpretation
- Gap analysis correctly identified missing components but didn't anticipate type consolidation needs
- Technical constraints list was comprehensive and properly enforced

## Pattern Recommendations

- Add data fetching strategy to frontend guidelines: document "receive props" vs "fetch internally" decision criteria
- Document state architecture anti-pattern: when extending state, must integrate into root DeckState type
- Extract image composition layer pattern: create reusable utility for absolute positioning SVG/clip-path logic
- Establish breaking change protocol: require explicit refactor phase in plan before mid-stream changes
- Clarify i18n key structure: document why pages.plannerMD.skillReplacement is preferred

## Next Time

- Front-load data fetching pattern decisions during research phase
- Include "API compatibility check" before mid-stream refactoring consumed components
- Add state architecture review to pre-write checklist - confirm new state integrates into root types
- Separate responsive pattern verification from component creation
- Document "context switch cost" in plan - deferred decisions accumulate debt
