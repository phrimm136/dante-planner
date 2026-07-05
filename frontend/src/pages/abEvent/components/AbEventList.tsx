import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import type { AbEventSpecList } from '../schemas/AbEventSchemas'
import { CARD_GRID, PROGRESSIVE_REVEAL } from '@/lib/constants'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { matchesRelatedEgoGiftFilter, matchesRelatedThemePackFilter } from '../lib/abEventFilter'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { ScaledCardWrapper } from '@/components/layout/ScaledCardWrapper'
import { AbEventCardLink } from './AbEventCardLink'

interface AbEventListProps {
  spec: AbEventSpecList
  selectedEgoGifts: Set<string>
  selectedThemePacks: Set<string>
}

/**
 * AbEventList - Renders ab event cards with CSS-based filtering.
 *
 * All cards rendered once, visibility toggled via CSS class.
 * Filter logic: AND between filter types, OR within each type.
 */
export function AbEventList({ spec, selectedEgoGifts, selectedThemePacks }: AbEventListProps) {
  const { t } = useTranslation('database')

  const sortedEvents = useMemo(
    () => Object.entries(spec).sort(([a], [b]) => a.localeCompare(b)),
    [spec],
  )

  // Progressive rendering
  const displayCount = useProgressiveCount({
    total: sortedEvents.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
    resetKey: sortedEvents,
  })

  // CSS-based visibility filtering
  const visibleIds = useMemo(() => {
    const ids = new Set<string>()
    for (const [eventId, entry] of sortedEvents) {
      if (!matchesRelatedEgoGiftFilter(entry, selectedEgoGifts)) continue
      if (!matchesRelatedThemePackFilter(entry, selectedThemePacks)) continue
      ids.add(eventId)
    }
    return ids
  }, [sortedEvents, selectedEgoGifts, selectedThemePacks])

  if (visibleIds.size === 0) {
    return (
      <div className="bg-muted border border-border rounded-md p-6">
        <div className="text-center text-muted-foreground py-8">
          {t('abEvent.emptyState', 'No events found.')}
        </div>
      </div>
    )
  }

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.AB_EVENT} mobileScale={0.8}>
        {sortedEvents.slice(0, displayCount).map(([eventId, entry]) => (
          <ScaledCardWrapper
            key={eventId}
            mobileScale={0.8}
            cardWidth={CARD_GRID.WIDTH.AB_EVENT}
            cardHeight={CARD_GRID.HEIGHT.AB_EVENT}
            className={visibleIds.has(eventId) ? '' : 'hidden'}
          >
            <AbEventCardLink
              eventId={eventId}
              hasImage={entry.hasImage}
              illustId={entry.illustId}
            />
          </ScaledCardWrapper>
        ))}
      </ResponsiveCardGrid>
    </div>
  )
}
