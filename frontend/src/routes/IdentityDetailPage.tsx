import { useParams } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { IdentityHeader } from '@/components/identity/IdentityHeader'
import { StatusPanel } from '@/components/identity/StatusPanel'
import { ResistancePanel } from '@/components/identity/ResistancePanel'
import { StaggerPanel } from '@/components/identity/StaggerPanel'
import { TraitsDisplay } from '@/components/identity/TraitsDisplay'
import { SkillCard } from '@/components/identity/SkillCard'
import type { IdentityData, IdentityI18n, SkillData } from '@/types/IdentityTypes'

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
    return (
      <div className="container mx-auto p-8">
        <div className="flex items-center justify-center h-64">
          <div className="text-muted-foreground">Loading identity data...</div>
        </div>
      </div>
    )
  }

  // Error fallback SECOND
  if (!identityData || !identityI18n) {
    return (
      <div className="container mx-auto p-8">
        <div className="bg-destructive/10 border border-destructive rounded-lg p-6 text-center">
          <h2 className="text-xl font-bold text-destructive mb-2">Identity Not Found</h2>
          <p className="text-muted-foreground">
            Could not load identity data for ID: {id}
          </p>
        </div>
      </div>
    )
  }

  // Use uptie4 for now
  const skills = identityData!.skills.uptie4
  const skillsI18n = identityI18n!.skills.uptie4

  const getCurrentSkills = (): SkillData[] => {
    return skills[activeSkillSlot] || []
  }

  // Get skill i18n data based on active slot
  const getSkillI18n = (variantIndex: number) => {
    return skillsI18n[activeSkillSlot][variantIndex]
  }

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

  // Loading state
  if (isLoading) {
    return (
      <div className="container mx-auto p-8">
        <div className="flex items-center justify-center h-64">
          <div className="text-muted-foreground">Loading identity data...</div>
        </div>
      </div>
    )
  }

  // Error state
  if (!identityData || !identityI18n) {
    return (
      <div className="container mx-auto p-8">
        <div className="bg-destructive/10 border border-destructive rounded-lg p-6 text-center">
          <h2 className="text-xl font-bold text-destructive mb-2">Identity Not Found</h2>
          <p className="text-muted-foreground">
            Could not load identity data for ID: {id}
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto p-8">
      {/* Four-quadrant layout: Left column (Header + Sanity), Right column (Skills + Passives) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* LEFT COLUMN */}
        <div className="space-y-6">
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
        </div>

        {/* RIGHT COLUMN */}
        <div className="space-y-6">
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
              {getCurrentSkills().map((skill, idx) => {
                const skillI18nData = getSkillI18n(idx)
                const skillSlotNumber = getSkillSlotNumber(activeSkillSlot)

                return (
                  <SkillCard
                    key={idx}
                    identityId={id!}
                    skillSlot={skillSlotNumber}
                    variantIndex={idx}
                    skillData={skill}
                    skillI18n={skillI18nData}
                    skillEA={skill.quantity}
                    isUptie4={true}
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
                    Passive Name {idx + 1}
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
                    Passive effect description
                  </div>
                </div>
              ))}
            </div>

            {/* Support Passive Section */}
            <div className="space-y-3">
              <div className="text-sm font-medium">Support Passive</div>
              {identityData.sptPassive.map((passive, idx) => (
                <div key={idx} className="border rounded p-3 space-y-2">
                  <div className="bg-muted px-3 py-1 rounded-full text-sm inline-block">
                    Support Passive Name
                  </div>
                  <div className="text-xs">
                    {passive.passiveSin?.map((sin, i) => (
                      <span key={i} className="mr-2">
                        {sin} x{passive.passiveEA?.[i]} {passive.passiveType}
                      </span>
                    ))}
                  </div>
                  <div className="text-sm text-muted-foreground">
                    Support passive effect description
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
