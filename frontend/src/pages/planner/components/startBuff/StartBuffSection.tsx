import { useTranslation } from 'react-i18next'
import type { MDVersion } from '@/shared/gameData'
import { useStartBuffSelection } from '../../hooks/useStartBuffSelection'
import { EMPTY_STATE } from '@/lib/constants'
import { cn } from '@/lib/utils'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { StarlightCostDisplay } from '../StarlightCostDisplay'
import { StartBuffMiniCard } from './StartBuffMiniCard'

/** `useStartBuffSelection` demands a writer; this summary only ever displays. */
const IGNORE_SELECTION = () => {}

export interface StartBuffSectionProps {
  mdVersion: MDVersion
  selectedBuffIds: Set<number>
  /** Callback when section is clicked (opens edit pane) */
  onClick?: () => void
  readOnly?: boolean
  onViewNotes?: () => void
}

/**
 * Start buff section container with PlannerSection wrapper.
 * Displays mini cards for selected buffs in summary view.
 * Clicking the section opens the edit dialog.
 */
export function StartBuffSection({
  mdVersion,
  selectedBuffIds,
  onClick,
  readOnly = false,
  onViewNotes,
}: StartBuffSectionProps) {
  const { t } = useTranslation(['planner', 'common'])

  const { displayBuffs } = useStartBuffSelection(mdVersion, selectedBuffIds, IGNORE_SELECTION)

  // Filter to only show selected buffs
  const selectedBuffs = displayBuffs.filter((buff) => {
    const buffId = Number(buff.id)
    return selectedBuffIds.has(buffId)
  })

  // Calculate total star cost of selected buffs
  const totalCost = selectedBuffs.reduce((sum, buff) => sum + buff.cost, 0)

  const hasSelectedBuffs = selectedBuffs.length > 0

  return (
    <PlannerSection title={t('pages.plannerMD.startBuffs')} onViewNotes={onViewNotes}>
      {/* Star cost display */}
      <div className="flex justify-end mb-4">
        <StarlightCostDisplay cost={totalCost} size="lg" />
      </div>

      <button
        type="button"
        onClick={onClick}
        className={cn('w-full text-left', !readOnly && 'selectable cursor-pointer')}
      >
        {hasSelectedBuffs ? (
          <div className="flex flex-wrap gap-2 min-h-28">
            {selectedBuffs.map((buff) => (
              <StartBuffMiniCard
                key={buff.baseId}
                buffId={Number(buff.id)}
                displayName={buff.name}
                mdVersion={mdVersion}
              />
            ))}
          </div>
        ) : (
          <div
            className={cn(
              'flex items-center justify-center p-2 text-muted-foreground',
              EMPTY_STATE.MIN_HEIGHT,
              EMPTY_STATE.DASHED_BORDER,
            )}
          >
            {readOnly
              ? t('pages.plannerMD.emptyState.noStartBuffs')
              : t('pages.plannerMD.selectStartBuffs')}
          </div>
        )}
      </button>
    </PlannerSection>
  )
}

/** Props a store-bound caller supplies; the selection comes from the store. */
export type StoreBoundStartBuffSectionProps = Omit<StartBuffSectionProps, 'selectedBuffIds'>

/** Renders the section against the buff selection held by the planner editor store. */
export function StoreBoundStartBuffSection(props: StoreBoundStartBuffSectionProps) {
  const selectedBuffIds = usePlannerEditorStore((s) => s.selectedBuffIds)

  return <StartBuffSection {...props} selectedBuffIds={selectedBuffIds} />
}
