export default function EnhancementPanel() {
  return (
    <div className="bg-gray-100 border rounded p-4 space-y-3">
      {/* TODO: Replace with actual enhancement data during data integration phase */}
      <div className="flex items-center gap-3">
        {/* Enhancement Level Icon */}
        <div className="w-12 h-12 bg-cyan-200 rounded flex items-center justify-center">
          <span className="font-bold">Level</span>
        </div>

        {/* Enhancement Cost */}
        <div className="flex items-center gap-2">
          <div className="w-6 h-6 bg-pink-200 rounded"></div>
          <span className="text-sm">Cost</span>
        </div>
      </div>

      {/* Enhancement Description */}
      <div className="bg-purple-100 rounded p-3">
        <p className="text-sm">Enhancement effect description</p>
      </div>
    </div>
  )
}
