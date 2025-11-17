import { useParams } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import mockData from '@/../../docs/02-id-browser/02-id-detail-page/01-UI-mock/data-mock.json'
import { IdentityHeader } from '@/components/identity/IdentityHeader'
import { StatusPanel } from '@/components/identity/StatusPanel'
import { ResistancePanel } from '@/components/identity/ResistancePanel'
import { StaggerPanel } from '@/components/identity/StaggerPanel'
import { TraitsDisplay } from '@/components/identity/TraitsDisplay'
import type { IdentityData, IdentityI18n } from '@/types/IdentityTypes'

type SkillSlot = 'skill1' | 'skill2' | 'skill3' | 'skillDef'

interface SkillData {
  basePower: number
  coinPower: number
  coinEA: string
  sin: string
  atkType: string
  atkWeight: number
  LV: number
  quantity: number
}

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
        const data = (await import(`@static/data/identity/${id}.json`)) as IdentityData
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
        const lang = i18n.language.toUpperCase()
        const data = (await import(`@static/i18n/${lang}/identity/${id}.json`)) as IdentityI18n
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

  // Use uptie4 for now
  const skills = identityData?.skills.uptie4 || mockData.skills.uptie4

  const getCurrentSkills = (): SkillData[] => {
    return (skills as any)[activeSkillSlot] || []
  }

  const renderCoinIcons = (coinEA: string) => {
    return coinEA.split('').map((coin, idx) => (
      <span key={idx} className="text-sm font-bold">
        {coin}
      </span>
    ))
  }

  const renderAttackWeight = (weight: number) => {
    return Array.from({ length: weight }, (_, i) => (
      <span key={i} className="inline-block w-3 h-3 bg-foreground mr-1">■</span>
    ))
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
              name={identityI18n.Name}
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
              {getCurrentSkills().map((skill, idx) => (
                <div key={idx} className="border rounded p-4 space-y-3">
                  {/* Skill Image Composite */}
                  <div className="relative bg-muted rounded-lg h-32 flex items-center justify-center">
                    <div className="text-muted-foreground">Skill Image</div>
                    <div className="absolute top-2 right-2 bg-background px-2 py-1 rounded text-sm font-bold">
                      +{skill.coinPower}
                    </div>
                    <div className="absolute top-2 left-2 bg-background px-2 py-1 rounded text-sm">
                      {skill.basePower}-{skill.basePower + skill.coinPower * skill.coinEA.length}
                    </div>
                    <div className="absolute bottom-2 left-2 bg-background px-2 py-1 rounded text-xs">
                      {skill.atkType}
                    </div>
                  </div>

                  {/* Coin Icons */}
                  <div className="flex gap-2">
                    {renderCoinIcons(skill.coinEA)}
                  </div>

                  {/* Skill Info Row */}
                  <div className="flex items-center gap-4 flex-wrap">
                    <div className="bg-muted px-3 py-1 rounded-full text-sm">
                      Skill Name
                    </div>
                    <div className="text-sm">
                      Offense {55 + skill.LV} (+{skill.LV})
                    </div>
                    <div className="text-sm flex items-center gap-1">
                      Attack Weight: {renderAttackWeight(skill.atkWeight)}
                    </div>
                  </div>

                  {/* Skill Description */}
                  <div className="space-y-2">
                    <div className="text-sm bg-muted/50 p-2 rounded">
                      Base skill description goes here
                    </div>
                    {skill.coinEA.split('').map((_, coinIdx) => (
                      <div key={coinIdx} className="flex gap-2">
                        <div className="bg-primary text-primary-foreground px-2 py-1 rounded text-xs font-semibold">
                          Coin {coinIdx + 1}
                        </div>
                        <div className="text-sm flex-1 bg-muted/30 p-2 rounded">
                          Coin effect description for coin {coinIdx + 1}
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Quantity indicator */}
                  {skill.quantity > 0 && (
                    <div className="text-xs text-muted-foreground">
                      Quantity: {skill.quantity}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* BOTTOM-RIGHT: Passive Skills Panel */}
          <div className="border rounded p-4 space-y-4">
            <div className="font-semibold">Passives</div>

            {/* Passive Section */}
            <div className="space-y-3">
              <div className="text-sm font-medium">Passive</div>
              {mockData.passive.map((passive, idx) => (
                <div key={idx} className="border rounded p-3 space-y-2">
                  <div className="bg-muted px-3 py-1 rounded-full text-sm inline-block">
                    Passive Name {idx + 1}
                  </div>
                  {passive.passiveSin && passive.passiveSin.length > 0 && (
                    <div className="text-xs">
                      {passive.passiveSin.map((sin, i) => (
                        <span key={i} className="mr-2">
                          {sin} x{passive.passsiveEA?.[i]} {passive.passiveType}
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
              {mockData.sptPassive.map((passive, idx) => (
                <div key={idx} className="border rounded p-3 space-y-2">
                  <div className="bg-muted px-3 py-1 rounded-full text-sm inline-block">
                    Support Passive Name
                  </div>
                  <div className="text-xs">
                    {passive.passiveSin?.map((sin, i) => (
                      <span key={i} className="mr-2">
                        {sin} x{passive.passsiveEA?.[i]} {passive.passiveType}
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
