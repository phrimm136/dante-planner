import { calculateEnhancementCost } from '@/lib/egoGiftUtils'

import EnhancementPanel from './EnhancementPanel'

interface EnhancementLevelsProps {
  descs: string[]
  tier: string
}

export default function EnhancementLevels({ descs, tier }: EnhancementLevelsProps) {
  if (descs.length === 0) {
    return null
  }

  return (
    <div className="border rounded p-4 space-y-4">
      {descs.map((desc, index) => (
        <EnhancementPanel
          key={index}
          description={desc}
          level={index}
          cost={calculateEnhancementCost(tier, index)}
        />
      ))}
    </div>
  )
}
