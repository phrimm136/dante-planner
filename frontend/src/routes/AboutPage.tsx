import { Link } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import { useExampleQuery } from '@/hooks/useExampleQuery'

export default function AboutPage() {
  const { data, isLoading } = useExampleQuery()

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-center space-y-6">
        <h1 className="text-4xl font-bold">About Page</h1>

        <p className="text-muted-foreground">
          Testing TanStack Router navigation
        </p>

        {/* Verify query cache works across routes */}
        <div className="p-4 border rounded-md">
          <h2 className="font-semibold mb-2">Cached Query Data:</h2>
          {isLoading ? (
            <p>Loading...</p>
          ) : (
            <p className="text-sm">{data?.message}</p>
          )}
        </div>

        <div className="space-x-2">
          <Button asChild variant="outline">
            <Link to="/">Back to Home</Link>
          </Button>
        </div>
      </div>
    </div>
  )
}
