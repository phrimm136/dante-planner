/**
 * Extraction Planner Page
 *
 * Main page for the extraction probability calculator.
 * Wraps content in ErrorBoundary + Suspense for proper error/loading handling.
 *
 * Route: /planner/extraction
 *
 * @see ExtractionCalculator.tsx for calculator logic
 * @see router.tsx for route registration
 */

import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { ErrorBoundary } from 'react-error-boundary'
import { ExtractionCalculator } from '@/components/extraction/ExtractionCalculator'

/**
 * Error fallback component for calculator errors
 */
function ErrorFallback({ error, resetErrorBoundary }: {
  error: Error
  resetErrorBoundary: () => void
}) {
  const { t } = useTranslation('common')

  return (
    <div className="bg-destructive/10 border border-destructive rounded-md p-6 text-center">
      <h2 className="text-lg font-semibold text-destructive mb-2">
        {t('errors.generic.title')}
      </h2>
      <p className="text-muted-foreground mb-4">{error.message}</p>
      <button
        onClick={resetErrorBoundary}
        className="px-4 py-2 bg-primary text-primary-foreground rounded-md hover:bg-primary/90"
      >
        {t('errors.generic.retry')}
      </button>
    </div>
  )
}

/**
 * Loading fallback for Suspense
 */
function LoadingFallback() {
  const { t } = useTranslation('common')

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      <div className="text-center text-muted-foreground py-8">
        {t('common:loading')}
      </div>
    </div>
  )
}

export default function ExtractionPlannerPage() {
  const { t } = useTranslation('extraction')

  return (
    <div className="container mx-auto p-8">
      {/* Page Header */}
      <h1 className="text-3xl font-bold mb-4">
        {t('page.title')}
      </h1>
      <p className="text-muted-foreground mb-6">
        {t('page.description')}
      </p>

      {/* Calculator Section */}
      <div className="bg-background rounded-lg p-6">
        <ErrorBoundary FallbackComponent={ErrorFallback}>
          <Suspense fallback={<LoadingFallback />}>
            <ExtractionCalculator />
          </Suspense>
        </ErrorBoundary>
      </div>
    </div>
  )
}
