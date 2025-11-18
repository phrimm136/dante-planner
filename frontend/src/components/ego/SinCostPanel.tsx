interface SinCostPanelProps {
  costs: number[]
}

const SINS = ['Wrath', 'Lust', 'Sloth', 'Gluttony', 'Gloom', 'Pride', 'Envy']

export function SinCostPanel({ costs }: SinCostPanelProps) {
  return (
    <div className="border rounded p-3 space-y-2">
      <div className="font-semibold text-sm text-center">Sin Cost</div>
      <div className="grid grid-cols-7 gap-1">
        {SINS.map((sin, index) => (
          <div key={sin} className="flex flex-col items-center gap-1">
            <img
              src={`/images/icon/sin/${sin}.webp`}
              alt={sin}
              className="w-6 h-6 object-contain"
            />
            <span className="text-xs">{costs[index] || 0}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
