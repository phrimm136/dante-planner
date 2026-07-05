import { memo } from 'react'
import { getBattleKeywordIconPath } from '@/shared/assets'
import { CARD_GRID } from '@/lib/constants'

interface KeywordCardProps {
  id: string
  iconId: string | null
  enableHoverHighlight?: boolean
}

/**
 * Presentational card for keyword browser grid.
 * Renders keyword icon centered with selectable hover highlight.
 *
 * Pattern Source: EGOGiftCard.tsx
 * Memoized by id to prevent re-renders during list filtering.
 */
export const KeywordCard = memo(
  function KeywordCard({ id, iconId, enableHoverHighlight = false }: KeywordCardProps) {
    return (
      <div
        className={`relative flex items-center justify-center rounded-md border border-border bg-card${enableHoverHighlight ? ' selectable' : ''}`}
        style={{
          width: `${String(CARD_GRID.WIDTH.KEYWORD)}px`,
          height: `${String(CARD_GRID.WIDTH.KEYWORD)}px`,
        }}
      >
        <img
          src={getBattleKeywordIconPath(iconId ?? id)}
          alt={id}
          className="w-16 h-16"
          loading="lazy"
        />
      </div>
    )
  },
  (prev, next) =>
    prev.id === next.id &&
    prev.iconId === next.iconId &&
    prev.enableHoverHighlight === next.enableHoverHighlight,
)
