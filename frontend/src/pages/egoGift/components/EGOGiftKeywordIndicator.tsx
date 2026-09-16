import { getKeywordIconPath } from '@/shared/assets'
import { getKeywordDisplayName } from '@/lib/utils'
import { EGO_GIFT_CARD, pct } from '../lib/cardLayout'

interface EGOGiftKeywordIndicatorProps {
  keyword?: string | null
}

/**
 * Keyword indicator for EGO gift cards
 * Shows keyword icon in lower-right corner
 * Not displayed for null or "None" keywords
 */
export function EGOGiftKeywordIndicator({ keyword }: EGOGiftKeywordIndicatorProps) {
  if (!keyword || keyword === 'None') {
    return null
  }

  return (
    <img
      src={getKeywordIconPath(keyword)}
      alt={getKeywordDisplayName(keyword)}
      className="absolute bottom-0 right-0 pointer-events-none"
      style={{ height: pct(EGO_GIFT_CARD.keywordIcon) }}
      onError={(e) => {
        e.currentTarget.style.display = 'none'
      }}
    />
  )
}
