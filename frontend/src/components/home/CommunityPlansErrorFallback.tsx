import type { FallbackProps } from 'react-error-boundary'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'

/**
 * CommunityPlansErrorFallback - Custom error fallback for community plans section
 *
 * Displays thematic Faust connection error message from game story (E618B.json)
 * instead of generic error message.
 *
 * Pattern: ErrorBoundary.tsx ErrorFallback component
 */
export function CommunityPlansErrorFallback({
  resetErrorBoundary,
}: FallbackProps) {
  const { t } = useTranslation('common')

  return (
    <div className="bg-destructive/10 border border-destructive rounded-lg p-6 text-center">
      <h3 className="text-lg font-semibold text-destructive mb-2">
        {t('errors.communityPlans.title')}
      </h3>
      <p className="text-muted-foreground mb-4">
        {t('errors.communityPlans.connectionLost')}
      </p>
      <Button onClick={resetErrorBoundary} variant="destructive" size="sm">
        {t('errors.generic.retry')}
      </Button>
    </div>
  )
}
