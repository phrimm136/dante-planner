import { useTranslation } from 'react-i18next'

import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { KeywordString } from './KeywordString'

interface KeywordsDisplayProps {
  /** Battle keyword keys to render as clickable chips */
  keywords: string[]
}

/**
 * Labeled detail-page panel of clickable battle keyword chips.
 * Each chip suspends independently via KeywordString, so the panel
 * label stays visible while keyword i18n loads.
 */
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
