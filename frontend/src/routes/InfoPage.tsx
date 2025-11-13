import { Link } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'

export default function InfoPage() {
  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">In-Game Info</h1>
      <p className="text-muted-foreground mb-6">
        Placeholder page for game information (Identities, EGOs, EGO Gifts).
      </p>
      <Button asChild variant="outline">
        <Link to="/">Back to Home</Link>
      </Button>
    </div>
  )
}
