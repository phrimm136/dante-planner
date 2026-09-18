import { Fragment } from 'react'
import { useKeywordFormatter } from '../hooks/useKeywordFormatter'
import { cn } from '@/lib/utils'
import { UPGRADE_HIGHLIGHT_GRAMMAR, tokenizeRichText } from '../lib/richText'
import { FormattedKeyword } from './FormattedKeyword'
import { ACCENT_COLORS } from '@/lib/constants'

const UPGRADE_HIGHLIGHT_COLOR = ACCENT_COLORS.ENHANCED

interface StyleSegment {
  content: string
  isHighlighted: boolean
}

function parseStyleSegments(text: string): StyleSegment[] {
  return tokenizeRichText(text, UPGRADE_HIGHLIGHT_GRAMMAR).flatMap<StyleSegment>((token) => {
    if (token.kind === 'element') return [{ content: token.content, isHighlighted: true }]
    if (token.kind === 'text') return [{ content: token.value, isHighlighted: false }]
    return []
  })
}

interface FormattedDescriptionProps {
  text: string
  className?: string
}

export function FormattedDescription({ text, className }: FormattedDescriptionProps) {
  const { format } = useKeywordFormatter()

  if (!text) {
    return null
  }

  const lines = text.split('\n')

  return (
    <span className={cn('inline', className)}>
      {lines.map((line, lineIndex) => (
        <Fragment key={lineIndex}>
          {lineIndex > 0 && <br />}
          {parseStyleSegments(line).map((styleSegment, styleIndex) => {
            const keywordSegments = format(styleSegment.content)

            const content = keywordSegments.map((segment, segmentIndex) => {
              if (segment.type === 'text') {
                if (styleSegment.isHighlighted) {
                  return (
                    <span key={segmentIndex} style={{ color: UPGRADE_HIGHLIGHT_COLOR }}>
                      {segment.content}
                    </span>
                  )
                }
                return <Fragment key={segmentIndex}>{segment.content}</Fragment>
              }

              if (segment.keyword) {
                return <FormattedKeyword key={segmentIndex} keyword={segment.keyword} />
              }

              return <Fragment key={segmentIndex}>{segment.content}</Fragment>
            })

            return <Fragment key={styleIndex}>{content}</Fragment>
          })}
        </Fragment>
      ))}
    </span>
  )
}
