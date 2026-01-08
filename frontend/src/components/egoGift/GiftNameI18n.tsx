import { Suspense } from 'react'
import { useEGOGiftDetailI18n } from '@/hooks/useEGOGiftDetailData'
import GiftName from './GiftName'

import type { EGOGiftAttributeType } from '@/lib/constants'

interface GiftNameI18nProps {
  /** EGO Gift ID for i18n lookup */
  id: string
  /** Gift attribute type for color styling */
  attributeType: EGOGiftAttributeType
}

/**
 * Gift name content - suspends to fetch i18n
 */
function GiftNameContent({ id, attributeType }: GiftNameI18nProps) {
  // const i18n = useEGOGiftDetailI18n(id)
  return <GiftName attributeType={attributeType} name="" />
}

/**
 * Gift name with internal Suspense - does NOT suspend itself.
 * Pattern: PassiveCardI18n (internal Suspense boundary)
 *
 * @example
 * <GiftNameI18n id={id} attributeType={attributeType} />
 */
export function GiftNameI18n({ id, attributeType }: GiftNameI18nProps) {
  return (
    <Suspense fallback={<GiftName attributeType={attributeType} name="" />}>
      <GiftNameContent id={id} attributeType={attributeType} />
    </Suspense>
  )
}
