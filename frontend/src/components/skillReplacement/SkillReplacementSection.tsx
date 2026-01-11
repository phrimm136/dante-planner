import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/common/PlannerSection'
import { SinnerSkillCard } from './SinnerSkillCard'
import { SkillExchangeModal } from './SkillExchangeModal'
import { useIdentityListData } from '@/hooks/useIdentityListData'
import { SINNERS, DEFAULT_SKILL_EA } from '@/lib/constants'
import type { OffensiveSkillSlot } from '@/lib/constants'
import type { SinnerEquipment, SkillEAState, SkillInfo } from '@/types/DeckTypes'

interface SkillReplacementSectionProps {
  equipment: Record<string, SinnerEquipment>
  plannedEAState: Record<string, SkillEAState>
  currentEAState?: Record<string, SkillEAState>
  setSkillEAState: React.Dispatch<React.SetStateAction<Record<string, SkillEAState>>>
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
  const handleExchange = (sinnerName: string, sourceSlot: OffensiveSkillSlot, targetSlot: OffensiveSkillSlot) => {
    const ea = currentEAState ? currentEAState[sinnerName] : plannedEAState[sinnerName]
    const activeEA = ea || { ...DEFAULT_SKILL_EA }
    if (activeEA[sourceSlot] <= 0) return

    setSkillEAState((prev) => ({
      ...prev,
      [sinnerName]: {
        ...activeEA,
        [sourceSlot]: activeEA[sourceSlot] - 1,
        [targetSlot]: activeEA[targetSlot] + 1,
      },
    }))
  }

  // Handle reset: restore EA to defaults or plannedEAState
  const handleReset = (sinnerName: string) => {
    const resetValue = currentEAState ? plannedEAState[sinnerName] || { ...DEFAULT_SKILL_EA } : { ...DEFAULT_SKILL_EA }
    setSkillEAState((prev) => ({
      ...prev,
      [sinnerName]: resetValue,
    }))
  }

  // Get current modal data
  const selectedSinnerEquipment = selectedSinner ? equipment[selectedSinner] : null
  const selectedIdentityId = selectedSinnerEquipment?.identity.id

  return (
    <PlannerSection title={t('pages.plannerMD.skillReplacement.title')} onViewNotes={onViewNotes}>
      {/* Sinner Grid - Responsive: 6->4->3->2 columns */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
        {SINNERS.map((sinnerName) => {
          const sinnerEquipment = equipment[sinnerName]
          if (!sinnerEquipment) return null

          const identityId = sinnerEquipment.identity.id
          const identityData = identitySpec[identityId]
          const skillInfos = getSkillInfos(identityId)
          const planned = plannedEAState[sinnerName] || { ...DEFAULT_SKILL_EA }
          const current = currentEAState?.[sinnerName]

          return (
            <SinnerSkillCard
              key={sinnerName}
              identityId={identityId}
              uptie={sinnerEquipment.identity.uptie}
              rank={identityData?.rank ?? 1}
              skillInfos={skillInfos}
              skillEA={planned}
              currentEA={current}
              onClick={() => { setSelectedSinner(sinnerName); }}
              readOnly={readOnly}
            />
          )
        })}
      </div>

      {/* Exchange Modal - Don't render when readOnly */}
      {!readOnly && selectedSinner && selectedIdentityId && (
        <SkillExchangeModal
          open={!!selectedSinner}
          onOpenChange={(open) => !open && setSelectedSinner(null)}
          sinnerName={selectedSinner}
          identityId={selectedIdentityId}
          skillInfos={getSkillInfos(selectedIdentityId)}
          skillEA={plannedEAState[selectedSinner] || { ...DEFAULT_SKILL_EA }}
          currentEA={currentEAState?.[selectedSinner]}
          onExchange={(source, target) => { handleExchange(selectedSinner, source, target); }}
          onReset={() => { handleReset(selectedSinner); }}
        />
      )}
    </PlannerSection>
  )
}
