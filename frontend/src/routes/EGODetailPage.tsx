import { useParams } from '@tanstack/react-router'

export default function EGODetailPage() {
  const { id } = useParams({ strict: false })

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">EGO Detail</h1>
      <p className="text-muted-foreground">EGO ID: {id}</p>
      <p className="text-muted-foreground mt-4">Detail page coming soon...</p>
    </div>
  )
}
