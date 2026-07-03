import type { EGOGiftAttributeType } from '@/shared/gameData'
import { Skeleton } from "@/components/ui/skeleton"
import { useColorCodes } from "@/shared/gameText"

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
    <h1 className="text-2xl font-bold" style={{ color }}>
      {name}
    </h1>
  )
}
