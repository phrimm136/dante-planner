# Task: Extraction Probability Calculator Page

## Description

Create a standalone extraction (gacha) probability calculator page at `/planner/extraction`. This tool helps users plan their pulls for limited banners by calculating probabilities of obtaining target items.

### Core Functionality

Users input:
- **Number of pulls** (1-400+)
- **Target counts** for each category:
  - 3★ Identities wanted (0-N, where N = featured count in banner)
  - EGOs wanted (0-N)
  - Announcers wanted (0-N)
- **Banner configuration**:
  - Total featured 3★ Identities in banner
  - Total featured EGOs in banner
  - Total featured Announcers in banner
- **Modifiers**:
  - "All EGO collected" checkbox (changes rate distribution)

**Announcer presence is calculated implicitly:** When featured Announcer count > 0, rates adjust automatically (no separate checkbox needed).

Calculator outputs:
- **P(at least one target)**: Probability of getting at least 1 of any target item
- **P(all targets)**: Probability of getting all target items in N pulls
- **P(all within pity)**: Probability using optimal pity strategy (get M-1 naturally, use pity on last)
- **Expected pulls**: Average pulls needed for all targets
- **Lunacy cost**: Total Lunacy required (pulls × 130)

### Extraction Rate Mechanics (Limbus Company)

**Base Rates (single pull):**
| Category | Standard | With Announcer | All EGO Collected | Both |
|----------|----------|----------------|-------------------|------|
| 3★ ID | 2.9% | 2.9% | 3.0% | 3.0% |
| 2★ ID | 12.8% | 12.8% | 13.0% | 13.0% |
| 1★ ID | 83.0% | 81.7% | 84.0% | 82.7% |
| EGO | 1.3% | 1.3% | 0% | 0% |
| Announcer | 0% | 1.3% | 0% | 1.3% |

**Rate-up mechanics:**
- Featured 3★ IDs share 1.45% total (split evenly: 1.45% ÷ N)
- Featured EGOs share 0.65% total (split evenly: 0.65% ÷ N)
- Featured Announcers share 1.3% total (split evenly: 1.3% ÷ N)

**Pity system:**
- Hard pity at 200 pulls
- User CHOOSES which featured item to claim (not random)
- Optimal strategy: Get M-1 items naturally, use pity on remaining item

### Probability Calculations

**Single target item:**
```
rate = categoryRateUp ÷ featuredCountInCategory
P(≥1 in N pulls) = 1 - (1 - rate)^N
```

**Multiple targets (M items) with pity:**
```
P(all M with pity, N ≥ 200) = P(get ≥ M-1 naturally) + P(get all M naturally)
                            = 1 - P(miss ≥ 2 items)
```

**10-pull bonus:** Every 10th pull guarantees minimum 2★ (95.8% 2★, 2.9% 3★, 1.3% EGO). This mainly affects 2★ calculations.

## Research

- [ ] Read existing planner page structure: `routes/PlannerMDNewPage.tsx`
- [ ] Study `PlannerSection` component for consistent section styling
- [ ] Review input patterns in planner (sliders, number inputs)
- [ ] Check `lib/constants.ts` for adding extraction rate constants
- [ ] Review router setup in `lib/router.tsx` for adding new route
- [ ] Study existing hook patterns: `hooks/useIdentityListData.ts`

## Scope

Files to READ for context:
- `frontend/src/routes/PlannerMDNewPage.tsx` - planner page structure
- `frontend/src/components/common/PlannerSection.tsx` - section wrapper
- `frontend/src/components/ui/input.tsx` - input component
- `frontend/src/components/ui/slider.tsx` - slider component
- `frontend/src/lib/router.tsx` - routing setup
- `frontend/src/lib/constants.ts` - constant patterns
- `frontend/src/types/PlannerTypes.ts` - type definition patterns
- `frontend/src/schemas/PlannerSchemas.ts` - Zod schema patterns

## Target Code Area

New files to CREATE:
- `frontend/src/routes/ExtractionPlannerPage.tsx` - main page component
- `frontend/src/components/extraction/ExtractionCalculator.tsx` - calculator container
- `frontend/src/components/extraction/ExtractionInputs.tsx` - input controls section
- `frontend/src/components/extraction/ExtractionResults.tsx` - results display section
- `frontend/src/lib/extractionCalculator.ts` - probability math functions
- `frontend/src/types/ExtractionTypes.ts` - TypeScript interfaces
- `frontend/src/schemas/ExtractionSchemas.ts` - Zod validation schemas
- `static/i18n/EN/extraction.json` - English translations
- `static/i18n/JP/extraction.json` - Japanese translations
- `static/i18n/KR/extraction.json` - Korean translations

Files to MODIFY:
- `frontend/src/lib/constants.ts` - add EXTRACTION_RATES constant
- `frontend/src/lib/router.tsx` - add extraction planner route

## Testing Guidelines

### Manual UI Testing

1. Navigate to `/planner/extraction`
2. Verify the page loads with default inputs (0 targets, 0 pulls)
3. Enter "100" in the pulls input field
4. Verify the input accepts the value and updates
5. Set "Featured 3★ Identities in banner" to 2
6. Set "3★ Identities wanted" to 1
7. Verify probability results update in real-time
8. Verify P(at least one) shows a percentage between 0-100%
9. Set pulls to 200
10. Verify P(all within pity) shows 100% for single target
11. Set "Featured Announcers in banner" to 1
12. Verify 1★ rate implicitly adjusts (announcer takes 1.3% from 1★ pool)
13. Enable "All EGO collected" checkbox
14. Verify EGO-related inputs become disabled or adjust appropriately
15. Set multiple targets: 2 IDs, 1 EGO, 1 Announcer
16. Verify complex probability calculation displays
17. Verify Lunacy cost shows correct value (pulls × 130)
18. Test extreme values: 0 pulls, 1000 pulls
19. Verify no negative probabilities or NaN values appear

### Automated Functional Verification

- [ ] Route accessible: `/planner/extraction` loads without error
- [ ] Input validation: Pulls field rejects negative numbers
- [ ] Input validation: Target counts cannot exceed featured counts
- [ ] Rate calculation: Single 3★ target at 100 pulls ≈ 76.5% (verify against 1-(1-0.0145)^100)
- [ ] Pity guarantee: Any single target with 200+ pulls = 100%
- [ ] EGO modifier: Enabling "All EGO collected" adjusts rates correctly
- [ ] Implicit announcer: Rates auto-adjust when announcer count > 0
- [ ] Announcer rate split: 2 announcers share 1.3% (0.65% each)
- [ ] Lunacy calculation: Displays pulls × 130 correctly
- [ ] Real-time updates: Results recalculate on input change without submit button

### Edge Cases

- [ ] Zero pulls: Shows 0% probability for all outputs
- [ ] Zero targets: Shows meaningful message or 100% (trivially satisfied)
- [ ] All targets with 200 pulls: Uses pity optimally, shows correct probability
- [ ] More targets than pity can guarantee: Shows < 100% even at 200 pulls
- [ ] Fractional rates: Handles rate-up splitting correctly
  - 3 featured IDs = 0.483% each (1.45% ÷ 3)
  - 2 featured Announcers = 0.65% each (1.3% ÷ 2)
- [ ] Large pull counts (1000+): No overflow or performance issues
- [ ] Rate-up split edge: 1 featured item gets full rate-up, not divided
- [ ] All EGO + Announcer: Both modifiers apply correctly together

### Integration Points

- [ ] Router: Navigation from `/planner` hub page works
- [ ] i18n: All text displays correctly in EN/JP/KR
- [ ] Theme: Calculator respects light/dark theme
- [ ] Mobile: Layout is responsive on mobile devices
- [ ] Constants: Uses EXTRACTION_RATES from constants.ts consistently
