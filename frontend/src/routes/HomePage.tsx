import { Link } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import { useExampleQuery } from '@/hooks/useExampleQuery'

export default function HomePage() {
  const { data, isLoading, error } = useExampleQuery()

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-center space-y-6">
        <h1 className="text-4xl font-bold">Limbus Planner</h1>

        <div className="space-y-2">
          <p className="text-muted-foreground">
            TanStack Query and Router integrated!
          </p>

          {/* React Query Test */}
          <div className="p-4 border rounded-md">
            <h2 className="font-semibold mb-2">React Query Test:</h2>
            {isLoading && <p>Loading data...</p>}
            {error && <p className="text-destructive">Error: {error.message}</p>}
            {data && (
              <div>
                <p className="text-sm">{data.message}</p>
                <p className="text-xs text-muted-foreground">
                  Timestamp: {new Date(data.timestamp).toLocaleTimeString()}
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Navigation Test */}
        <div className="space-x-2">
          <Button asChild>
            <Link to="/about">Go to About</Link>
          </Button>
        </div>
      </div>
    </div>
  )
}
