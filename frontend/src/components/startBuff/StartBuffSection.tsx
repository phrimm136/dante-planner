import { useTranslation } from 'react-i18next'
import type { MDVersion } from '@/hooks/useStartBuffData'
import { useStartBuffSelection } from '@/hooks/useStartBuffSelection'
import { PlannerSection } from '@/components/common/PlannerSection'
import { StartBuffCard } from './StartBuffCard'

interface StartBuffSectionProps {
  mdVersion: MDVersion
  selectedBuffIds: Set<number>
  onSelectionChange: (buffIds: Set<number>) => void
  /** When true, displays read-only cards and makes section clickable */
  viewMode?: boolean
  /** Callback when section is clicked in view mode (opens edit pane) */
  onClick?: () => void
}

/**
 * Start buff section container with PlannerSection wrapper
 * In viewMode, the entire section is clickable to open edit pane
 */
export function StartBuffSection({
  mdVersion,
  selectedBuffIds,
  onSelectionChange,
  viewMode = false,
  onClick,
}: StartBuffSectionProps) {
  const { t } = useTranslation()
  const { buffs, i18n, battleKeywords, displayBuffs, handleSelect } =
    useStartBuffSelection(mdVersion, selectedBuffIds, onSelectionChange)

  const gridContent = (
    <div className="bg-muted w-full max-w-full overflow-x-auto scrollbar-hide">
      <div className="grid grid-cols-5 gap-1 min-w-max">
        {displayBuffs.map((buff) => {
          const currentBuffId = Number(buff.id)
          const isSelected = selectedBuffIds.has(currentBuffId)

          return (
            <StartBuffCard
              key={buff.baseId}
              buff={buff}
              allBuffs={buffs}
              i18n={i18n}
              battleKeywords={battleKeywords}
              isSelected={isSelected}
              onSelect={handleSelect}
              viewMode={viewMode}
            />
          )
        })}
      </div>
    </div>
  )

  return (
    <PlannerSection title={t('pages.plannerMD.startBuffs')}>
      {viewMode ? (
        <div
          className="cursor-pointer hover:opacity-90 transition-opacity"
          onClick={onClick}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') onClick?.(); }}
        >
          {gridContent}
        </div>
      ) : (
        gridContent
      )}
    </PlannerSection>
  )
}
