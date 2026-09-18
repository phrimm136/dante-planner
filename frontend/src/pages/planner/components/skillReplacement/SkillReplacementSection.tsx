import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { SinnerSkillCard } from './SinnerSkillCard'
import { SkillExchangeModal } from './SkillExchangeModal'
import { useIdentityListSpec } from '@/pages/identity'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import { CardSlot } from '@/shared/cardLayout'
import { SINNERS, DEFAULT_SKILL_EA } from '@/shared/gameData'
import { SINNER_SKILL_GEOMETRY } from '../../lib/cardLayout'
import { useSkillReplacementLayout } from '../../hooks/useSkillReplacementLayout'
import type { IdentityId, OffensiveSkillSlot } from '@/shared/gameData'
import type { SinnerEquipment, SkillEAState, SkillInfo } from '../../types/DeckTypes'

export interface SkillReplacementSectionProps {
  equipment: Record<string, SinnerEquipment>
  plannedEAState: Record<string, SkillEAState>
  currentEAState?: Record<string, SkillEAState>
  setSkillEAState?: (state: Record<string, SkillEAState>) => void
  readOnly?: boolean
  onViewNotes?: () => void
}

export function SkillReplacementSection({
  equipment,
  plannedEAState,
  currentEAState,
  setSkillEAState,
  readOnly = false,
  onViewNotes,
}: SkillReplacementSectionProps) {
  const { t } = useTranslation(['planner', 'common'])

  const identitySpec = useIdentityListSpec()

  const [selectedSinner, setSelectedSinner] = useState<string | null>(null)

  const { gridStyle, mobileScale } = useSkillReplacementLayout()

  const getSkillInfos = (identityId: IdentityId): [SkillInfo, SkillInfo, SkillInfo] => {
    const spec = identitySpec[identityId]
    if (!spec) {
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

  const handleReset = (sinnerCode: string) => {
    setSkillEAState?.({
      ...plannedEAState,
      [sinnerCode]: { ...DEFAULT_SKILL_EA },
    })
  }

  const selectedSinnerEquipment = selectedSinner ? equipment[selectedSinner] : null
  const selectedIdentityId = selectedSinnerEquipment?.identity.id
  const selectedSinnerName = selectedSinner ? SINNERS[parseInt(selectedSinner, 10) - 1] : undefined

  return (
    <PlannerSection
      title={t('pages.plannerMD.skillReplacement.title')}
      {...(onViewNotes !== undefined && { onViewNotes })}
    >
      <div className="grid mx-auto" style={gridStyle}>
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

export type StoreBoundSkillReplacementSectionProps = Omit<
  SkillReplacementSectionProps,
  'equipment' | 'plannedEAState' | 'setSkillEAState'
>

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
