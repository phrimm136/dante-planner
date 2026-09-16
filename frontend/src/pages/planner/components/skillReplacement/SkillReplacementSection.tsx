import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { SinnerSkillCard } from './SinnerSkillCard'
import { SkillExchangeModal } from './SkillExchangeModal'
import { useIdentityListSpec } from '@/pages/identity'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import { CardSlot, useSlotSizePx } from '@/shared/cardLayout'
import { SINNERS, DEFAULT_SKILL_EA } from '@/shared/gameData'
import { CARD_MOBILE_SCALE, SM_BREAKPOINT_PX } from '@/lib/constants'
import { SINNER_SKILL_GEOMETRY } from '../../lib/cardLayout'
import { SKILL_REPLACEMENT_COLUMNS, SKILL_REPLACEMENT_GRID_GAP } from '../../lib/cardLayout'
import type { IdentityId, OffensiveSkillSlot } from '@/shared/gameData'
import type { SinnerEquipment, SkillEAState, SkillInfo } from '../../types/DeckTypes'

export interface SkillReplacementSectionProps {
  equipment: Record<string, SinnerEquipment>
  plannedEAState: Record<string, SkillEAState>
  /** Current EA state for tracker mode (shows difference from planned) */
  currentEAState?: Record<string, SkillEAState>
  /** Absent in read-only surfaces, which never open the exchange modal. */
  setSkillEAState?: (state: Record<string, SkillEAState>) => void
  readOnly?: boolean
  onViewNotes?: () => void
}

/**
 * SkillReplacementSection - Section for skill EA exchange
 *
 * Displays a 12-sinner responsive grid. Clicking a sinner opens the exchange modal.
 * Fetches identity data internally for skill attribute/attack type display.
 */
export function SkillReplacementSection({
  equipment,
  plannedEAState,
  currentEAState,
  setSkillEAState,
  readOnly = false,
  onViewNotes,
}: SkillReplacementSectionProps) {
  const { t } = useTranslation(['planner', 'common'])

  // Fetch identity data internally
  const identitySpec = useIdentityListSpec()

  // Modal state
  const [selectedSinner, setSelectedSinner] = useState<string | null>(null)

  const isSm = useIsBreakpoint('min', SM_BREAKPOINT_PX)

  const mobileScale = CARD_MOBILE_SCALE
  const { widthPx: columnWidth, heightPx: rowHeight } = useSlotSizePx(
    SINNER_SKILL_GEOMETRY.size,
    mobileScale,
  )

  const columnCount = isSm ? SKILL_REPLACEMENT_COLUMNS.wide : SKILL_REPLACEMENT_COLUMNS.narrow

  // Get skill infos for a sinner's equipped identity from spec data
  const getSkillInfos = (identityId: IdentityId): [SkillInfo, SkillInfo, SkillInfo] => {
    const spec = identitySpec[identityId]
    if (!spec) {
      // Default fallback for missing identity
      return [
        { attributeType: 'NEUTRAL' },
        { attributeType: 'NEUTRAL' },
        { attributeType: 'NEUTRAL' },
      ]
    }
    // spec.attributeType and spec.atkType are arrays for the 3 offensive skills
    return [
      { attributeType: spec.attributeType[0] || 'NEUTRAL', atkType: spec.atkType[0] },
      { attributeType: spec.attributeType[1] || 'NEUTRAL', atkType: spec.atkType[1] },
      { attributeType: spec.attributeType[2] || 'NEUTRAL', atkType: spec.atkType[2] },
    ]
  }

  // Handle exchange: transfer 1 EA from source to target
  const handleExchange = (
    sinnerCode: string,
    sourceSlot: OffensiveSkillSlot,
    targetSlot: OffensiveSkillSlot,
  ) => {
    const ea = currentEAState ? currentEAState[sinnerCode] : plannedEAState[sinnerCode]
    const activeEA = ea || { ...DEFAULT_SKILL_EA }
    if (activeEA[sourceSlot] <= 0) return

    setSkillEAState?.({
      ...plannedEAState,
      [sinnerCode]: {
        ...activeEA,
        [sourceSlot]: activeEA[sourceSlot] - 1,
        [targetSlot]: activeEA[targetSlot] + 1,
      },
    })
  }

  // Handle reset: restore EA to defaults (3/2/1)
  const handleReset = (sinnerCode: string) => {
    setSkillEAState?.({
      ...plannedEAState,
      [sinnerCode]: { ...DEFAULT_SKILL_EA },
    })
  }

  // Get current modal data (selectedSinner is now a sinner code)
  const selectedSinnerEquipment = selectedSinner ? equipment[selectedSinner] : null
  const selectedIdentityId = selectedSinnerEquipment?.identity.id
  const selectedSinnerName = selectedSinner ? SINNERS[parseInt(selectedSinner, 10) - 1] : undefined

  return (
    <PlannerSection
      title={t('pages.plannerMD.skillReplacement.title')}
      {...(onViewNotes !== undefined && { onViewNotes })}
    >
      {/* Sinner Grid - Responsive: 6->4->3->2 columns */}
      <div
        className="grid mx-auto"
        style={{
          gridTemplateColumns: `repeat(${String(columnCount)}, ${String(columnWidth)}px)`,
          gridAutoRows: `${String(rowHeight)}px`,
          gap: `${String(SKILL_REPLACEMENT_GRID_GAP)}px`,
          justifyContent: 'center',
        }}
      >
        {SINNERS.map((_, index) => {
          const sinnerCode = String(index + 1)
          const sinnerEquipment = equipment[sinnerCode]
          if (!sinnerEquipment) return null

          const identityId = sinnerEquipment.identity.id
          const identityData = identitySpec[identityId]
          const skillInfos = getSkillInfos(identityId)
          const planned = plannedEAState[sinnerCode] || { ...DEFAULT_SKILL_EA }
          const current = currentEAState?.[sinnerCode]

          return (
            <CardSlot key={sinnerCode} size={SINNER_SKILL_GEOMETRY.size} mobileScale={mobileScale}>
              <SinnerSkillCard
                identityId={identityId}
                uptie={sinnerEquipment.identity.uptie}
                rank={identityData?.rank ?? 1}
                skillInfos={skillInfos}
                skillEA={planned}
                currentEA={current}
                onClick={() => {
                  setSelectedSinner(sinnerCode)
                }}
                readOnly={readOnly}
              />
            </CardSlot>
          )
        })}
      </div>

      {/* Exchange Modal - Don't render when readOnly */}
      {!readOnly && selectedSinner && selectedIdentityId && selectedSinnerName && (
        <SkillExchangeModal
          open={!!selectedSinner}
          onOpenChange={(open) => !open && setSelectedSinner(null)}
          sinnerName={selectedSinnerName}
          identityId={selectedIdentityId}
          skillInfos={getSkillInfos(selectedIdentityId)}
          skillEA={plannedEAState[selectedSinner] || { ...DEFAULT_SKILL_EA }}
          currentEA={currentEAState?.[selectedSinner]}
          onExchange={(source, target) => {
            handleExchange(selectedSinner, source, target)
          }}
          onReset={() => {
            handleReset(selectedSinner)
          }}
        />
      )}
    </PlannerSection>
  )
}

/** Props a store-bound caller supplies; deck and planned EA come from the store. */
export type StoreBoundSkillReplacementSectionProps = Omit<
  SkillReplacementSectionProps,
  'equipment' | 'plannedEAState' | 'setSkillEAState'
>

/** Renders the section against the deck and skill EA held by the planner editor store. */
export function StoreBoundSkillReplacementSection(props: StoreBoundSkillReplacementSectionProps) {
  const equipment = usePlannerEditorStore((s) => s.equipment)
  const plannedEAState = usePlannerEditorStore((s) => s.skillEAState)
  const setSkillEAState = usePlannerEditorStore((s) => s.setSkillEAState)

  return (
    <SkillReplacementSection
      {...props}
      equipment={equipment}
      plannedEAState={plannedEAState}
      setSkillEAState={setSkillEAState}
    />
  )
}
