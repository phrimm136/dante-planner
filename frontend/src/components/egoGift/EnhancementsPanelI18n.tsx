import { Suspense } from 'react'
import { useEGOGiftDetailI18n } from '@/hooks/useEGOGiftDetailData'
import AllEnhancementsPanel from './AllEnhancementsPanel'
import type { EnhancementLevel } from '@/lib/constants'

interface EnhancementsPanelI18nProps {
  /** EGO Gift ID for i18n lookup */
  giftId: string
  /** Maximum enhancement level (0, 1, or 2) */
  maxEnhancement: EnhancementLevel
  /** Array of costs per level (null if enhancement not available for that level) */
  costs: (number | null)[]
}

/**
 * Enhancements content - suspends to fetch i18n
 */
function EnhancementsPanelContent({ giftId, maxEnhancement, costs }: EnhancementsPanelI18nProps) {
  const i18n = useEGOGiftDetailI18n(giftId)
  return <AllEnhancementsPanel maxEnhancement={maxEnhancement} descriptions={i18n.descs} costs={costs} />
}

/**
 * AllEnhancementsPanel with internal Suspense - does NOT suspend itself.
 * Pattern: PassiveCardI18n (internal Suspense boundary)
 *
 * @example
 * <EnhancementsPanelI18n giftId={id} maxEnhancement={2} costs={costs} />
 */
export function EnhancementsPanelI18n({ giftId, maxEnhancement, costs }: EnhancementsPanelI18nProps) {
  return (
    <Suspense fallback={<AllEnhancementsPanel maxEnhancement={maxEnhancement} descriptions={[]} costs={costs} />}>
      <EnhancementsPanelContent giftId={giftId} maxEnhancement={maxEnhancement} costs={costs} />
    </Suspense>
  )
}
