import { LIST_CARD_HOVER_CLASS } from '@/lib/constants'
import { cn } from '@/lib/utils'
import { getBattleKeywordIconPath } from '@/shared/assets'
import { KEYWORD_GEOMETRY } from '../lib/cardLayout'

interface KeywordCardProps {
  id: string
  iconId: string | null
  enableHoverHighlight?: boolean
}

export const KeywordCard = function KeywordCard({
  id,
  iconId,
  enableHoverHighlight = false,
}: KeywordCardProps) {
  return (
    <div
      className={cn(
        'relative flex items-center justify-center rounded-md border border-border bg-card',
        enableHoverHighlight && LIST_CARD_HOVER_CLASS,
      )}
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
