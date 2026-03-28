/**
 * ThemePackDetailPage - Theme pack detail page with two-column layout
 *
 * Desktop: 4:6 ratio (left: image + metadata, right: gifts + events)
 * Mobile: Single column, left on top, right below
 */

import { useParams } from '@tanstack/react-router'
import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import { DetailPageSkeleton } from '@/components/common/DetailPageSkeleton'
import { ThemePackCard } from '@/components/floorTheme/ThemePackCard'
import { EGOGiftCard } from '@/components/egoGift/EGOGiftCard'
import { EGOGiftName } from '@/components/egoGift/EGOGiftName'
import { AbEventCard } from '@/components/abEvent/AbEventCard'
import { Skeleton } from '@/components/ui/skeleton'
import { useThemePackDetailData } from '@/hooks/useThemePackDetailData'
import { useThemePackListData } from '@/hooks/useThemePackListData'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { useAbEventListData } from '@/hooks/useAbEventListData'
import {
  DUNGEON_IDX,
  DIFFICULTY_COLORS,
  DIFFICULTY_LABELS,
  THEME_PACK_FLOOR_LABELS,
} from '@/lib/constants'
import type { DungeonIdx, ThemePackFloor, DifficultyLabel } from '@/lib/constants'
import type { ThemePackDetail } from '@/schemas/ThemePackSchemas'
import { Link } from '@tanstack/react-router'

// =============================================================================
// Left Column Components
// =============================================================================

/**
 * Difficulty badges with colored text matching DIFFICULTY_COLORS
 */
function DifficultyBadges({ conditions }: { conditions: ThemePackDetail['exceptionConditions'] }) {
  const dungeonIdxSet = new Set(conditions.map((c) => c.dungeonIdx))

  const DUNGEON_DISPLAY: { idx: DungeonIdx; label: DifficultyLabel }[] = [
    { idx: DUNGEON_IDX.NORMAL, label: DIFFICULTY_LABELS.NORMAL },
    { idx: DUNGEON_IDX.HARD, label: DIFFICULTY_LABELS.HARD },
    { idx: DUNGEON_IDX.PARALLEL, label: DIFFICULTY_LABELS.INFINITY_MIRROR },
    { idx: DUNGEON_IDX.EXTREME, label: DIFFICULTY_LABELS.EXTREME_MIRROR },
  ]

  return (
    <div className="flex flex-wrap gap-2">
      {DUNGEON_DISPLAY.filter((d) => dungeonIdxSet.has(d.idx)).map((d) => (
        <span
          key={d.idx}
          className="px-2 py-0.5 text-xs font-semibold rounded border border-border"
          style={{ color: DIFFICULTY_COLORS[d.label] }}
        >
          {d.label}
        </span>
      ))}
    </div>
  )
}

/**
 * Floor display grouped by difficulty.
 * Only shows Normal (0) and Hard (1) — Infinity/Extreme have no floor data.
 */
function FloorDisplay({ conditions }: { conditions: ThemePackDetail['exceptionConditions'] }) {
  const FLOOR_DIFFICULTIES: { idx: DungeonIdx; label: DifficultyLabel }[] = [
    { idx: DUNGEON_IDX.NORMAL, label: DIFFICULTY_LABELS.NORMAL },
    { idx: DUNGEON_IDX.HARD, label: DIFFICULTY_LABELS.HARD },
  ]

  const floorGroups = FLOOR_DIFFICULTIES
    .map((d) => {
      const conds = conditions.filter((c) => c.dungeonIdx === d.idx && c.selectableFloors)
      const floors = new Set<number>()
      for (const c of conds) {
        for (const f of c.selectableFloors ?? []) {
          floors.add(f)
        }
      }
      return { ...d, floors: Array.from(floors).sort() }
    })
    .filter((g) => g.floors.length > 0)

  if (floorGroups.length === 0) return null

  return (
    <div className="space-y-2">
      {floorGroups.map((group) => (
        <div key={group.idx}>
          <div
            className="text-xs font-medium mb-1"
            style={{ color: DIFFICULTY_COLORS[group.label] }}
          >
            {group.label}
          </div>
          <div className="flex flex-wrap gap-1">
            {group.floors.map((floor) => (
              <span
                key={floor}
                className="px-2 py-0.5 text-xs rounded border border-border"
              >
                {THEME_PACK_FLOOR_LABELS[floor as ThemePackFloor]}
              </span>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}

// =============================================================================
// Right Column Components
// =============================================================================

/**
 * Section header for right column
 */
function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide border-b border-border pb-2">
      {children}
    </h2>
  )
}

/**
 * Specific EGO gifts grid (from specificEgoGiftPool + fusioned gifts)
 */
function SpecificEgoGifts({ giftIds }: { giftIds: number[] }) {
  const { spec } = useEGOGiftListData()

  if (giftIds.length === 0) return null

  // Find fusioned gifts: gifts whose recipe materials are all in this pool
  const poolSet = new Set(giftIds.map(String))
  const fusionedIds: string[] = []
  for (const [id, giftSpec] of Object.entries(spec)) {
    if (poolSet.has(id)) continue
    const recipe = giftSpec.recipe
    if (!recipe) continue
    if ('type' in recipe && recipe.type === 'mixed') {
      const allIds = [...(recipe.a?.ids ?? []), ...(recipe.b?.ids ?? [])]
      if (allIds.length > 0 && allIds.every((mid) => poolSet.has(String(mid)))) {
        fusionedIds.push(id)
      }
    } else if ('materials' in recipe) {
      for (const matSet of recipe.materials ?? []) {
        if (matSet.length > 0 && matSet.every((mid) => poolSet.has(String(mid)))) {
          fusionedIds.push(id)
          break
        }
      }
    }
  }

  const allIds = [...giftIds.map(String), ...fusionedIds]

  return (
    <div className="flex flex-wrap gap-3">
      {allIds.map((id) => {
        const giftSpec = spec[id]
        if (!giftSpec) return null
        return (
          <Link key={id} to="/ego-gift/$id" params={{ id }}>
            <div className="flex flex-col items-center gap-1">
              <EGOGiftCard
                gift={{
                  id,
                  tag: giftSpec.tag,
                  keyword: giftSpec.keyword,
                  attributeType: giftSpec.attributeType,
                  themePack: giftSpec.themePack,
                  maxEnhancement: giftSpec.maxEnhancement,
                }}
                enableHoverHighlight
              />
              <span className="text-xs text-center text-foreground line-clamp-2 w-24 leading-tight font-medium">
                <EGOGiftName id={id} />
              </span>
            </div>
          </Link>
        )
      })}
    </div>
  )
}

/**
 * Exclusive abnormality events section — events only in this pack.
 * Self-contained: renders nothing if no exclusive events found.
 */
function ExclusiveEventsSection({
  eventPool,
  packId,
}: {
  eventPool: number[]
  packId: string
}) {
  const { t } = useTranslation('database')
  const { spec: abEventSpec } = useAbEventListData()

  const exclusiveIds = eventPool.filter((eventId) => {
    const entry = abEventSpec[String(eventId)]
    if (!entry) return false
    return entry.relatedThemePacks.length === 1 && entry.relatedThemePacks[0] === packId
  })

  if (exclusiveIds.length === 0) return null

  return (
    <div className="space-y-3">
      <SectionTitle>{t('themePack.exclusiveEvents', 'Exclusive Abnormality Events')}</SectionTitle>
      <div className="flex flex-wrap gap-3">
        {exclusiveIds.map((eventId) => {
          const eid = String(eventId)
          const entry = abEventSpec[eid]
          if (!entry) return null
          return (
            <Link key={eid} to="/ab-event/$id" params={{ id: eid }}>
              <div className="w-40">
                <AbEventCard
                  eventId={eid}
                  hasImage={entry.hasImage}
                  enableHoverHighlight
                />
              </div>
            </Link>
          )
        })}
      </div>
    </div>
  )
}

/**
 * All acquirable EGO gifts grid (from egoGiftPool)
 */
function AllEgoGifts({ giftIds }: { giftIds: number[] }) {
  const { spec } = useEGOGiftListData()

  return (
    <div className="flex flex-wrap gap-2">
      {giftIds.map((giftId) => {
        const id = String(giftId)
        const giftSpec = spec[id]
        if (!giftSpec) return null
        return (
          <Link key={id} to="/ego-gift/$id" params={{ id }}>
            <EGOGiftCard
              gift={{
                id,
                tag: giftSpec.tag,
                keyword: giftSpec.keyword,
                attributeType: giftSpec.attributeType,
                themePack: giftSpec.themePack,
                maxEnhancement: giftSpec.maxEnhancement,
              }}
              enableHoverHighlight
            />
          </Link>
        )
      })}
    </div>
  )
}

/**
 * All encounterable events grid — title below image
 */
function AllEvents({ eventPool }: { eventPool: number[] }) {
  const { spec: abEventSpec } = useAbEventListData()

  return (
    <div className="flex flex-wrap gap-3">
      {eventPool.map((eventId) => {
        const eid = String(eventId)
        const entry = abEventSpec[eid]
        if (!entry) return null
        return (
          <Link key={eid} to="/ab-event/$id" params={{ id: eid }}>
            <div className="w-40">
              <AbEventCard
                eventId={eid}
                hasImage={entry.hasImage}
                enableHoverHighlight
              />
            </div>
          </Link>
        )
      })}
    </div>
  )
}

// =============================================================================
// Main Page
// =============================================================================

function ThemePackDetailContent() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation('database')

  if (!id) {
    throw new Error('Theme Pack ID is required')
  }

  const { spec, i18n: themePackI18n } = useThemePackDetailData(id)
  const { spec: listSpec } = useThemePackListData()
  const listEntry = listSpec[id]
  const i18nEntry = themePackI18n[id]

  const leftColumn = (
    <div className="flex gap-4">
      {/* Theme Pack card image */}
      {listEntry && (
        <div className="shrink-0">
          <ThemePackCard
            packId={id}
            packEntry={listEntry}
            packName={i18nEntry?.name ?? id}
            specialName={i18nEntry?.specialName}
          />
        </div>
      )}

      {/* Difficulty + Floors */}
      <div className="flex-1 border rounded p-4 space-y-4">
        <div className="space-y-1">
          <div className="text-xs text-muted-foreground font-medium uppercase tracking-wide">
            {t('themePack.difficulty', 'Difficulty')}
          </div>
          <DifficultyBadges conditions={spec.exceptionConditions} />
        </div>
        <div className="space-y-1">
          <div className="text-xs text-muted-foreground font-medium uppercase tracking-wide">
            {t('themePack.floors', 'Floors')}
          </div>
          <FloorDisplay conditions={spec.exceptionConditions} />
        </div>
      </div>
    </div>
  )

  // Merge eventPool + specialEventPool for full event list
  const allEventPool = [
    ...spec.nodeOption.eventPool,
    ...(spec.nodeOption.specialEventPool ?? []),
  ]

  const rightColumn = (
    <div className="space-y-6">
      {/* Specific EGO Gifts — skip if empty */}
      {spec.specificEgoGiftPool.length > 0 && (
        <div className="space-y-3">
          <SectionTitle>{t('themePack.exclusiveGifts', 'Exclusive EGO Gifts')}</SectionTitle>
          <Suspense fallback={<Skeleton className="h-24 w-full" />}>
            <SpecificEgoGifts giftIds={spec.specificEgoGiftPool} />
          </Suspense>
        </div>
      )}

      {/* Exclusive Events — rendered only if non-empty (checked inside component) */}
      <Suspense fallback={<Skeleton className="h-24 w-full" />}>
        <ExclusiveEventsSection eventPool={allEventPool} packId={id} />
      </Suspense>

      {/* All Acquirable EGO Gifts */}
      <div className="space-y-3">
        <SectionTitle>{t('themePack.allGifts', 'All Acquirable EGO Gifts')}</SectionTitle>
        <Suspense fallback={<Skeleton className="h-24 w-full" />}>
          <AllEgoGifts giftIds={spec.egoGiftPool} />
        </Suspense>
      </div>

      {/* All Encounterable Events */}
      {allEventPool.length > 0 && (
        <div className="space-y-3">
          <SectionTitle>{t('themePack.allEvents', 'All Encounterable Events')}</SectionTitle>
          <Suspense fallback={<Skeleton className="h-24 w-full" />}>
            <AllEvents eventPool={allEventPool} />
          </Suspense>
        </div>
      )}
    </div>
  )

  const mobileContent = rightColumn

  return (
    <DetailPageLayout
      leftColumn={leftColumn}
      rightColumn={rightColumn}
      mobileTabsContent={mobileContent}
    />
  )
}

export default function ThemePackDetailPage() {
  return (
    <Suspense fallback={<DetailPageSkeleton preset="themePack" />}>
      <ThemePackDetailContent />
    </Suspense>
  )
}
