import { useParams } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { IdentityHeader } from '@/components/identity/IdentityHeader'
import { StatusPanel } from '@/components/identity/StatusPanel'
import { ResistancePanel } from '@/components/identity/ResistancePanel'
import { StaggerPanel } from '@/components/identity/StaggerPanel'
import { TraitsDisplay } from '@/components/identity/TraitsDisplay'
import { SkillCard } from '@/components/identity/SkillCard'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorState } from '@/components/common/ErrorState'
import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import type { IdentityData, IdentityI18n } from '@/types/IdentityTypes'

type SkillSlot = 'skill1' | 'skill2' | 'skill3' | 'skillDef'

export default function IdentityDetailPage() {
  const { id } = useParams({ strict: false })
  const { i18n } = useTranslation()
  const [activeSkillSlot, setActiveSkillSlot] = useState<SkillSlot>('skill1')
  const [identityData, setIdentityData] = useState<IdentityData | null>(null)
  const [identityI18n, setIdentityI18n] = useState<IdentityI18n | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  // Load identity data using dynamic import
  useEffect(() => {
    if (!id) return

    setIsLoading(true)
    const loadData = async () => {
      try {
        // Dynamic import for identity data
        const data = (await import(`@static/data/identity/${id}.json`)).default as IdentityData
        setIdentityData(data)
      } catch (error) {
        console.error(`Failed to load identity data for ${id}:`, error)
        setIdentityData(null)
      }
    }
    loadData()
  }, [id])

  // Load identity i18n
  useEffect(() => {
    if (!id) return

    const loadI18n = async () => {
      try {
        const lang = i18n.language
        const data = (await import(`@static/i18n/${lang}/identity/${id}.json`)).default as IdentityI18n
        setIdentityI18n(data)
        setIsLoading(false)
      } catch (error) {
        console.error(`Failed to load identity i18n for ${id}:`, error)
        setIdentityI18n(null)
        setIsLoading(false)
      }
    }
    loadI18n()
  }, [id, i18n.language])

  if (isLoading) {
    return <LoadingState message="Loading identity data..." />
  }

  if (!identityData || !identityI18n) {
    return (
      <ErrorState
        title="Identity Not Found"
        message={`Could not load identity data for ID: ${id}`}
      />
    )
  }

  // Current uptie level - hardcoded to 4 for now
  const uptieLevel: '3' | '4' = '4'

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

  const leftColumn = (
    <>
          {/* TOP-LEFT: Header Area */}
          <div className="space-y-4">
            {/* Header with grade, name, and image */}
            <IdentityHeader
              identityId={id!}
              name={identityI18n.name}
              grade={identityData.grade}
            />

            {/* Three Horizontal Status Panels */}
            <div className="grid grid-cols-3 gap-2">
              <StatusPanel
                hp={identityData.HP}
                minSpeed={identityData.minSpeed}
                maxSpeed={identityData.maxSpeed}
                defense={identityData.defLV}
              />

              <ResistancePanel
                slash={identityData.resist[0]}
                pierce={identityData.resist[1]}
                blunt={identityData.resist[2]}
              />

              <StaggerPanel maxHP={identityData.HP} staggerThresholds={identityData.stagger} />
            </div>

            {/* Traits Panel */}
            <TraitsDisplay traits={identityData.traits} />
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
                  Panic description goes here
                </div>
              </div>
            </div>

            {/* Sanity Increment */}
            <div className="flex gap-3">
              <div className="w-8 h-8 bg-orange-500 rounded shrink-0" />
              <div className="flex-1">
                <div className="font-medium text-sm">Sanity Increment Condition</div>
                <div className="text-xs text-muted-foreground">
                  Increment condition description
                </div>
              </div>
            </div>

            {/* Sanity Decrement */}
            <div className="flex gap-3">
              <div className="w-8 h-8 bg-yellow-500 rounded shrink-0" />
              <div className="flex-1">
                <div className="font-medium text-sm">Sanity Decrement Condition</div>
                <div className="text-xs text-muted-foreground">
                  Decrement condition description
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
                const skillData = skill
                const skillI18nData = identityI18n.skills[activeSkillSlot][idx]

                return (
                  <SkillCard
                    key={idx}
                    identityId={id!}
                    skillSlot={getSkillSlotNumber(activeSkillSlot)}
                    variantIndex={idx}
                    skillData={skillData}
                    skillI18nData={skillI18nData}
                    uptie={uptieLevel}
                  />
                )
              })}
            </div>
          </div>

          {/* BOTTOM-RIGHT: Passive Skills Panel */}
          <div className="border rounded p-4 space-y-4">
            <div className="font-semibold">Passives</div>

            {/* Passive Section */}
            <div className="space-y-3">
              <div className="text-sm font-medium">Passive</div>
              {identityData.passive.map((passive, idx) => (
                <div key={idx} className="border rounded p-3 space-y-2">
                  <div className="bg-muted px-3 py-1 rounded-full text-sm inline-block">
                    {identityI18n.passive[idx]?.name || `Passive ${idx + 1}`}
                  </div>
                  {passive.passiveSin && passive.passiveSin.length > 0 && (
                    <div className="text-xs">
                      {passive.passiveSin.map((sin, i) => (
                        <span key={i} className="mr-2">
                          {sin} x{passive.passiveEA?.[i]} {passive.passiveType}
                        </span>
                      ))}
                    </div>
                  )}
                  <div className="text-sm text-muted-foreground">
                    {identityI18n.passive[idx]?.desc || 'Passive effect description'}
                  </div>
                </div>
              ))}
            </div>

            {/* Support Passive Section */}
            <div className="space-y-3">
              <div className="text-sm font-medium">Support Passive</div>
              <div className="border rounded p-3 space-y-2">
                <div className="bg-muted px-3 py-1 rounded-full text-sm inline-block">
                  {identityI18n.sptPassive.name || 'Support Passive'}
                </div>
                {identityData.sptPassive.passiveSin && identityData.sptPassive.passiveSin.length > 0 && (
                  <div className="text-xs">
                    {identityData.sptPassive.passiveSin.map((sin, i) => (
                      <span key={i} className="mr-2">
                        {sin} x{identityData.sptPassive.passiveEA?.[i]} {identityData.sptPassive.passiveType}
                      </span>
                    ))}
                  </div>
                )}
                <div className="text-sm text-muted-foreground">
                  {identityI18n.sptPassive.desc || 'Support passive effect description'}
                </div>
              </div>
            </div>
          </div>
    </>
  )

  return <DetailPageLayout leftColumn={leftColumn} rightColumn={rightColumn} />
}
