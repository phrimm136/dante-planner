import { getKeywordIconPath } from '@/lib/assetPaths'
import { getKeywordDisplayName } from '@/lib/utils'

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

  const iconSize = 'h-6 bottom-0 right-0'

  return (
    <img
      src={getKeywordIconPath(keyword)}
      alt={getKeywordDisplayName(keyword)}
      className={`absolute ${iconSize} pointer-events-none`}
      onError={(e) => {
        e.currentTarget.style.display = 'none'
      }}
    />
  )
}
