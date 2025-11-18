export default function CostDisplay() {
  return (
    <div className="border rounded p-4">
      {/* TODO: Replace with actual cost icon and value during data integration phase */}
      <div className="flex items-center justify-center gap-2">
        <div className="w-6 h-6 bg-gray-200 rounded"></div>
        <span className="text-lg font-semibold">Cost Value</span>
      </div>
    </div>
  )
}
