import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { Skeleton } from '@/components/ui/skeleton'

import { TraitsI18n } from './TraitsI18n'

interface TraitsDisplayProps {
  traits: string[]
}

/**
 * Wrapper component for trait badges with labeled container.
 * Uses TraitsI18n internally with Suspense for granular loading.
 */
export function TraitsDisplay({ traits }: TraitsDisplayProps) {
  const { t } = useTranslation(['database', 'common'])

  if (!traits || traits.length === 0) {
    return null
  }

  return (
    <div className="border rounded p-3">
      <div className="font-semibold text-sm mb-2 text-center">{t('identity.unitKeyword')}</div>
      <Suspense fallback={<Skeleton className="h-6 w-full" />}>
        <TraitsI18n traits={traits} />
      </Suspense>
    </div>
  )
}
