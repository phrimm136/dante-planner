import { useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { IdentityHeader } from '@/components/identity/IdentityHeader'
import { StatusPanel } from '@/components/identity/StatusPanel'
import { ResistancePanel } from '@/components/identity/ResistancePanel'
import { StaggerPanel } from '@/components/identity/StaggerPanel'
import { TraitsDisplay } from '@/components/identity/TraitsDisplay'
import { SkillCard } from '@/components/identity/SkillCard'
import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import { useIdentityDetailData } from '@/hooks/useIdentityDetailData'
import type { Uptie } from '@/types/IdentityTypes'

type SkillSlot = 'skill1' | 'skill2' | 'skill3' | 'skillDef'

export default function IdentityDetailPage() {
  const { id } = useParams({ strict: false }) as { id: string }
  const [activeSkillSlot, setActiveSkillSlot] = useState<SkillSlot>('skill1')

  // Hooks must be called unconditionally - route should validate id exists
  const { spec: identityData, i18n: identityI18n } = useIdentityDetailData(id)

  // Current uptie level - hardcoded to 4 for now
  const uptieLevel: Uptie = 4

  // Get skill slot number for image paths
  const getSkillSlotNumber = (slot: SkillSlot): number => {
    switch (slot) {
      case 'skill1':
        return 1
      case 'skill2':
        return 2
      case 'skill3':
        return 3
      case 'skillDef':
        return 4
      default:
        return 1
    }
  }

  // Calculate HP at level 45 (or use a configurable level)
  const level = 45
  const calculatedHp = identityData.hp.defaultStat + identityData.hp.incrementByLevel * level

  // Get speed values at uptie level (0-indexed, so uptie 4 = index 3)
  const uptieIndex = uptieLevel - 1
  const minSpeed = identityData.minSpeedList[uptieIndex] ?? identityData.minSpeedList[0]
  const maxSpeed = identityData.maxSpeedList[uptieIndex] ?? identityData.maxSpeedList[0]

  const leftColumn = (
    <>
          {/* TOP-LEFT: Header Area */}
          <div className="space-y-4">
            {/* Header with rank, name, and image */}
            <IdentityHeader
              identityId={id}
              name={identityI18n.name}
              rank={identityData.rank}
            />

            {/* Three Horizontal Status Panels */}
            <div className="grid grid-cols-3 gap-2">
              <StatusPanel
                hp={calculatedHp}
                minSpeed={minSpeed}
                maxSpeed={maxSpeed}
                defense={identityData.defCorrection}
              />

              <ResistancePanel
                slash={identityData.ResistInfo.SLASH}
                pierce={identityData.ResistInfo.PENETRATE}
                blunt={identityData.ResistInfo.HIT}
              />

              <StaggerPanel maxHP={calculatedHp} staggerThresholds={identityData.staggerList} />
            </div>

            {/* Traits Panel */}
            <TraitsDisplay traits={identityData.unitKeywordList} />
          </div>

          {/* BOTTOM-LEFT: Sanity Panel */}
          <div className="border rounded p-4 space-y-4">
            <div className="font-semibold">Sanity</div>

            {/* Panic Type */}
            <div className="flex gap-3">
              <div className="w-8 h-8 bg-red-500 rounded shrink-0" />
              <div className="flex-1">
                <div className="font-medium text-sm">Panic Type</div>
                <div className="text-xs text-muted-foreground">
                  Type {identityData.panicType}
                </div>
              </div>
            </div>

            {/* Sanity Increment */}
            <div className="flex gap-3">
              <div className="w-8 h-8 bg-orange-500 rounded shrink-0" />
              <div className="flex-1">
                <div className="font-medium text-sm">Sanity Increment Condition</div>
                <div className="text-xs text-muted-foreground">
                  {identityData.mentalConditionInfo.add.join(', ') || 'None'}
                </div>
              </div>
            </div>

            {/* Sanity Decrement */}
            <div className="flex gap-3">
              <div className="w-8 h-8 bg-yellow-500 rounded shrink-0" />
              <div className="flex-1">
                <div className="font-medium text-sm">Sanity Decrement Condition</div>
                <div className="text-xs text-muted-foreground">
                  {identityData.mentalConditionInfo.min.join(', ') || 'None'}
                </div>
              </div>
            </div>
          </div>
    </>
  )

  const rightColumn = (
    <>
          {/* TOP-RIGHT: Skills Panel */}
          <div className="space-y-4">
            {/* Skill Selector */}
            <div className="flex gap-2">
              <button
                onClick={() => setActiveSkillSlot('skill1')}
                className={`flex-1 py-2 px-4 rounded ${
                  activeSkillSlot === 'skill1'
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted'
                }`}
              >
                Skill 1
              </button>
              <button
                onClick={() => setActiveSkillSlot('skill2')}
                className={`flex-1 py-2 px-4 rounded ${
                  activeSkillSlot === 'skill2'
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted'
                }`}
              >
                Skill 2
              </button>
              <button
                onClick={() => setActiveSkillSlot('skill3')}
                className={`flex-1 py-2 px-4 rounded ${
                  activeSkillSlot === 'skill3'
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted'
                }`}
              >
                Skill 3
              </button>
              <button
                onClick={() => setActiveSkillSlot('skillDef')}
                className={`flex-1 py-2 px-4 rounded ${
                  activeSkillSlot === 'skillDef'
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted'
                }`}
              >
                Defense
              </button>
            </div>

            {/* Skill Display - Show ALL skills in the selected slot */}
            <div className="space-y-4">
              {identityData.skills[activeSkillSlot].map((skill, idx) => {
                // Get skill i18n by skill ID
                const skillI18n = identityI18n.skills[String(skill.id)]

                return (
                  <SkillCard
                    key={idx}
                    identityId={id}
                    skillSlot={getSkillSlotNumber(activeSkillSlot)}
                    variantIndex={idx}
                    skillEntry={skill}
                    skillI18n={skillI18n}
                    uptie={uptieLevel}
                  />
                )
              })}
            </div>
          </div>

          {/* BOTTOM-RIGHT: Passive Skills Panel */}
          <div className="border rounded p-4 space-y-4">
            <div className="font-semibold">Passives</div>

            {/* Battle Passive Section */}
            <div className="space-y-3">
              <div className="text-sm font-medium">Battle Passives</div>
              {/* Get active passives for current uptie level */}
              {identityData.passives.battlePassiveList[uptieIndex]?.map((passiveId) => {
                const passiveI18n = identityI18n.passives[String(passiveId)]
                const condition = identityData.passives.conditions[String(passiveId)]

                return (
                  <div key={passiveId} className="border rounded p-3 space-y-2">
                    <div className="bg-muted px-3 py-1 rounded-full text-sm inline-block">
                      {passiveI18n?.name || `Passive ${passiveId}`}
                    </div>
                    {condition && (
                      <div className="text-xs">
                        {condition.type}: {Object.entries(condition.values).map(([key, val]) => `${key} x${val}`).join(', ')}
                      </div>
                    )}
                    <div className="text-sm text-muted-foreground">
                      {passiveI18n?.desc || 'Passive effect description'}
                    </div>
                  </div>
                )
              })}
              {(!identityData.passives.battlePassiveList[uptieIndex] || identityData.passives.battlePassiveList[uptieIndex].length === 0) && (
                <div className="text-sm text-muted-foreground">No battle passives at this uptie level</div>
              )}
            </div>

            {/* Support Passive Section */}
            <div className="space-y-3">
              <div className="text-sm font-medium">Support Passives</div>
              {identityData.passives.supportPassiveList[uptieIndex]?.map((passiveId) => {
                const passiveI18n = identityI18n.passives[String(passiveId)]
                const condition = identityData.passives.conditions[String(passiveId)]

                return (
                  <div key={passiveId} className="border rounded p-3 space-y-2">
                    <div className="bg-muted px-3 py-1 rounded-full text-sm inline-block">
                      {passiveI18n?.name || `Support Passive ${passiveId}`}
                    </div>
                    {condition && (
                      <div className="text-xs">
                        {condition.type}: {Object.entries(condition.values).map(([key, val]) => `${key} x${val}`).join(', ')}
                      </div>
                    )}
                    <div className="text-sm text-muted-foreground">
                      {passiveI18n?.desc || 'Support passive effect description'}
                    </div>
                  </div>
                )
              })}
              {(!identityData.passives.supportPassiveList[uptieIndex] || identityData.passives.supportPassiveList[uptieIndex].length === 0) && (
                <div className="text-sm text-muted-foreground">No support passives at this uptie level</div>
              )}
            </div>
          </div>
    </>
  )

  return <DetailPageLayout leftColumn={leftColumn} rightColumn={rightColumn} />
}
