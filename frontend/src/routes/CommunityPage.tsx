import { Link } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'

export default function CommunityPage() {
  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">Community</h1>
      <p className="text-muted-foreground mb-6">
        Placeholder page for community features (share plans, discuss strategies).
      </p>
      <Button asChild variant="outline">
        <Link to="/">Back to Home</Link>
      </Button>
    </div>
  )
}
