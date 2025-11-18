interface CostDisplayProps {
  cost: number
}

export default function CostDisplay({ cost }: CostDisplayProps) {
  return (
    <div className="border rounded p-4">
      <div className="flex items-center justify-center gap-2">
        <div className="w-6 h-6 bg-gray-200 rounded"></div>
        <span className="text-lg font-semibold">Cost: {cost}</span>
      </div>
    </div>
  )
}
