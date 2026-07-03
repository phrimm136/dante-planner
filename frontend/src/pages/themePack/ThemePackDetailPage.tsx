/**
 * ThemePackDetailPage - Theme pack detail page with two-column layout
 *
 * Desktop: 4:6 ratio (left: image + metadata, right: gifts + events)
 * Mobile: Single column, left on top, right below
 */

import { useParams } from '@tanstack/react-router'
import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { DetailPageLayout } from '@/components/layout/DetailPageLayout'
import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { ThemePackCard } from '@/pages/themePack'
import { EGOGiftCard } from '@/pages/egoGift'
import { EGOGiftName } from '@/pages/egoGift'
import { Skeleton } from '@/components/ui/skeleton'
import { useThemePackDetailData } from '@/pages/themePack'
import { useThemePackListData } from '@/pages/themePack'
import { useEGOGiftListData } from '@/pages/egoGift'
import { AbEventCard, useAbEventListData } from '@/pages/abEvent'
import { getFeaturedBossImagePath } from '@/shared/assets'
import {
  DUNGEON_IDX,
  DIFFICULTY_COLORS,
  DIFFICULTY_LABELS,
  DUNGEON_FIXED_FLOOR_RANGE,
  THEME_PACK_FLOOR_LABELS,
} from '@/shared/gameData'
import type { DungeonIdx, ThemePackFloor, DifficultyLabel } from '@/shared/gameData'
import type { ThemePackDetail } from '@/pages/themePack'
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
          className="px-2 py-0.5 text-xs font-semibold rounded"
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
 * Normal/Hard read indexed floors from selectableFloors (mapped through
 * THEME_PACK_FLOOR_LABELS). Infinity/Extreme synthesize their absolute
 * 6-10F / 11-15F ranges from DUNGEON_FIXED_FLOOR_RANGE when no
 * selectableFloors are present.
 */
export function FloorDisplay({ conditions }: { conditions: ThemePackDetail['exceptionConditions'] }) {
  const FLOOR_DIFFICULTIES: { idx: DungeonIdx; label: DifficultyLabel }[] = [
    { idx: DUNGEON_IDX.NORMAL, label: DIFFICULTY_LABELS.NORMAL },
    { idx: DUNGEON_IDX.HARD, label: DIFFICULTY_LABELS.HARD },
    { idx: DUNGEON_IDX.PARALLEL, label: DIFFICULTY_LABELS.INFINITY_MIRROR },
    { idx: DUNGEON_IDX.EXTREME, label: DIFFICULTY_LABELS.EXTREME_MIRROR },
  ]

  const floorGroups = FLOOR_DIFFICULTIES
    .map((d) => {
      const conds = conditions.filter((c) => c.dungeonIdx === d.idx)
      if (conds.length === 0) return { ...d, floors: [] as string[] }

      const indexed = new Set<number>()
      for (const c of conds) {
        for (const f of c.selectableFloors ?? []) {
          indexed.add(f)
        }
      }

      if (indexed.size > 0) {
        const floors = Array.from(indexed)
          .sort((a, b) => a - b)
          .map((f) => THEME_PACK_FLOOR_LABELS[f as ThemePackFloor])
        return { ...d, floors }
      }

      const fixedRange = DUNGEON_FIXED_FLOOR_RANGE[d.idx]
      const floors = fixedRange ? fixedRange.map((n) => `${n}F`) : []
      return { ...d, floors }
    })
    .filter((g) => g.floors.length > 0)

  if (floorGroups.length === 0) return null

  return (
    <div className="space-y-2">
      {floorGroups.map((group) => (
        <div key={group.idx}>
          <div
            className="px-2 text-xs font-medium mb-1"
            style={{ color: DIFFICULTY_COLORS[group.label] }}
          >
            {group.label}
          </div>
          <div className="flex flex-wrap gap-1 pl-2">
            {group.floors.map((label) => (
              <span key={label} className="px-2 py-0.5 text-xs rounded">
                {label}
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
 * Featured boss panels — pre-composited webp per boss from the manifest.
 * Self-contained: renders nothing when the roster is empty.
 * Sources images only from manifest entries (never from showBossIds).
 */
export function FeaturedBoss({
  packId,
  bosses,
}: {
  packId: string
  bosses: ThemePackDetail['featuredBosses']
}) {
  const { t } = useTranslation('database')

  if (bosses.length === 0) return null

  return (
    <div className="space-y-3">
      <SectionTitle>{t('themePack.featuredBoss')}</SectionTitle>
      <div className="flex flex-wrap gap-3">
        {bosses.map((boss) => (
          <img
            key={boss.portraitId}
            src={getFeaturedBossImagePath(packId, boss.portraitId)}
            loading="lazy"
            alt=""
            className="w-24"
          />
        ))}
      </div>
    </div>
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
                  battleKeywordList: giftSpec.battleKeywordList ?? [],
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
 * Fixed reward EGO gifts for hidden theme packs
 */
function FixedRewardEgoGifts({ giftIds }: { giftIds: number[] }) {
  const { spec } = useEGOGiftListData()

  return (
    <div className="flex flex-wrap gap-3">
      {giftIds.map((giftId) => {
        const id = String(giftId)
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
                  battleKeywordList: giftSpec.battleKeywordList ?? [],
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
      <SectionTitle>{t('themePack.exclusiveEvents', 'Exclusive Dungeon Events')}</SectionTitle>
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
                  illustId={entry.illustId}
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
                battleKeywordList: giftSpec.battleKeywordList ?? [],
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
                illustId={entry.illustId}
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

      {/* Difficulty + Floors + Hidden Theme Rate */}
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
        {spec.hiddenThemeRate != null && (
          <div className="space-y-1">
            <div className="text-xs text-muted-foreground font-medium uppercase tracking-wide">
              {t('themePack.hiddenThemeRate')}
            </div>
            <span className="px-2 py-0.5 text-sm">
              {(spec.hiddenThemeRate * 100).toFixed(2)}%
            </span>
          </div>
        )}
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
      <FeaturedBoss packId={id} bosses={spec.featuredBosses} />

      {/* Fixed Reward EGO Gifts — hidden theme only */}
      {spec.fixedRewardEgoGifts && spec.fixedRewardEgoGifts.length > 0 && (
        <div className="space-y-3">
          <SectionTitle>{t('themePack.fixedRewards')}</SectionTitle>
          <Suspense fallback={<Skeleton className="h-24 w-full" />}>
            <FixedRewardEgoGifts giftIds={spec.fixedRewardEgoGifts} />
          </Suspense>
        </div>
      )}

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
