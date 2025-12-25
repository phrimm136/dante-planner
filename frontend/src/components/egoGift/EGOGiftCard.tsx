import { Link } from '@tanstack/react-router'
import {
  getStatusEffectIconPath,
  getEGOGiftIconPath,
  getEGOGiftGradeIconPath,
} from '@/lib/assetPaths'
import { getKeywordDisplayName } from '@/lib/utils'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'

interface EGOGiftCardProps {
  gift: EGOGiftListItem
}

export function EGOGiftCard({ gift }: EGOGiftCardProps) {
  const { id } = gift

  // Extract tier from tag array
  const tier = gift.tag.find(t => t.startsWith('TIER_'))?.replace('TIER_', '') || null

  return (
    <Link
      to="/ego-gift/$id"
      params={{ id }}
      className="block w-32 relative border rounded-lg hover:shadow-md transition-shadow"
    >
      {/* Grade Icon - Upper-left */}
      {tier && (
        <img
          src={getEGOGiftGradeIconPath(tier)}
          alt={`Grade ${tier}`}
          className="absolute -top-2 -left-2 w-10 h-10 pointer-events-none"
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

      {/* Keyword - Icon only in lower-right corner */}
      {gift.keyword && (
        <div className="absolute bottom-2 right-2">
          <img
            src={getStatusEffectIconPath(gift.keyword)}
            alt={getKeywordDisplayName(gift.keyword)}
            title={getKeywordDisplayName(gift.keyword)}
            className="w-6 h-6 pointer-events-none"
            onError={(e) => {
              // Text fallback for missing icons
              const target = e.currentTarget
              const span = document.createElement('span')
              span.textContent = gift.keyword || ''
              span.className = 'px-1 py-0.5 text-xs bg-blue-100 text-blue-800 rounded'
              span.title = gift.keyword || ''
              target.replaceWith(span)
            }}
          />
        </div>
      )}
    </Link>
  )
}
