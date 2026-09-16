/**
 * Suspense fallbacks shared by the planner editor and both viewers, so a
 * section's loading shape is described once wherever that section is hosted.
 */

import { useTranslation } from 'react-i18next'

import { Skeleton } from '@/components/ui/skeleton'
import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { PlannerGridSkeleton } from '@/components/feedback/ListPageSkeleton'
import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import {
  LG_BREAKPOINT_PX,
  MD_BREAKPOINT_PX,
  SECTION_STYLES,
  STAGGER_STEP_MS,
} from '@/lib/constants'
import { staggerDelay } from '@/lib/stagger'
import { cn } from '@/lib/utils'
import { MD_CATEGORIES, SINNERS } from '@/shared/gameData'
import { CardSlot, EGO_GIFT_GEOMETRY, PLANNER_GEOMETRY, useSlotSizePx } from '@/shared/cardLayout'

import { PlannerSection } from '@/components/layout/PlannerSection'
import { useSinnerGridLayout } from '../hooks/useSinnerGridLayout'
import { useSkillReplacementLayout } from '../hooks/useSkillReplacementLayout'
import {
  GIFT_GRID_ROWS,
  GIFT_ROW_PADDING_PX,
  giftGridHeightPx,
  giftRowMinHeightPx,
  KEYWORD_ICON_GEOMETRY,
  SINNER_DECK_GEOMETRY,
  SINNER_SKILL_GEOMETRY,
} from '../lib/cardLayout'

const GIFT_TILES = 6
const SKILL_TILES = 12
const START_GIFT_TILES = 2
const FILTER_CONTROLS = 3
/** All, plus one per Mirror Dungeon category. */
const CATEGORY_PILLS = MD_CATEGORIES.length + 1
/** Keyword icons the planner header carries, at a typical plan's count. */
const HEADER_KEYWORD_ICONS = 2
/** Note-bearing blocks of the create form below the deck. */
const CREATE_FORM_SECTIONS = 3

/** The gift row's height at the current breakpoint, shared by every gift-bearing fallback. */
function useGiftRowMinHeightPx(): number {
  const { heightPx } = useSlotSizePx(EGO_GIFT_GEOMETRY.size, EGO_GIFT_GEOMETRY.mobileScale)

  return giftRowMinHeightPx(heightPx)
}

/** The starlight-cost row every gift-bearing section carries above its row. */
function CostRow() {
  return (
    <div className="flex justify-end mb-4">
      <div className="w-8 h-8 rounded bg-muted" />
      <TextSkeleton size="base" width="xs" />
    </div>
  )
}

/** Sinner cards of the deck summary, before the deck data resolves. */
export function DeckGridSkeleton() {
  const { t } = useTranslation(['planner', 'common'])
  const { gridStyle } = useSinnerGridLayout()

  return (
    <PlannerSection title={t('pages.plannerMD.deckBuilder')}>
      <div className="grid mx-auto" style={gridStyle}>
        {SINNERS.map((sinnerName, i) => (
          <CardSlot
            key={sinnerName}
            size={SINNER_DECK_GEOMETRY.size}
            mobileScale={SINNER_DECK_GEOMETRY.mobileScale}
          >
            <Skeleton className="size-full rounded-lg" style={staggerDelay(i)} />
          </CardSlot>
        ))}
      </div>

      <div className="mt-6 flex flex-col lg:flex-row lg:items-start lg:justify-between gap-3">
        <div className="border rounded-lg p-3 space-y-2">
          <TextSkeleton size="xs" lines={2} width="lg" />
        </div>
      </div>
    </PlannerSection>
  )
}

/** The Grace of Stars section, before the buff data resolves. */
export function StartBuffSkeleton() {
  const { t } = useTranslation(['planner', 'common'])
  const minHeight = useGiftRowMinHeightPx()

  return (
    <PlannerSection title={t('pages.plannerMD.startBuffs')}>
      <CostRow />
      <Skeleton className="w-full rounded-lg" style={{ minHeight }} />
    </PlannerSection>
  )
}

/** The start-gift keyword and its gifts, before the gift data resolves. */
export function StartGiftSkeleton() {
  const { t } = useTranslation(['planner', 'common'])
  const minHeight = useGiftRowMinHeightPx()

  return (
    <PlannerSection title={t('pages.plannerMD.startEgoGift')}>
      <div className="flex items-center gap-4" style={{ padding: GIFT_ROW_PADDING_PX, minHeight }}>
        <CardSlot
          size={KEYWORD_ICON_GEOMETRY.size}
          mobileScale={KEYWORD_ICON_GEOMETRY.mobileScale}
          className="shrink-0"
        >
          <Skeleton className="size-full rounded-lg" />
        </CardSlot>
        <div className="flex flex-wrap gap-2">
          {Array.from({ length: START_GIFT_TILES }).map((_, i) => (
            <CardSlot
              key={i}
              size={EGO_GIFT_GEOMETRY.size}
              mobileScale={EGO_GIFT_GEOMETRY.mobileScale}
            >
              <Skeleton className="size-full rounded-lg" style={staggerDelay(i)} />
            </CardSlot>
          ))}
        </div>
      </div>
    </PlannerSection>
  )
}

/** Wrapping grid of gift tiles inside its titled section. */
export function GiftGridSkeleton({ title }: { title: string }) {
  const minHeight = useGiftRowMinHeightPx()

  return (
    <PlannerSection title={title}>
      <CostRow />
      <div className="flex flex-wrap gap-2" style={{ padding: GIFT_ROW_PADDING_PX, minHeight }}>
        {Array.from({ length: GIFT_TILES }).map((_, i) => (
          <CardSlot
            key={i}
            size={EGO_GIFT_GEOMETRY.size}
            mobileScale={EGO_GIFT_GEOMETRY.mobileScale}
          >
            <Skeleton
              className="size-full rounded-lg"
              style={staggerDelay(i, STAGGER_STEP_MS.LOOSE)}
            />
          </CardSlot>
        ))}
      </div>
    </PlannerSection>
  )
}

interface GiftGridTrackerSkeletonProps {
  /** The box the grid takes; omitted → it stretches to the column it sits in. */
  height?: number | undefined
}

/** The filtered comprehensive gift grid, before the gift data resolves. */
export function GiftGridTrackerSkeleton({ height }: GiftGridTrackerSkeletonProps) {
  const stretch = height === undefined

  return (
    <div className={cn('flex flex-col gap-2', stretch && 'flex-1 min-h-0')}>
      <div className="flex flex-wrap gap-2">
        {Array.from({ length: FILTER_CONTROLS }).map((_, i) => (
          <div key={i} className="h-10 w-32 rounded-md bg-muted" />
        ))}
      </div>
      <div className={cn('flex', stretch && 'flex-1 min-h-0')} style={{ height }}>
        <Skeleton className="w-full rounded-md" />
      </div>
    </div>
  )
}

/** One card per sinner, each with its three skill slots. */
export function SkillGridSkeleton({ title }: { title: string }) {
  const { gridStyle } = useSkillReplacementLayout()

  return (
    <PlannerSection title={title}>
      <div className="grid mx-auto" style={gridStyle}>
        {Array.from({ length: SKILL_TILES }).map((_, i) => (
          <CardSlot
            key={i}
            size={SINNER_SKILL_GEOMETRY.size}
            mobileScale={SINNER_SKILL_GEOMETRY.mobileScale}
          >
            <Skeleton
              className="size-full rounded-lg"
              style={staggerDelay(i, STAGGER_STEP_MS.NORMAL)}
            />
          </CardSlot>
        ))}
      </div>
    </PlannerSection>
  )
}

// ============================================================================
// Page-level skeletons
// ============================================================================

/** The `/planner/md` list page, before its chunk and its planner list resolve. */
export function PlannerMDPageSkeleton() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      {/* Create New, a default-size Button */}
      <div className="flex justify-end mb-6">
        <div className="h-9 w-32 rounded-md bg-muted" />
      </div>

      {/* My Plans / Gesellschaft, two size="sm" Buttons */}
      <div className="mb-6">
        <div className="flex gap-2">
          <div className="h-8 w-25 rounded-md bg-muted" />
          <div className="h-8 w-25 rounded-md bg-muted" />
        </div>
      </div>

      {/* MDPlannerToolbar, whose SearchBar is h-8 */}
      <div className="mb-4">
        <div className="h-8 w-full rounded-md bg-muted" />
      </div>

      {/* PlannerListFilterPills, four text-sm py-1.5 pills */}
      <div className="mb-4">
        <div className="flex gap-2 flex-wrap">
          {Array.from({ length: CATEGORY_PILLS }).map((_, i) => (
            <div key={i} className="h-8 w-16 rounded-full bg-muted" />
          ))}
        </div>
      </div>

      {/* PlannerFilterPane */}
      <div className="mb-4">
        <div className="h-10 w-full rounded-md bg-muted" />
      </div>

      <PlannerGridSkeleton geometry={PLANNER_GEOMETRY} />
    </div>
  )
}

/** The header rows both planner detail headers share, as `PlannerHeaderChrome` draws them. */
function PlannerHeaderSkeleton() {
  return (
    <header className="space-y-3">
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-3 min-w-0">
          {/* Back, an icon-sm Button */}
          <div className="size-8 rounded-md bg-muted" />
          {/* Category badge */}
          <div className="h-6 w-12 rounded bg-muted" />
          {/* Keyword icons */}
          <div className="flex items-center gap-1.5">
            {Array.from({ length: HEADER_KEYWORD_ICONS }).map((_, i) => (
              <div key={i} className="size-6 rounded bg-muted" />
            ))}
          </div>
        </div>
        {/* Variant meta block */}
        <div className="flex items-center gap-3 shrink-0">
          <div className="h-6 w-24 rounded bg-muted" />
        </div>
      </div>

      <div className="flex items-center justify-between gap-4">
        <TextSkeleton size="2xl" width="lg" />
        {/* Variant actions */}
        <div className="flex items-center gap-1 shrink-0">
          <div className="size-8 rounded-md bg-muted" />
          <div className="size-8 rounded-md bg-muted" />
        </div>
      </div>
    </header>
  )
}

/**
 * A saved planner's header and its viewer sections, before the planner resolves.
 *
 * The three planner detail routes show it while their chunk loads and again while the
 * planner is read, so one shape spans both waits.
 */
export function PlannerViewerSkeleton() {
  const { t } = useTranslation(['planner', 'common'])
  const isMd = useIsBreakpoint('min', MD_BREAKPOINT_PX)
  const isLg = useIsBreakpoint('min', LG_BREAKPOINT_PX)
  const { heightPx: giftSlotHeightPx } = useSlotSizePx(
    EGO_GIFT_GEOMETRY.size,
    EGO_GIFT_GEOMETRY.mobileScale,
  )
  const giftGridHeight = isMd
    ? giftGridHeightPx(isLg ? GIFT_GRID_ROWS.lg : GIFT_GRID_ROWS.md, giftSlotHeightPx)
    : undefined

  return (
    <div className="space-y-4">
      <PlannerHeaderSkeleton />

      <div className="bg-background rounded-lg space-y-2">
        <DeckGridSkeleton />
        <StartBuffSkeleton />
        <StartGiftSkeleton />
        <GiftGridSkeleton title={t('pages.plannerMD.egoGiftObservation')} />
        <SkillGridSkeleton title={t('pages.plannerMD.skillReplacement.title')} />
        <PlannerSection title={t('pages.plannerMD.comprehensiveEgoGiftListView')}>
          <GiftGridTrackerSkeleton height={giftGridHeight} />
        </PlannerSection>
      </div>
    </div>
  )
}

/** The `/planner/md/new` create form, before its static data resolves. */
export function PlannerMDNewPageSkeleton() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <div className="flex items-center justify-between mb-4">
        <TextSkeleton size="2xl" width="lg" />
        <div className="h-10 w-24 rounded-md bg-muted" />
      </div>
      <TextSkeleton className="mb-6" width="lg" />

      <div className="bg-background rounded-lg p-6 space-y-6">
        <div className="flex gap-4">
          <div className="h-10 w-32 rounded-md bg-muted" />
          <div className="h-10 flex-1 rounded-md bg-muted" />
        </div>

        <div className="h-10 w-full rounded-md bg-muted" />

        <DeckGridSkeleton />

        {Array.from({ length: CREATE_FORM_SECTIONS }).map((_, i) => (
          <div key={i} className="space-y-2">
            <TextSkeleton size="base" width="lg" />
            <div className="h-32 w-full rounded-lg bg-muted" />
          </div>
        ))}
      </div>
    </div>
  )
}

/** The `/planner/deck` page, before the deck's static data resolves. */
export function DeckBuilderPageSkeleton() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <DeckGridSkeleton />
    </div>
  )
}
