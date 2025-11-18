import EnhancementPanel from './EnhancementPanel'

export default function EnhancementLevels() {
  return (
    <div className="border rounded p-4 space-y-4">
      {/* TODO: Replace with actual enhancement data during data integration phase */}
      <div>
        <h3 className="text-lg font-semibold mb-3">Level 0</h3>
        <EnhancementPanel />
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-3">Level +1</h3>
        <EnhancementPanel />
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-3">Level +2</h3>
        <EnhancementPanel />
      </div>
    </div>
  )
}
