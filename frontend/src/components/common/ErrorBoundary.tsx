import type { ErrorInfo, ReactNode } from 'react'
import { ErrorBoundary as ReactErrorBoundary } from 'react-error-boundary'
import { Button } from '@/components/ui/button'

interface ErrorFallbackProps {
  error: Error
  resetErrorBoundary: () => void
}

/**
 * ErrorFallback - Fallback UI for error boundary
 *
 * Displays error message with reset button
 */
function ErrorFallback({ error, resetErrorBoundary }: ErrorFallbackProps) {
  return (
    <div className="container mx-auto p-8 min-h-screen flex items-center justify-center">
      <div className="bg-destructive/10 border border-destructive rounded-lg p-6 text-center max-w-2xl">
        <h2 className="text-xl font-bold text-destructive mb-2">Something Went Wrong</h2>
        <p className="text-muted-foreground mb-4">{error.message}</p>
        <Button onClick={resetErrorBoundary} variant="destructive">
          Reset Application
        </Button>
      </div>
    </div>
  )
}

interface ErrorBoundaryProps {
  children: ReactNode
}

/**
 * ErrorBoundary - Wrapper component for react-error-boundary
 *
 * Catches unexpected render and logic errors in child components
 */
export function ErrorBoundary({ children }: ErrorBoundaryProps) {
  return (
    <ReactErrorBoundary
      FallbackComponent={ErrorFallback}
      onError={(error: Error, info: ErrorInfo) => {
        console.error('Error boundary caught error:', error, info)
      }}
      onReset={() => {
        // Reset any global state if needed
        window.location.href = '/'
      }}
    >
      {children}
    </ReactErrorBoundary>
  )
}
