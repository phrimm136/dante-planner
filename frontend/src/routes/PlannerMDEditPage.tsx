import { Link, useParams } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'

/**
 * Planner MD Edit Page - Edit an existing planner
 * TODO: Implement full edit functionality, reusing PlannerMDNewPage logic
 */
export default function PlannerMDEditPage() {
  const { id } = useParams({ from: '/planner/md/$id/edit' })

  return (
    <div className="container mx-auto py-6">
      <div className="space-y-6">
        <h1 className="text-2xl font-bold">Edit Planner</h1>
        <p className="text-muted-foreground">Planner ID: {id}</p>
        <p className="text-muted-foreground">
          This page is under construction.
        </p>
        <Button asChild variant="outline">
          <Link to="/planner/md">Back to Planner List</Link>
        </Button>
      </div>
    </div>
  )
}
