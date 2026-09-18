import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { ErrorBoundary as ReactErrorBoundary } from 'react-error-boundary'
import { ExtractionCalculator } from './components/ExtractionCalculator'
import { SECTION_STYLES } from '@/lib/constants'

function ErrorFallback({
  error,
  resetErrorBoundary,
}: {
  error: unknown
  resetErrorBoundary: (...args: unknown[]) => void
}) {
  const { t } = useTranslation('common')
  const errorMessage = error instanceof Error ? error.message : String(error)

  return (
    <div className="bg-destructive/10 border border-destructive rounded-md text-center">
      <h2 className="text-lg font-semibold text-destructive mb-2">{t('errors.generic.title')}</h2>
      <p className="text-muted-foreground mb-4">{errorMessage}</p>
      <button
        onClick={resetErrorBoundary}
        className="px-4 py-2 bg-primary text-primary-foreground rounded-md hover:bg-primary/90"
      >
        {t('errors.generic.retry')}
      </button>
    </div>
  )
}

function LoadingFallback() {
  const { t } = useTranslation('common')

  return (
    <div className="bg-muted border border-border rounded-md">
      <div className="text-center text-muted-foreground py-8">{t('common:loading')}</div>
    </div>
  )
}

export default function ExtractionPlannerPage() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <div className="bg-background rounded-lg">
        <ReactErrorBoundary FallbackComponent={ErrorFallback}>
          <Suspense fallback={<LoadingFallback />}>
            <ExtractionCalculator />
          </Suspense>
        </ReactErrorBoundary>
      </div>
    </div>
  )
}
