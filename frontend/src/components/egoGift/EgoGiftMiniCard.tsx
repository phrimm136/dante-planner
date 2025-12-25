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

interface EgoGiftMiniCardProps {
  giftId: string
  giftName: string
  attributeType: string
  isSelected?: boolean
  isSelectable?: boolean
  onSelect?: (giftId: string) => void
}

/**
 * Compact EGO gift card for selection grids
 * Shows icon and name, with hover tooltip displaying colored name + description
 * Used for start gift selection, gift observation, and comprehensive selection
 */
export function EgoGiftMiniCard({
  giftId,
  giftName,
  attributeType,
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
          className={`
            relative w-24 cursor-pointer transition-all
            ${isSelected ? 'ring-2 ring-yellow-400 rounded-lg' : ''}
          `}
          onMouseEnter={() => setIsHovered(true)}
          onMouseLeave={() => setIsHovered(false)}
          onClick={handleClick}
        >
          {/* Gift icon */}
          <div className="w-24 h-24 flex items-center justify-center">
            <img
              src={getEGOGiftIconPath(giftId)}
              alt={giftName}
              className="w-full h-full object-contain"
            />
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
