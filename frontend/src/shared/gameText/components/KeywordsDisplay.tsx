import { useTranslation } from 'react-i18next'

import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { KeywordString } from './KeywordString'

interface KeywordsDisplayProps {
  keywords: string[]
}

export function KeywordsDisplay({ keywords }: KeywordsDisplayProps) {
  const { t } = useTranslation('database')

  if (keywords.length === 0) {
    return null
  }

  return (
    <LabeledPanel title={t('meta.keywords')}>
      <div className="flex flex-wrap justify-start gap-x-1 gap-y-0.5 text-xs">
        {keywords.map((key) => (
          <KeywordString key={key} keyword={key} />
        ))}
      </div>
    </LabeledPanel>
  )
}
