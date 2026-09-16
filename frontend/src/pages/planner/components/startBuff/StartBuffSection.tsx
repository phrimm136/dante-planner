import { useTranslation } from 'react-i18next'
import type { MDVersion } from '@/shared/gameData'
import { useStartBuffSelection } from '../../hooks/useStartBuffSelection'
import { CARD_MOBILE_SCALE_NONE } from '@/lib/constants'
import { CardSlot, EGO_GIFT_GEOMETRY, useSlotSizePx } from '@/shared/cardLayout'
import { EmptyStatePlaceholder } from '@/components/feedback/EmptyStatePlaceholder'
import { GIFT_ROW_PADDING_PX, giftRowMinHeightPx } from '../../lib/cardLayout'
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

  const { heightPx: buffSlotHeightPx } = useSlotSizePx(
    EGO_GIFT_GEOMETRY.size,
    CARD_MOBILE_SCALE_NONE,
  )
  const minHeight = giftRowMinHeightPx(buffSlotHeightPx)

  // Filter to only show selected buffs
  const selectedBuffs = displayBuffs.filter((buff) => {
    const buffId = Number(buff.id)
    return selectedBuffIds.has(buffId)
  })

  // Calculate total star cost of selected buffs
  const totalCost = selectedBuffs.reduce((sum, buff) => sum + buff.cost, 0)

  const hasSelectedBuffs = selectedBuffs.length > 0

  return (
    <PlannerSection
      title={t('pages.plannerMD.startBuffs')}
      {...(onViewNotes !== undefined && { onViewNotes })}
    >
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
          <div className="flex flex-wrap gap-2" style={{ padding: GIFT_ROW_PADDING_PX, minHeight }}>
            {selectedBuffs.map((buff) => (
              <CardSlot
                key={buff.baseId}
                size={EGO_GIFT_GEOMETRY.size}
                mobileScale={CARD_MOBILE_SCALE_NONE}
              >
                <StartBuffMiniCard
                  buffId={Number(buff.id)}
                  displayName={buff.name}
                  mdVersion={mdVersion}
                />
              </CardSlot>
            ))}
          </div>
        ) : (
          <div className="flex" style={{ minHeight }}>
            <EmptyStatePlaceholder
              label={
                readOnly
                  ? t('pages.plannerMD.emptyState.noStartBuffs')
                  : t('pages.plannerMD.selectStartBuffs')
              }
              className="flex-1"
            />
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
