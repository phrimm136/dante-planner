import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { Skeleton } from '@/components/ui/skeleton'

import { AllTraitsI18n, TraitsI18n } from './TraitsI18n'

interface TraitsDisplayProps {
  traits: string[]
  table?: 'playable' | 'all'
}

export function TraitsDisplay({ traits, table = 'playable' }: TraitsDisplayProps) {
  const { t } = useTranslation(['database', 'common'])

  if (!traits || traits.length === 0) {
    return null
  }

  return (
    <LabeledPanel title={t('identity.unitKeyword')}>
      <Suspense fallback={<Skeleton className="h-6 w-full" />}>
        {table === 'all' ? <AllTraitsI18n traits={traits} /> : <TraitsI18n traits={traits} />}
      </Suspense>
    </LabeledPanel>
  )
}
