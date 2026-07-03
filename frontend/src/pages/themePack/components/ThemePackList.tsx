import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import type { ThemePackList as ThemePackListType } from '../types/ThemePackTypes'
import type { DungeonIdx, ThemePackFloor } from '@/shared/gameData'
import { CARD_GRID, PROGRESSIVE_REVEAL } from '@/lib/constants'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { useThemePackI18n } from '../hooks/useThemePackListData'
import {
  matchesDungeonDifficultyFilter,
  matchesFloorFilter,
  matchesEgoGiftFilter,
} from '../lib/themePackFilter'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { ScaledCardWrapper } from '@/components/layout/ScaledCardWrapper'
import { ThemePackCardLink } from './ThemePackCardLink'

interface ThemePackListProps {
  spec: ThemePackListType
  selectedDifficulties: Set<DungeonIdx>
  selectedFloors: Set<ThemePackFloor>
  selectedEgoGifts: Set<string>
  searchQuery: string
}

/**
 * ThemePackList - Renders theme pack cards with CSS-based filtering.
 *
 * All cards rendered once, visibility toggled via CSS class.
 * Filter logic: AND between filter types, OR within each type.
 */
export function ThemePackList({
  spec,
  selectedDifficulties,
  selectedFloors,
  selectedEgoGifts,
  searchQuery,
}: ThemePackListProps) {
  const { t } = useTranslation('database')
  const themePackI18n = useThemePackI18n()

  // Build sorted pack list
  const sortedPacks = useMemo(
    () => Object.entries(spec).sort(([a], [b]) => a.localeCompare(b)),
    [spec]
  )

  // Progressive rendering
  const displayCount = useProgressiveCount({
    total: sortedPacks.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
    resetKey: sortedPacks,
  })

  // CSS-based visibility filtering
  const visibleIds = useMemo(() => {
    const ids = new Set<string>()

    for (const [packId, entry] of sortedPacks) {
      if (!matchesDungeonDifficultyFilter(entry, selectedDifficulties)) continue
      if (!matchesFloorFilter(entry, selectedFloors)) continue
      if (!matchesEgoGiftFilter(entry, selectedEgoGifts)) continue

      // Search filter - match name (case-insensitive)
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()
        const packName = themePackI18n[packId]?.name ?? ''
        if (!packName.toLowerCase().includes(lowerQuery)) continue
      }

      ids.add(packId)
    }

    return ids
  }, [
    sortedPacks,
    selectedDifficulties,
    selectedFloors,
    selectedEgoGifts,
    searchQuery,
    themePackI18n,
  ])

  if (visibleIds.size === 0) {
    return (
      <div className="bg-muted border border-border rounded-md p-6">
        <div className="text-center text-muted-foreground py-8">
          {t('themePack.emptyState', 'No theme packs found.')}
        </div>
      </div>
    )
  }

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      <ResponsiveCardGrid
        cardWidth={CARD_GRID.WIDTH.THEME_PACK}
        mobileScale={0.8}
      >
        {sortedPacks.slice(0, displayCount).map(([packId, entry]) => {
          const i18nEntry = themePackI18n[packId]
          return (
            <ScaledCardWrapper
              key={packId}
              mobileScale={0.8}
              cardWidth={CARD_GRID.WIDTH.THEME_PACK}
              cardHeight={CARD_GRID.HEIGHT.THEME_PACK}
              className={visibleIds.has(packId) ? '' : 'hidden'}
            >
              <ThemePackCardLink
                packId={packId}
                packEntry={entry}
                packName={i18nEntry?.name ?? `Pack ${packId}`}
                specialName={i18nEntry?.specialName}
              />
            </ScaledCardWrapper>
          )
        })}
      </ResponsiveCardGrid>
    </div>
  )
}
