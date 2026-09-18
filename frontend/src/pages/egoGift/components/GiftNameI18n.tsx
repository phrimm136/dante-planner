import { Suspense } from 'react'
import { useEGOGiftDetailI18n } from '../hooks/useEGOGiftDetailData'
import GiftName from './GiftName'

import type { EGOGiftAttributeType } from '@/shared/gameData'

interface GiftNameI18nProps {
  id: string
  attributeType: EGOGiftAttributeType
}

function GiftNameContent({ id, attributeType }: GiftNameI18nProps) {
  const i18n = useEGOGiftDetailI18n(id)
  return <GiftName attributeType={attributeType} name={i18n.name} />
}

export function GiftNameI18n({ id, attributeType }: GiftNameI18nProps) {
  return (
    <Suspense fallback={<GiftName attributeType={attributeType} name="" />}>
      <GiftNameContent id={id} attributeType={attributeType} />
    </Suspense>
  )
}
