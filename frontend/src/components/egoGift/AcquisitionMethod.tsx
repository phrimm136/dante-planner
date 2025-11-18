interface AcquisitionMethodProps {
  obtain: string
}

export default function AcquisitionMethod({ obtain }: AcquisitionMethodProps) {
  return (
    <div className="border rounded p-4">
      <h3 className="text-lg font-semibold mb-2">Acquisition Method</h3>
      <p className="text-sm text-gray-700">{obtain}</p>
    </div>
  )
}
