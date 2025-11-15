import { getSinnerIconPath } from '@/lib/identityUtils'
import { Button } from '@/components/ui/button'

const SINNERS = [
  'yiSang',
  'faust',
  'donQuixote',
  'ryoShu',
  'meursault',
  'hongLu',
  'heathcliff',
  'ishmael',
  'rodion',
  'sinclair',
  'outis',
  'gregor',
] as const

interface IdentitySinnerFilterProps {
  selectedSinners: Set<string>
  onSelectionChange: (sinners: Set<string>) => void
}

export function IdentitySinnerFilter({
  selectedSinners,
  onSelectionChange,
}: IdentitySinnerFilterProps) {
  const toggleSinner = (sinner: string) => {
    const newSelection = new Set(selectedSinners)
    if (newSelection.has(sinner)) {
      newSelection.delete(sinner)
    } else {
      newSelection.add(sinner)
    }
    onSelectionChange(newSelection)
  }

  const clearAll = () => {
    onSelectionChange(new Set())
  }

  return (
    <div className="bg-card border border-border rounded-md p-2 h-14 flex items-center gap-2">
      {/* Clear All Button */}
      <Button
        variant="outline"
        size="icon-sm"
        onClick={clearAll}
        className="shrink-0"
        title="Clear all filters"
      >
        <span className="text-xs">×</span>
      </Button>

      {/* Scrollable Sinner Icons */}
      <div className="flex gap-2 overflow-x-auto">
        {SINNERS.map((sinner) => {
          const isSelected = selectedSinners.has(sinner)
          return (
            <button
              key={sinner}
              onClick={() => toggleSinner(sinner)}
              className={`shrink-0 w-8 h-8 rounded-md border-2 transition-all ${
                isSelected
                  ? 'border-primary bg-primary/10'
                  : 'border-border bg-button hover:border-primary/50'
              }`}
              title={sinner}
            >
              <img
                src={getSinnerIconPath(`[${sinner}]`)}
                alt={sinner}
                className="w-full h-full object-contain"
              />
            </button>
          )
        })}
      </div>
    </div>
  )
}
