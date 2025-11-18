import EnhancementPanel from './EnhancementPanel'

interface EnhancementLevelsProps {
  descs: string[]
}

export default function EnhancementLevels({ descs }: EnhancementLevelsProps) {
  if (descs.length === 0) {
    return null
  }

  const getLevelLabel = (index: number) => {
    if (index === 0) return 'Level 0'
    return `Level +${index}`
  }

  return (
    <div className="border rounded p-4 space-y-4">
      {descs.map((desc, index) => (
        <div key={index}>
          <h3 className="text-lg font-semibold mb-3">{getLevelLabel(index)}</h3>
          <EnhancementPanel description={desc} />
        </div>
      ))}
    </div>
  )
}
