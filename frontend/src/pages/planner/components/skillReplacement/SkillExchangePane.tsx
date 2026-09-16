import type { CSSProperties } from 'react'
import { ArrowRight, RotateCcw } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { SkillImageSimple } from './SkillImageSimple'
import { getSkillImagePath } from '@/shared/assets'
import { aspectOf } from '@/shared/cardLayout'
import { SKILL_EXCHANGE_GEOMETRY } from '../../lib/cardLayout'
import { cn } from '@/lib/utils'
import type { SkillAttributeType, OffensiveSkillSlot } from '@/shared/gameData'
import { SKILL_EXCHANGE_CARD, cqw } from '../../lib/cardLayout'

const ROOT_STYLE: CSSProperties = {
  aspectRatio: aspectOf(SKILL_EXCHANGE_GEOMETRY.size),
  padding: cqw(SKILL_EXCHANGE_CARD.padding),
  gap: cqw(SKILL_EXCHANGE_CARD.gap),
  borderWidth: cqw(SKILL_EXCHANGE_CARD.border),
}

const SKILL_STYLE: CSSProperties = { width: cqw(SKILL_EXCHANGE_CARD.skill) }

const ARROW_STYLE: CSSProperties = {
  width: cqw(SKILL_EXCHANGE_CARD.arrowWidth),
  height: cqw(SKILL_EXCHANGE_CARD.arrowHeight),
}

interface SkillExchangePaneProps {
  identityId: string
  sourceSlot: OffensiveSkillSlot
  targetSlot: OffensiveSkillSlot
  sourceAttributeType: SkillAttributeType
  targetAttributeType: SkillAttributeType
  sourceAtkType?: string | undefined
  targetAtkType?: string | undefined
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
  const sourceId = identityId + (sourceSlot + 1).toString().padStart(2, '0')
  const targetId = identityId + (targetSlot + 1).toString().padStart(2, '0')
  const sourceImagePath = getSkillImagePath(identityId, sourceId)
  const targetImagePath = getSkillImagePath(identityId, targetId)

  return (
    <button
      onClick={onClick}
      disabled={isDisabled}
      className={cn(
        'w-full flex items-center justify-center rounded-lg border-solid',
        isDisabled
          ? 'border-muted bg-muted/50 opacity-50 cursor-not-allowed'
          : 'selectable border-border bg-card cursor-pointer',
      )}
      style={ROOT_STYLE}
    >
      {/* Source skill */}
      <div className="origin-center shrink-0" style={SKILL_STYLE}>
        <SkillImageSimple
          skillImagePath={sourceImagePath}
          attributeType={sourceAttributeType}
          skillTier={sourceSlot + 1}
          atkType={sourceAtkType}
        />
      </div>

      {/* Arrow */}
      <ArrowRight className="text-muted-foreground shrink-0" style={ARROW_STYLE} />

      {/* Target skill */}
      <div className="origin-center shrink-0" style={SKILL_STYLE}>
        <SkillImageSimple
          skillImagePath={targetImagePath}
          attributeType={targetAttributeType}
          skillTier={targetSlot + 1}
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
      <span className="text-sm font-medium">{t('pages.plannerMD.skillReplacement.reset')}</span>
    </button>
  )
}
