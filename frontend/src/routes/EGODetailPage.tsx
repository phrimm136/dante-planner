import { useParams } from '@tanstack/react-router'
import { Suspense, useState } from 'react'
import { EGOHeader } from '@/components/ego/EGOHeader'
import { SinCostPanel } from '@/components/ego/SinCostPanel'
import { SinResistancePanel } from '@/components/ego/SinResistancePanel'
import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import { DetailPageSkeleton } from '@/components/common/DetailPageSkeleton'
import { FormattedDescription } from '@/components/common/FormattedDescription'
import { useEGODetailData } from '@/hooks/useEGODetailData'
import type { EGOSkillEntry } from '@/types/EGOTypes'

type SkillType = 'awaken' | 'erosion'

/**
 * Inner content component that uses Suspense-aware hooks
 */
function EGODetailContent() {
  const { id } = useParams({ strict: false })
  const [activeSkillType, setActiveSkillType] = useState<SkillType>('awaken')

  // Route validation - id must be defined
  if (!id) {
    throw new Error('EGO ID is required')
  }

  // Hooks must be called unconditionally - route should validate id exists
  const { spec: egoData, i18n: egoI18n } = useEGODetailData(id)

  // Current threadspin level - hardcoded to 4 for now (0-indexed: 3)
  const threadspinIndex = 3

  // Check if erosion skills exist
  const hasErosion = egoData.skills.erosion && egoData.skills.erosion.length > 0

  const leftColumn = (
    <>
          {/* TOP-LEFT: Header Area */}
          <div className="space-y-4">
            {/* Header with rank, name, and image */}
            <EGOHeader
              egoId={id}
              name={egoI18n.name}
              rank={egoData.egoType}
            />

            {/* Two Horizontal Panels: Sin Cost and Sin Resistance */}
            <div className="grid grid-cols-2 gap-2">
              <SinCostPanel costs={egoData.requirements} />
              <SinResistancePanel resistances={egoData.attributeResist} />
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
                onClick={() => { setActiveSkillType('awaken'); }}
                className={`flex-1 py-2 px-4 rounded ${
                  activeSkillType === 'awaken'
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted'
                }`}
              >
                Awakening
              </button>
              {hasErosion && (
                <button
                  onClick={() => { setActiveSkillType('erosion'); }}
                  className={`flex-1 py-2 px-4 rounded ${
                    activeSkillType === 'erosion'
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-muted'
                  }`}
                >
                  Erosion
                </button>
              )}
            </div>

            {/* Skill Display - TODO: Create EGOSkillCard that works with new types */}
            <div className="space-y-4">
              {egoData.skills[activeSkillType].map((skillEntry: EGOSkillEntry, idx: number) => {
                const skillI18n = egoI18n.skills[String(skillEntry.id)]
                const mergedSkillData = { ...skillEntry.skillData[0] }
                // Merge skill data up to current threadspin level
                for (let i = 1; i <= threadspinIndex; i++) {
                  Object.assign(mergedSkillData, skillEntry.skillData[i])
                }

                return (
                  <div key={idx} className="border rounded p-4 space-y-2">
                    <div className="font-semibold">{skillI18n?.name || `Skill ${skillEntry.id}`}</div>
                    <div className="text-sm text-muted-foreground">
                      {mergedSkillData.attributeType} | {mergedSkillData.atkType} | Target: {mergedSkillData.targetNum}
                    </div>
                    <div className="text-sm">
                      MP: {mergedSkillData.mpUsage} | Base: {mergedSkillData.defaultValue} | Scale: {mergedSkillData.scale}
                    </div>
                    {skillI18n?.descs[threadspinIndex]?.desc && (
                      <div className="text-sm text-muted-foreground mt-2">
                        {skillI18n.descs[threadspinIndex].desc}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </div>

          {/* BOTTOM-RIGHT: Passive Panel */}
          <div className="border rounded p-4 space-y-4">
            <div className="font-semibold">Passives</div>
            {/* Get active passives for current threadspin level */}
            {egoData.passives.passiveList[threadspinIndex]?.map((passiveId: string) => {
              const passiveI18n = egoI18n.passives[passiveId]
              return (
                <div key={passiveId} className="border rounded p-3 space-y-2">
                  <div className="bg-muted px-3 py-1 rounded-full text-sm inline-block">
                    {passiveI18n?.name || `Passive ${passiveId}`}
                  </div>
                  <div className="text-sm text-muted-foreground">
                    <FormattedDescription text={passiveI18n?.desc || ''} />
                  </div>
                </div>
              )
            })}
            {(!egoData.passives.passiveList[threadspinIndex] || egoData.passives.passiveList[threadspinIndex].length === 0) && (
              <div className="text-sm text-muted-foreground">No passives at this threadspin level</div>
            )}
          </div>
    </>
  )

  return <DetailPageLayout leftColumn={leftColumn} rightColumn={rightColumn} />
}

/**
 * EGODetailPage - EGO detail page with two-column layout
 *
 * Desktop: 4:6 ratio two-column grid
 * Mobile: Single column layout
 */
export default function EGODetailPage() {
  return (
    <Suspense fallback={<DetailPageSkeleton preset="ego" />}>
      <EGODetailContent />
    </Suspense>
  )
}
