interface ErrorStateProps {
  title: string
  message: string
}

/**
 * ErrorState - Reusable error state component
 *
 * Displays a centered error message with consistent styling
 */
export function ErrorState({ title, message }: ErrorStateProps) {
  return (
    <div className="container mx-auto p-8">
      <div className="bg-destructive/10 border border-destructive rounded-lg p-6 text-center">
        <h2 className="text-xl font-bold text-destructive mb-2">{title}</h2>
        <p className="text-muted-foreground">{message}</p>
      </div>
    </div>
  )
}
