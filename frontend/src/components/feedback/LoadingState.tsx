interface LoadingStateProps {
  message?: string
}

/**
 * LoadingState - Reusable loading state component
 *
 * Displays a centered loading message with consistent styling
 */
export function LoadingState({ message = 'Loading...' }: LoadingStateProps) {
  return (
    <div className="container mx-auto p-8">
      <div className="flex items-center justify-center h-64">
        <div className="text-muted-foreground">{message}</div>
      </div>
    </div>
  )
}
