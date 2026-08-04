import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import { PlannerSection } from '../PlannerSection'
import { SinnerSkillCard } from './SinnerSkillCard'
import { SkillExchangeModal } from './SkillExchangeModal'
import { useIdentityListData } from '@/pages/identity'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import { ScaledCardWrapper } from '@/components/layout/ScaledCardWrapper'
import { SINNERS, DEFAULT_SKILL_EA } from '@/shared/gameData'
import { CARD_GRID } from '@/lib/constants'
import type { OffensiveSkillSlot } from '@/shared/gameData'
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
  const { spec: identitySpec } = useIdentityListData()

  // Modal state
  const [selectedSinner, setSelectedSinner] = useState<string | null>(null)

  const isDesktop = useIsBreakpoint('min', CARD_GRID.LG_BREAKPOINT)
  const isSm = useIsBreakpoint('min', CARD_GRID.SM_BREAKPOINT)

  // Calculate scale and dimensions
  const mobileScale = CARD_GRID.MOBILE_SCALE.STANDARD
  const scale = isDesktop ? 1 : mobileScale
  const scaledWidth = CARD_GRID.WIDTH.SINNER_SKILL * scale
  const scaledHeight = CARD_GRID.HEIGHT.SINNER_SKILL * scale

  const getColumnCount = () => (isSm ? 6 : 3)

  const columnCount = getColumnCount()

  // Get skill infos for a sinner's equipped identity from spec data
  const getSkillInfos = (identityId: string): [SkillInfo, SkillInfo, SkillInfo] => {
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

  return (
    <PlannerSection title={t('pages.plannerMD.skillReplacement.title')} onViewNotes={onViewNotes}>
      {/* Sinner Grid - Responsive: 6->4->3->2 columns */}
      <div
        className="grid mx-auto gap-0.5"
        style={{
          gridTemplateColumns: `repeat(${columnCount}, ${scaledWidth}px)`,
          gridAutoRows: `${scaledHeight}px`,
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
            <ScaledCardWrapper
              key={sinnerCode}
              mobileScale={mobileScale}
              cardWidth={CARD_GRID.WIDTH.SINNER_SKILL}
              cardHeight={CARD_GRID.HEIGHT.SINNER_SKILL}
            >
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
            </ScaledCardWrapper>
          )
        })}
      </div>

      {/* Exchange Modal - Don't render when readOnly */}
      {!readOnly && selectedSinner && selectedIdentityId && (
        <SkillExchangeModal
          open={!!selectedSinner}
          onOpenChange={(open) => !open && setSelectedSinner(null)}
          sinnerName={SINNERS[parseInt(selectedSinner, 10) - 1]}
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
