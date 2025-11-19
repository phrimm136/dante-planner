import { useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { EGOHeader } from '@/components/ego/EGOHeader'
import { SinCostPanel } from '@/components/ego/SinCostPanel'
import { SinResistancePanel } from '@/components/ego/SinResistancePanel'
import { EGOSkillCard } from '@/components/ego/EGOSkillCard'
import { EGOPassiveDisplay } from '@/components/ego/EGOPassiveDisplay'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorState } from '@/components/common/ErrorState'
import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import { useEntityDetailData } from '@/hooks/useEntityDetailData'
import type { EGOData, EGOI18n } from '@/types/EGOTypes'

type SkillType = 'awakening' | 'corrosion'

export default function EGODetailPage() {
  const { id } = useParams({ strict: false })
  const [activeSkillType, setActiveSkillType] = useState<SkillType>('awakening')

  // Validate id exists before making queries
  if (!id) {
    return (
      <ErrorState
        title="Invalid URL"
        message="No EGO ID provided in the URL"
      />
    )
  }

  const { data: egoData, i18n: egoI18n, isPending, isError } =
    useEntityDetailData<EGOData, EGOI18n>('ego', id)

  if (isPending) {
    return <LoadingState />
  }

  if (isError || !egoData || !egoI18n) {
    return (
      <ErrorState
        title="EGO Not Found"
        message={`Could not load EGO data for ID: ${id}`}
      />
    )
  }

  // Current threadspin level - hardcoded to 4 for now
  const threadspinLevel: '3' | '4' = '4'

  const leftColumn = (
    <>
          {/* TOP-LEFT: Header Area */}
          <div className="space-y-4">
            {/* Header with rank, name, and image */}
            <EGOHeader
              egoId={id}
              name={egoI18n.name}
              rank={egoData.rank}
            />

            {/* Two Horizontal Panels: Sin Cost and Sin Resistance */}
            <div className="grid grid-cols-2 gap-2">
              <SinCostPanel costs={egoData.costs} />
              <SinResistancePanel resistances={egoData.resistances} />
            </div>
          </div>
    </>
  )

  const rightColumn = (
    <>
          {/* TOP-RIGHT: Skills Panel */}
          <div className="space-y-4">
            {/* Skill Type Selector */}
            <div className="flex gap-2">
              <button
                onClick={() => setActiveSkillType('awakening')}
                className={`flex-1 py-2 px-4 rounded ${
                  activeSkillType === 'awakening'
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted'
                }`}
              >
                Awakening
              </button>
              {egoData.skills.corrosion && (
                <button
                  onClick={() => setActiveSkillType('corrosion')}
                  className={`flex-1 py-2 px-4 rounded ${
                    activeSkillType === 'corrosion'
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-muted'
                  }`}
                >
                  Corrosion
                </button>
              )}
            </div>

            {/* Skill Display */}
            <div className="space-y-4">
              {activeSkillType === 'awakening' && egoData.skills.awakening && (
                <EGOSkillCard
                  egoId={id}
                  skillType="awakening"
                  skillData={egoData.skills.awakening}
                  skillI18n={egoI18n.skills.awakening}
                  sin={egoData.sin}
                  threadspin={threadspinLevel}
                />
              )}

              {activeSkillType === 'corrosion' && egoData.skills.corrosion && egoI18n.skills.corrosion && (
                <EGOSkillCard
                  egoId={id}
                  skillType="corrosion"
                  skillData={egoData.skills.corrosion}
                  skillI18n={egoI18n.skills.corrosion}
                  sin={egoData.sin}
                  threadspin={threadspinLevel}
                />
              )}
            </div>
          </div>

          {/* BOTTOM-RIGHT: Passive Panel */}
          <EGOPassiveDisplay passives={egoI18n.passive} />
    </>
  )

  return <DetailPageLayout leftColumn={leftColumn} rightColumn={rightColumn} />
}
