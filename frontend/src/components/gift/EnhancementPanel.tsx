import { getEGOGiftEnhancementIconPath, getEGOGiftCoinIconPath } from '@/lib/assetPaths'

interface EnhancementPanelProps {
  description: string
  level: number
  cost: number | null
}

export default function EnhancementPanel({ description, level, cost }: EnhancementPanelProps) {
  return (
    <div className="bg-muted border rounded p-4 space-y-3">
      <div className="flex items-center gap-3">
        {/* Enhancement Level Icon */}
        <img
          src={getEGOGiftEnhancementIconPath(level)}
          alt={`Enhancement ${level}`}
          className="w-12 h-12"
        />

        {/* Enhancement Cost */}
        {cost !== null && (
          <div className="flex items-center gap-2">
            <img
              src={getEGOGiftCoinIconPath()}
              alt="Cost"
              className="w-6 h-6"
            />
            <span className="text-sm font-semibold">{cost}</span>
          </div>
        )}
      </div>

      {/* Enhancement Description */}
      <div className="bg-background rounded p-3">
        <p className="text-sm">{description}</p>
      </div>
    </div>
  )
}
