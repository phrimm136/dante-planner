import { useParams } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { EGOHeader } from '@/components/ego/EGOHeader'
import { SinCostPanel } from '@/components/ego/SinCostPanel'
import { SinResistancePanel } from '@/components/ego/SinResistancePanel'
import { EGOSkillCard } from '@/components/ego/EGOSkillCard'
import { EGOPassiveDisplay } from '@/components/ego/EGOPassiveDisplay'
import type { EGOData, EGOI18n } from '@/types/EGOTypes'

type SkillType = 'awakening' | 'corrosion'

export default function EGODetailPage() {
  const { id } = useParams({ strict: false })
  const { i18n } = useTranslation()
  const [activeSkillType, setActiveSkillType] = useState<SkillType>('awakening')
  const [egoData, setEgoData] = useState<EGOData | null>(null)
  const [egoI18n, setEgoI18n] = useState<EGOI18n | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  // Load EGO data using dynamic import
  useEffect(() => {
    if (!id) return

    setIsLoading(true)
    const loadData = async () => {
      try {
        // Dynamic import for EGO data
        const data = (await import(`@static/data/EGO/${id}.json`)).default as EGOData
        setEgoData(data)
      } catch (error) {
        console.error(`Failed to load EGO data for ${id}:`, error)
        setEgoData(null)
      }
    }
    loadData()
  }, [id])

  // Load EGO i18n
  useEffect(() => {
    if (!id) return

    const loadI18n = async () => {
      try {
        const lang = i18n.language
        const data = (await import(`@static/i18n/${lang}/EGO/${id}.json`)).default as EGOI18n
        setEgoI18n(data)
        setIsLoading(false)
      } catch (error) {
        console.error(`Failed to load EGO i18n for ${id}:`, error)
        setEgoI18n(null)
        setIsLoading(false)
      }
    }
    loadI18n()
  }, [id, i18n.language])

  if (isLoading) {
    return (
      <div className="container mx-auto p-8">
        <div className="flex items-center justify-center h-64">
          <div className="text-muted-foreground">Loading EGO data...</div>
        </div>
      </div>
    )
  }

  // Error fallback
  if (!egoData || !egoI18n) {
    return (
      <div className="container mx-auto p-8">
        <div className="bg-destructive/10 border border-destructive rounded-lg p-6 text-center">
          <h2 className="text-xl font-bold text-destructive mb-2">EGO Not Found</h2>
          <p className="text-muted-foreground">
            Could not load EGO data for ID: {id}
          </p>
        </div>
      </div>
    )
  }

  // Current threadspin level - hardcoded to 4 for now
  const threadspinLevel: '3' | '4' = '4'

  return (
    <div className="container mx-auto p-8">
      {/* Two-column layout: Left column (Header + Stats), Right column (Skills + Passives) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* LEFT COLUMN */}
        <div className="space-y-6">
          {/* TOP-LEFT: Header Area */}
          <div className="space-y-4">
            {/* Header with rank, name, and image */}
            <EGOHeader
              egoId={id!}
              name={egoI18n.name}
              rank={egoData.rank}
            />

            {/* Two Horizontal Panels: Sin Cost and Sin Resistance */}
            <div className="grid grid-cols-2 gap-2">
              <SinCostPanel costs={egoData.costs} />
              <SinResistancePanel resistances={egoData.resitances} />
            </div>
          </div>
        </div>

        {/* RIGHT COLUMN */}
        <div className="space-y-6">
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
                  egoId={id!}
                  skillType="awakening"
                  skillData={egoData.skills.awakening}
                  skillI18n={egoI18n.skills.awakening}
                  sin={egoData.sin}
                  threadspin={threadspinLevel}
                />
              )}

              {activeSkillType === 'corrosion' && egoData.skills.corrosion && egoI18n.skills.corrosion && (
                <EGOSkillCard
                  egoId={id!}
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
        </div>
      </div>
    </div>
  )
}
