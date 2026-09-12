import { getAttributeColors } from '@/shared/gameData'
import type { EGOGiftAttributeType } from '@/shared/gameData'
import { Skeleton } from '@/components/ui/skeleton'
import { SECTION_STYLES } from '@/lib/constants'

interface GiftNameProps {
  attributeType: EGOGiftAttributeType
  name: string
}

export default function GiftName({ attributeType, name }: GiftNameProps) {
  const { primary: color } = getAttributeColors(attributeType)

  if (!name) {
    return <Skeleton className="h-8 w-32" style={{ backgroundColor: color }} />
  }

  return (
    <h1 className={SECTION_STYLES.TEXT.pageTitle} style={{ color }}>
      {name}
    </h1>
  )
}
