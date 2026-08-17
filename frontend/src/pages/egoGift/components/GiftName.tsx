import type { EGOGiftAttributeType } from '@/shared/gameData'
import { Skeleton } from '@/components/ui/skeleton'
import { useColorCodes } from '@/shared/gameText'
import { SECTION_STYLES } from '@/lib/constants'

interface GiftNameProps {
  attributeType: EGOGiftAttributeType
  name: string
}

export default function GiftName({ attributeType, name }: GiftNameProps) {
  const { data: colorCodes } = useColorCodes()
  const color = colorCodes[attributeType]

  if (!name) {
    return <Skeleton className="h-8 w-32" style={{ backgroundColor: color }} />
  }

  return (
    <h1 className={SECTION_STYLES.TEXT.pageTitle} style={{ color }}>
      {name}
    </h1>
  )
}
