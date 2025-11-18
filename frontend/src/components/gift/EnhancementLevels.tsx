import EnhancementPanel from './EnhancementPanel'

interface EnhancementLevelsProps {
  descs: string[]
  tier: string
}

export default function EnhancementLevels({ descs, tier }: EnhancementLevelsProps) {
  if (descs.length === 0) {
    return null
  }

  // Calculate enhancement cost based on tier and level
  const getEnhancementCost = (level: number): number | null => {
    // No enhancement for level 0 or tier 5/EX
    if (level === 0 || tier === '5' || tier === 'EX') {
      return null
    }

    const baseCosts: Record<string, number> = {
      '1': 50,
      '2': 60,
      '3': 75,
      '4': 100,
    }

    const baseCost = baseCosts[tier]
    if (!baseCost) return null

    // Level 1 = base cost, Level 2 = double base cost
    return baseCost * level
  }

  return (
    <div className="border rounded p-4 space-y-4">
      {descs.map((desc, index) => (
        <EnhancementPanel
          key={index}
          description={desc}
          level={index}
          cost={getEnhancementCost(index)}
        />
      ))}
    </div>
  )
}
