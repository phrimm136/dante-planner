import { getStatusEffectIconPath } from '@/lib/assetPaths'
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

  const iconSize = 'w-6 h-6 bottom-1 right-1'

  return (
    <img
      src={getStatusEffectIconPath(keyword)}
      alt={getKeywordDisplayName(keyword)}
      title={getKeywordDisplayName(keyword)}
      className={`absolute ${iconSize} pointer-events-none`}
      onError={(e) => {
        e.currentTarget.style.display = 'none'
      }}
    />
  )
}
