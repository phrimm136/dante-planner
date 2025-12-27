import { useState } from 'react'
import { getEGOGiftIconPath } from '@/lib/assetPaths'
import { getColorForAttributeType, useColorCodes } from '@/hooks/useColorCodes'
import { useEgoGiftDescription } from '@/hooks/useEgoGiftDescription'
import { formatGiftDescription } from '@/lib/parseStyleTags'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { EGOGiftCardBackground } from './EGOGiftCardBackground'
import { EGOGiftTierIndicator } from './EGOGiftTierIndicator'
import { EGOGiftEnhancementIndicator } from './EGOGiftEnhancementIndicator'
import { EGOGiftKeywordIndicator } from './EGOGiftKeywordIndicator'

interface EgoGiftMiniCardProps {
  giftId: string
  giftName: string
  attributeType: string
  tier: string
  keyword?: string | null
  enhancement?: 0 | 1 | 2
  isSelected?: boolean
  isSelectable?: boolean
  onSelect?: (giftId: string) => void
}

/**
 * Compact EGO gift card for selection grids
 * Shows icon with background, tier, enhancement, and keyword indicators
 * Hover tooltip displays colored name + description
 * Used for start gift selection, gift observation, and comprehensive selection
 */
export function EgoGiftMiniCard({
  giftId,
  giftName,
  attributeType,
  tier,
  keyword,
  enhancement = 0,
  isSelected = false,
  isSelectable = true,
  onSelect,
}: EgoGiftMiniCardProps) {
  const [isHovered, setIsHovered] = useState(false)
  const { data: colorCodes } = useColorCodes()
  const { data: giftI18n, isPending: isLoadingDescription } = useEgoGiftDescription(
    giftId,
    isHovered
  )

  const nameColor = getColorForAttributeType(colorCodes, attributeType)

  const handleClick = () => {
    if (isSelectable && onSelect) {
      onSelect(giftId)
    }
  }

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div
          className="relative w-24 cursor-pointer transition-all"
          onMouseEnter={() => setIsHovered(true)}
          onMouseLeave={() => setIsHovered(false)}
          onClick={handleClick}
        >
          {/* Background and icon container */}
          <div className="relative w-24 h-24">
            {/* Background layers */}
            <EGOGiftCardBackground enhancement={enhancement} size="mini" />

            {/* Gift icon - centered */}
            <img
              src={getEGOGiftIconPath(giftId)}
              alt={giftName}
              className="absolute inset-0 w-full h-full object-contain"
              loading="lazy"
            />

            {/* Selection highlight overlay */}
            {isSelected && (
              <img
                src="/images/UI/egoGift/onSelect.webp"
                alt="Selected"
                className="absolute inset-0 w-full h-full object-contain pointer-events-none"
                loading="lazy"
              />
            )}

            {/* Tier Indicator - Upper-left */}
            <EGOGiftTierIndicator tier={tier} />

            {/* Enhancement Indicator - Upper-right */}
            <EGOGiftEnhancementIndicator enhancement={enhancement} />

            {/* Keyword Icon - Lower-right */}
            <EGOGiftKeywordIndicator keyword={keyword} />
          </div>
        </div>
      </TooltipTrigger>

      <TooltipContent
        side="bottom"
        className="max-w-xs bg-gray-900 border border-gray-700 p-3"
      >
        {/* Tooltip: Name with attribute color */}
        <p className="font-semibold mb-2" style={{ color: nameColor }}>
          {giftName}
        </p>

        {/* Tooltip: Description (base level only - descs[0]) */}
        {isLoadingDescription ? (
          <p className="text-gray-400 text-sm">Loading...</p>
        ) : giftI18n?.descs[0] ? (
          <div className="text-sm text-gray-200">
            {formatGiftDescription(giftI18n.descs[0])}
          </div>
        ) : (
          <p className="text-gray-400 text-sm">No description available</p>
        )}
      </TooltipContent>
    </Tooltip>
  )
}
