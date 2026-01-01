import { useTranslation } from 'react-i18next'
import type { MDVersion } from '@/hooks/useStartBuffData'
import { useStartBuffSelection } from '@/hooks/useStartBuffSelection'
import { PlannerSection } from '@/components/common/PlannerSection'
import { StartBuffMiniCard } from './StartBuffMiniCard'

interface StartBuffSectionProps {
  mdVersion: MDVersion
  selectedBuffIds: Set<number>
  onSelectionChange: (buffIds: Set<number>) => void
  /** Callback when section is clicked (opens edit pane) */
  onClick?: () => void
}

/**
 * Start buff section container with PlannerSection wrapper.
 * Displays mini cards for selected buffs in summary view.
 * Clicking the section opens the edit dialog.
 */
export function StartBuffSection({
  mdVersion,
  selectedBuffIds,
  onSelectionChange,
  onClick,
}: StartBuffSectionProps) {
  const { t } = useTranslation()
  const { displayBuffs } = useStartBuffSelection(mdVersion, selectedBuffIds, onSelectionChange)

  // Filter to only show selected buffs
  const selectedBuffs = displayBuffs.filter((buff) => {
    const buffId = Number(buff.id)
    return selectedBuffIds.has(buffId)
  })

  const hasSelectedBuffs = selectedBuffs.length > 0

  return (
    <PlannerSection title={t('pages.plannerMD.startBuffs')}>
      <div
        className="cursor-pointer hover:opacity-90 transition-opacity"
        onClick={onClick}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') onClick?.()
        }}
      >
        {hasSelectedBuffs ? (
          <div className="flex flex-wrap gap-2 p-2">
            {selectedBuffs.map((buff) => (
              <StartBuffMiniCard
                key={buff.baseId}
                buffId={Number(buff.id)}
                displayName={buff.name}
              />
            ))}
          </div>
        ) : (
          <div className="flex items-center justify-center h-24 text-muted-foreground">
            {t('pages.plannerMD.selectStartBuffs')}
          </div>
        )}
      </div>
    </PlannerSection>
  )
}
