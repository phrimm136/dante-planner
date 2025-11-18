import { Link } from '@tanstack/react-router'
import {
  getStatusEffectIconPath,
  getEGOGiftIconPath,
  getEGOGiftGradeIconPath,
  getEGOGiftEnhancementIconPath,
} from '@/lib/assetPaths'
import { getKeywordDisplayName } from '@/lib/utils'
import type { EGOGift } from '@/types/EGOGiftTypes'

interface EGOGiftCardProps {
  gift: EGOGift
}

export function EGOGiftCard({ gift }: EGOGiftCardProps) {
  const { id } = gift

  return (
    <Link
      to="/ego-gift/$id"
      params={{ id }}
      className="block relative border rounded-lg p-3 hover:shadow-md transition-shadow bg-white"
    >
      {/* Grade Icon - Upper-left */}
      <img
        src={getEGOGiftGradeIconPath(gift.tier)}
        alt={`Grade ${gift.tier}`}
        className="absolute -top-2 -left-2 w-10 h-10 pointer-events-none"
      />

      {/* Enhancement Icon - Upper-right (only if enhanced) */}
      {gift.enhancement > 0 && (
        <img
          src={getEGOGiftEnhancementIconPath(gift.enhancement)}
          alt={`Enhancement +${gift.enhancement}`}
          className="absolute -top-2 -right-2 w-10 h-10 pointer-events-none"
        />
      )}

      {/* Main content - vertical layout */}
      <div className="flex flex-col items-center gap-2">
        {/* Gift Icon - 128x128px */}
        <div className="w-32 h-32 flex items-center justify-center">
          <img
            src={getEGOGiftIconPath(id)}
            alt={gift.name}
            className="w-full h-full object-contain"
          />
        </div>

        {/* Name below icon */}
        <h3 className="font-semibold text-sm text-center line-clamp-2 w-full">
          {gift.name}
        </h3>
      </div>

      {/* Category - Icon only in lower-right corner */}
      {gift.category && (
        <div className="absolute bottom-2 right-2">
          <img
            src={getStatusEffectIconPath(gift.category)}
            alt={getKeywordDisplayName(gift.category)}
            title={getKeywordDisplayName(gift.category)}
            className="w-6 h-6 pointer-events-none"
            onError={(e) => {
              // Text fallback for missing icons (e.g., Common category)
              const target = e.currentTarget
              const span = document.createElement('span')
              span.textContent = gift.category
              span.className = 'px-1 py-0.5 text-xs bg-blue-100 text-blue-800 rounded'
              span.title = gift.category
              target.replaceWith(span)
            }}
          />
        </div>
      )}
    </Link>
  )
}
