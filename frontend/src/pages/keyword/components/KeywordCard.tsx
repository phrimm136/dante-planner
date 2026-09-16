import { getBattleKeywordIconPath } from '@/shared/assets'
import { KEYWORD_GEOMETRY } from '../lib/cardLayout'

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
export const KeywordCard = function KeywordCard({
  id,
  iconId,
  enableHoverHighlight = false,
}: KeywordCardProps) {
  return (
    <div
      className={`relative flex items-center justify-center rounded-md border border-border bg-card${enableHoverHighlight ? ' selectable' : ''}`}
      style={{
        width: `${String(KEYWORD_GEOMETRY.size.widthPx)}px`,
        height: `${String(KEYWORD_GEOMETRY.size.widthPx)}px`,
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
}
