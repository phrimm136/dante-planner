import type { EGOGiftAttributeType } from "@/lib/constants"
import { useColorCodes } from "@/hooks/useColorCodes"

interface GiftNameProps {
  attributeType: EGOGiftAttributeType
  name: string
}


export default function GiftName({ attributeType, name }: GiftNameProps) {
  const { data: colorCodes } = useColorCodes()
  const color = colorCodes[attributeType]

  return (
    <h1 className="text-2xl font-bold" style={{ color }}>
      {name}
    </h1>
  )
}
