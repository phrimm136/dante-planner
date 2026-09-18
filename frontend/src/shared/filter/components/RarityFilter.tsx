import { IconFilter } from './IconFilter'
import { getRarityIconPath } from '@/shared/assets'

const RANKS = ['1', '2', '3'] as const

interface RarityFilterProps {
  selected: Set<number>
  onSelectionChange: (ranks: Set<number>) => void
}

export function RarityFilter({ selected, onSelectionChange }: RarityFilterProps) {
  const selectedAsStrings = new Set([...selected].map(String))

  const handleSelectionChange = (strSet: Set<string>) => {
    onSelectionChange(new Set([...strSet].map(Number)))
  }

  return (
    <IconFilter
      options={RANKS}
      selectedOptions={selectedAsStrings}
      onSelectionChange={handleSelectionChange}
      getIconPath={(rank: string) => getRarityIconPath(Number(rank))}
      flexIcons
    />
  )
}
