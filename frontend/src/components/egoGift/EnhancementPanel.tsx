import { getEGOGiftEnhancementIconPath, getEGOGiftCoinIconPath } from '@/lib/assetPaths'
import { FormattedDescription } from '@/components/common/FormattedDescription'
import { ENHANCEMENT_LABELS, type EnhancementLevel } from '@/lib/constants'

interface EnhancementPanelProps {
  description: string
  level: number
  cost: number | null
}

export default function EnhancementPanel({ description, level, cost }: EnhancementPanelProps) {
  const enhancementLevel = level as EnhancementLevel

  return (
    <div className="border rounded-lg p-4 space-y-3">
      <div className="flex items-center gap-3">
        {/* Enhancement Level Icon - follows DetailEntitySelector pattern */}
        <div className="w-10 h-10 rounded flex items-center justify-center bg-muted">
          {level === 0 ? (
            <span className="text-sm font-medium">{ENHANCEMENT_LABELS[enhancementLevel]}</span>
          ) : (
            <img
              src={getEGOGiftEnhancementIconPath(level)}
              alt={`+${level}`}
              className="w-6 h-6 object-contain"
            />
          )}
        </div>

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

      {/* Enhancement Description - follows SkillCardLayout pattern */}
      <div className="text-sm">
        <FormattedDescription text={description} />
      </div>
    </div>
  )
}
