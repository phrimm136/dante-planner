import { Suspense } from 'react'
import { useEGOGiftDetailI18n } from '../hooks/useEGOGiftDetailData'
import { AllEnhancementsPanel } from './AllEnhancementsPanel'
import type { EnhancementLevel } from '@/shared/gameData'

interface EnhancementsPanelI18nProps {
  giftId: string
  maxEnhancement: EnhancementLevel
  costs: (number | null)[]
}

function EnhancementsPanelContent({ giftId, maxEnhancement, costs }: EnhancementsPanelI18nProps) {
  const i18n = useEGOGiftDetailI18n(giftId)
  return (
    <AllEnhancementsPanel maxEnhancement={maxEnhancement} descriptions={i18n.descs} costs={costs} />
  )
}

export function EnhancementsPanelI18n({
  giftId,
  maxEnhancement,
  costs,
}: EnhancementsPanelI18nProps) {
  return (
    <Suspense
      fallback={
        <AllEnhancementsPanel maxEnhancement={maxEnhancement} descriptions={[]} costs={costs} />
      }
    >
      <EnhancementsPanelContent giftId={giftId} maxEnhancement={maxEnhancement} costs={costs} />
    </Suspense>
  )
}
