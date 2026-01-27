import type { ErrorInfo, ReactNode } from 'react'
import { ErrorBoundary as ReactErrorBoundary } from 'react-error-boundary'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { NotFoundError } from '@/lib/api'
import NotFoundPage from '@/routes/NotFoundPage'

interface ErrorFallbackProps {
  error: Error
  resetErrorBoundary: () => void
}

/**
 * ErrorFallback - Fallback UI for error boundary
 *
 * Displays error message with reset button
 *
 * Dev mode: Shows detailed error message and stack trace for debugging
 * Production: Shows user-friendly generic message
 *
 * Special handling for NotFoundError: Shows dedicated "not found" page
 */
function ErrorFallback({ error, resetErrorBoundary }: ErrorFallbackProps) {
  const { t } = useTranslation()
  const isDev = import.meta.env.DEV

  // Special handling for 404 Not Found errors - use NotFoundPage component
  if (error instanceof NotFoundError) {
    return <NotFoundPage />
  }

  // In dev: show actual error for debugging
  // In production: show generic user-friendly message
  const errorMessage = isDev ? error.message : t('errors.generic.message')

  return (
    <div className="container mx-auto p-8 min-h-screen flex items-center justify-center">
      <div className="bg-destructive/10 border border-destructive rounded-lg p-6 text-center max-w-2xl">
        <h2 className="text-xl font-bold text-destructive mb-2">
          {t('errors.generic.title')}
        </h2>
        <p className="text-muted-foreground mb-4">{errorMessage}</p>
        {isDev && error.stack && (
          <details className="mt-4 text-left">
            <summary className="cursor-pointer text-sm font-semibold text-muted-foreground hover:text-foreground">
              {t('errors.stackTrace')}
            </summary>
            <pre className="mt-2 overflow-auto rounded bg-muted p-4 text-xs">
              {error.stack}
            </pre>
          </details>
        )}
        <Button onClick={resetErrorBoundary} variant="destructive" className="mt-4">
          {t('errors.generic.reset')}
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
