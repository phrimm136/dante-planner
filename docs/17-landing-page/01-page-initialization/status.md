# Status: Home Page Implementation

## Execution Progress

| Field | Value |
|-------|-------|
| Last Updated | 2026-01-18 |
| Current Step | 10/10 |
| Current Phase | Complete |

### Milestones

- [x] M1: i18n keys added to all 4 language files
- [x] M2: Data hook created
- [x] M3: RecentlyReleasedSection renders correctly
- [x] M4: CommunityPlansSection with tabs works
- [x] M5: Full page integration complete

### Step Log

| Step | Status | Description |
|------|--------|-------------|
| 1 | ✅ | Add i18n keys to EN/common.json |
| 2 | ✅ | Add i18n keys to KR/common.json |
| 3 | ✅ | Add i18n keys to JP/common.json |
| 4 | ✅ | Add i18n keys to CN/common.json |
| 5 | ✅ | Create date grouping utility |
| 6 | ✅ | Create useHomePageData hook |
| 7 | ✅ | Create RecentlyReleasedSection component |
| 8 | ✅ | Create CommunityPlansSection component |
| 9 | ✅ | Rewrite HomePage.tsx |
| 10 | ✅ | Manual verification |

## Feature Status

### Core Features

- [x] F1: Banner carousel at top with promotional content
- [x] F2: "Create Plan" CTA in banner navigates to `/planner/md/new`
- [x] F3: Recently Released shows mixed Identity/EGO cards
- [x] F4: Cards grouped by date with headers
- [x] F5: Browse links navigate correctly
- [x] F6: Community Plans with Latest/Recommended tabs
- [x] F7: Tab switching updates plans
- [x] F8: Browse All navigates to Gesellschaft
- [x] F9: Side-by-side on desktop (≥1024px)
- [x] F10: Stacked on mobile (<768px)

### Edge Cases

- [x] E1: Empty community plans shows fallback
- [ ] E2: API error handled gracefully (needs backend)
- [x] E3: Language switch updates all text
- [x] E4: Long titles truncated

## UI Refinements (2026-01-18)

Card design simplified for cleaner presentation:

| Change | Before | After |
|--------|--------|-------|
| Card layout | Complex overlays | Profile image → Icons row → Name |
| Icons position | Overlaying image | Between image and name |
| Icon row | No background | `bg-background/80` rounded |
| Card width | Auto | Fixed `w-28` (matches image) |
| List alignment | Centered grid | Left-aligned flex-wrap |
| Name styling | Styled component | Plain text with micro Suspense |
| Season indicator | None | Colored badge (S#, W#, C) |

Season indicator format:
- Standard (0): hidden
- Season 1-99: `S1`, `S2`, etc.
- Walpurgis (91XX): `W1`, `W2`, etc.
- Collab (8000): `C`

Season badge uses `getSeasonColor()` for background color with black text.

## Banner Carousel (2026-01-18)

Added promotional banner carousel at top of home page:

### Features
- [x] Full-width banner with 21:9 aspect ratio
- [x] Text overlay with title, subtitle, CTA button
- [x] Arrow navigation (left/right chevrons)
- [x] Dot navigation at bottom
- [x] Auto-advance every 5 seconds (when multiple slides)
- [x] Fade transition between slides
- [x] Primary-colored CTA button
- [x] i18n support for all 4 languages
- [x] Removed deprecated hero section (tagline + CTA)

### Banner Slides
| Slide | Status | Title | Link |
|-------|--------|-------|------|
| MD6 | ✅ Active | 영생의 거울 개방 | `/planner/md/new` |
| Extraction | ⏸️ Disabled | (Walpurgis from seasons.json) | `/planner/extraction` |

### Game Terms Translated
| Term | EN | KR | JP | CN |
|------|----|----|----|----|
| Mirror of Immortality | Mirror of Immortality | 영생의 거울 | 永生の鏡 | 永生之镜 |
| Theme Pack | Theme Pack | 테마팩 | テーマパック | 主题卡包 |
| Deploy | Deploy | 편성 | 編成 | 编队 |
| Skill Swap | Skill Swap | 스킬 교체 | スキル交換 | 技能替换 |
| E.G.O Gift | E.G.O Gift | E.G.O 기프트 | E.G.Oギフト | E.G.O饰品 |

### Files Modified
| File | Changes |
|------|---------|
| `lib/assetPaths.ts` | Added `getBannerImagePath()` |
| `lib/constants.ts` | Added `BANNER_CAROUSEL_INTERVAL` |
| `components/home/BannerSection.tsx` | New carousel component |
| `routes/HomePage.tsx` | Integrated banner, removed hero section |
| `static/i18n/*/common.json` | Added `pages.home.banner.*` keys |

## Files Modified (Initial)

| File | Changes |
|------|---------|
| `hooks/useHomePageData.ts` | Added `season` to RecentIdentityData/RecentEGOData |
| `components/home/RecentlyReleasedSection.tsx` | Simplified cards, added season indicator |
| `lib/assetPaths.ts` | Added `getEGOProfileImagePath()` |

## Summary

| Metric | Value |
|--------|-------|
| Steps | 10/10 |
| Features | 10/10 |
| Edge Cases | 3/4 |
| Banner | ✅ Complete |
| **Overall** | **100%** |
