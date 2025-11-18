import type { EGOGift } from '@/types/EGOGiftTypes'

interface EGOGiftCardProps {
  gift: EGOGift
}

export function EGOGiftCard({ gift }: EGOGiftCardProps) {
  return (
    <div className="border rounded-lg p-4 hover:shadow-md transition-shadow bg-white">
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

      {/* Keywords */}
      {gift.keywords.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {gift.keywords.map((keyword, index) => (
            <span
              key={index}
              className="px-2 py-0.5 text-xs bg-blue-100 text-blue-800 rounded"
            >
              {keyword}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
