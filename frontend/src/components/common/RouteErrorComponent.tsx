import type { ErrorComponentProps } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'

/**
 * RouteErrorComponent - Error component for TanStack Router
 *
 * Displayed when a route-level error occurs (e.g., data loading errors)
 * Different from ErrorBoundary which catches React render errors
 *
 * Dev mode: Shows detailed error message for debugging
 * Production: Shows user-friendly generic message
 */
export function RouteErrorComponent({ error, reset }: ErrorComponentProps) {
  const { t } = useTranslation()
  const isDev = import.meta.env.DEV

  // In dev: show actual error for debugging
  // In production: show generic user-friendly message
  const errorMessage = isDev
    ? error instanceof Error
      ? error.message
      : 'An unexpected error occurred'
    : t('errors.generic.message')

  return (
    <div className="container mx-auto p-8 min-h-screen flex items-center justify-center">
      <div className="bg-destructive/10 border border-destructive rounded-lg p-6 text-center max-w-2xl">
        <h2 className="text-xl font-bold text-destructive mb-2">
          {t('errors.generic.title')}
        </h2>
        <p className="text-muted-foreground mb-4">{errorMessage}</p>
        {isDev && error instanceof Error && error.stack && (
          <details className="mt-4 text-left">
            <summary className="cursor-pointer text-sm font-semibold text-muted-foreground hover:text-foreground">
              {t('errors.stackTrace')}
            </summary>
            <pre className="mt-2 overflow-auto rounded bg-muted p-4 text-xs">
              {error.stack}
            </pre>
          </details>
        )}
        <Button onClick={reset} variant="destructive" className="mt-4">
          {t('errors.generic.retry')}
        </Button>
      </div>
    </div>
  )
}
