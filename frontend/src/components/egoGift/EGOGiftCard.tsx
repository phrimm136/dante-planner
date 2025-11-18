import { Link } from '@tanstack/react-router'
import { getStatusEffectIconPath } from '@/lib/assetPaths'
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
      className="block relative border rounded-lg p-4 hover:shadow-md transition-shadow bg-white"
    >
      {/* Tier Badge */}
      <div className="flex justify-between items-start mb-2">
        <span
          className={`px-2 py-1 text-xs font-bold rounded ${
            gift.tier === 'EX'
              ? 'bg-purple-500 text-white'
              : 'bg-gray-200 text-gray-800'
          }`}
        >
          {gift.tier}
        </span>
        <span className="text-sm text-gray-600">Cost: {gift.cost}</span>
      </div>

      {/* Name */}
      <h3 className="font-semibold text-lg mb-2 line-clamp-2">{gift.name}</h3>

      {/* Keywords - Icon only in lower-right corner */}
      {gift.keywords.length > 0 && (
        <div className="absolute bottom-2 right-2 flex gap-1">
          {gift.keywords.map((keyword, index) => {
            const displayName = getKeywordDisplayName(keyword)
            const iconPath = getStatusEffectIconPath(keyword)

            return (
              <img
                key={index}
                src={iconPath}
                alt={displayName}
                title={displayName}
                className="w-6 h-6"
                onError={(e) => {
                  // Text fallback for missing icons
                  const target = e.currentTarget
                  const span = document.createElement('span')
                  span.textContent = displayName
                  span.className = 'px-1 py-0.5 text-xs bg-blue-100 text-blue-800 rounded'
                  span.title = displayName
                  target.replaceWith(span)
                }}
              />
            )
          })}
        </div>
      )}
    </Link>
  )
}
