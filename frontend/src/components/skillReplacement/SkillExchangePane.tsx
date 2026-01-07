import { ArrowRight, RotateCcw } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { SkillImageSimple } from './SkillImageSimple'
import { getSkillImagePath } from '@/lib/assetPaths'
import { cn } from '@/lib/utils'
import type { SkillAttributeType, OffensiveSkillSlot } from '@/lib/constants'

interface SkillExchangePaneProps {
  identityId: string
  sourceSlot: OffensiveSkillSlot
  targetSlot: OffensiveSkillSlot
  sourceAttributeType: SkillAttributeType
  targetAttributeType: SkillAttributeType
  sourceAtkType?: string
  targetAtkType?: string
  sourceEA: number
  onClick: () => void
  disabled?: boolean
}

interface ResetPaneProps {
  onClick: () => void
}

/**
 * SkillExchangePane - Clickable exchange option showing source → target skill
 *
 * Displays source skill card → arrow → target skill card.
 * Disabled when source skill has 0 EA remaining.
 */
export function SkillExchangePane({
  identityId,
  sourceSlot,
  targetSlot,
  sourceAttributeType,
  targetAttributeType,
  sourceAtkType,
  targetAtkType,
  sourceEA,
  onClick,
  disabled = false,
}: SkillExchangePaneProps) {
  const isDisabled = disabled || sourceEA <= 0

  // Skill slots are 0-indexed, but paths use 1-indexed (slot 0 = skill01)
  const sourceImagePath = getSkillImagePath(identityId, sourceSlot + 1)
  const targetImagePath = getSkillImagePath(identityId, targetSlot + 1)

  return (
    <button
      onClick={onClick}
      disabled={isDisabled}
      className={cn(
        'flex items-center gap-2 p-2 rounded-lg border-2',
        isDisabled
          ? 'border-muted bg-muted/50 opacity-50 cursor-not-allowed'
          : 'selectable border-border bg-card cursor-pointer'
      )}
    >
      {/* Source skill (smaller size) */}
      <div className="scale-75 origin-center">
        <SkillImageSimple
          skillImagePath={sourceImagePath}
          attributeType={sourceAttributeType}
          skillSlot={sourceSlot + 1}
          atkType={sourceAtkType}
        />
      </div>

      {/* Arrow */}
      <ArrowRight className="w-6 h-6 text-muted-foreground shrink-0" />

      {/* Target skill (smaller size) */}
      <div className="scale-75 origin-center">
        <SkillImageSimple
          skillImagePath={targetImagePath}
          attributeType={targetAttributeType}
          skillSlot={targetSlot + 1}
          atkType={targetAtkType}
        />
      </div>
    </button>
  )
}

/**
 * ResetPane - Clickable reset button to restore EA to defaults
 */
export function ResetPane({ onClick }: ResetPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <button
      onClick={onClick}
      className="selectable flex items-center gap-2 px-4 py-3 rounded-lg border-2 border-border bg-card cursor-pointer"
    >
      <RotateCcw className="w-5 h-5 text-muted-foreground" />
      <span className="text-sm font-medium">
        {t('pages.plannerMD.skillReplacement.reset')}
      </span>
    </button>
  )
}
