import { useState, useEffect } from 'react'
import { useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorState } from '@/components/common/ErrorState'
import GiftImage from '@/components/gift/GiftImage'
import GiftName from '@/components/gift/GiftName'
import CostDisplay from '@/components/gift/CostDisplay'
import EnhancementLevels from '@/components/gift/EnhancementLevels'
import AcquisitionMethod from '@/components/gift/AcquisitionMethod'
import type { EGOGiftSpec, EGOGiftI18n } from '@/types/EGOGiftTypes'

export default function EGOGiftDetailPage() {
  const { id } = useParams({ strict: false })
  const { i18n } = useTranslation()
  const [isLoading, setIsLoading] = useState(true)
  const [giftSpec, setGiftSpec] = useState<EGOGiftSpec | null>(null)
  const [giftI18n, setGiftI18n] = useState<EGOGiftI18n | null>(null)

  // Phase 1: Load spec data
  useEffect(() => {
    if (!id) return

    setIsLoading(true)
    const loadSpec = async () => {
      try {
        const specList = (await import('@static/data/EGOGiftSpecList.json')).default
        const spec = specList[id as keyof typeof specList]
        if (!spec) {
          console.error(`Gift spec not found for ID: ${id}`)
          setGiftSpec(null)
        } else {
          setGiftSpec(spec)
        }
      } catch (error) {
        console.error(`Failed to load gift spec for ${id}:`, error)
        setGiftSpec(null)
      }
    }
    loadSpec()
  }, [id])

  // Phase 2: Load i18n data
  useEffect(() => {
    if (!id) return

    const loadI18n = async () => {
      try {
        const lang = i18n.language
        const data = (await import(`@static/i18n/${lang}/gift/${id}.json`)).default as EGOGiftI18n
        setGiftI18n(data)
        setIsLoading(false)
      } catch (error) {
        console.error(`Failed to load gift i18n for ${id}:`, error)
        setGiftI18n(null)
        setIsLoading(false)
      }
    }
    loadI18n()
  }, [id, i18n.language])

  if (isLoading) {
    return <LoadingState />
  }

  if (!giftSpec || !giftI18n) {
    return (
      <ErrorState
        title="Gift Not Found"
        message={`Unable to load gift data for ID: ${id}`}
      />
    )
  }

  return (
    <div className="container mx-auto p-6">
      {/* Two-column grid layout */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left Column */}
        <div className="space-y-4">
          <GiftImage id={id!} />
          <GiftName name={giftI18n.name} />
          <CostDisplay cost={giftSpec.cost} />
          <AcquisitionMethod obtain={giftI18n.obtain} />
        </div>

        {/* Right Column */}
        <div>
          <EnhancementLevels descs={giftI18n.descs} />
        </div>
      </div>
    </div>
  )
}
