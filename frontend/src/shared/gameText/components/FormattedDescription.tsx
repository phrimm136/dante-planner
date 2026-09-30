import { Fragment } from 'react'
import { useKeywordFormatter } from '../hooks/useKeywordFormatter'
import { cn } from '@/lib/utils'
import { scanTmpRuns } from '../lib/tmpRichText'
import { renderTmpRun } from '../lib/unityRichText'
import { FormattedKeyword } from './FormattedKeyword'

interface FormattedDescriptionProps {
  text: string
  className?: string
}

export function FormattedDescription({ text, className }: FormattedDescriptionProps) {
  const { format } = useKeywordFormatter()

  if (!text) {
    return null
  }

  return (
    <span className={cn('inline', className)}>
      {scanTmpRuns(text).map((token, tokenIndex) => {
        if (token.kind === 'break') return <br key={tokenIndex} />

        const content = format(token.text).map((segment, segmentIndex) => {
          if (segment.type === 'keyword' && segment.keyword) {
            return <FormattedKeyword key={segmentIndex} keyword={segment.keyword} />
          }
          return <Fragment key={segmentIndex}>{segment.content}</Fragment>
        })

        return renderTmpRun(token, tokenIndex, content)
      })}
    </span>
  )
}
