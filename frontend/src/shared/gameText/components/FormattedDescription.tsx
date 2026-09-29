import { Fragment, type ReactNode } from 'react'
import { useKeywordFormatter } from '../hooks/useKeywordFormatter'
import { cn } from '@/lib/utils'
import { NAMED_STYLE_GRAMMAR, TEXT_STYLE, tokenizeRichText } from '../lib/richText'
import { FormattedKeyword } from './FormattedKeyword'
import { ACCENT_COLORS, ORANGE_MARK_STYLE } from '@/lib/constants'

const UPGRADE_HIGHLIGHT_COLOR = ACCENT_COLORS.ENHANCED

interface StyleSegment {
  content: string
  style: string | undefined
}

function parseStyleSegments(text: string): StyleSegment[] {
  return tokenizeRichText(text, NAMED_STYLE_GRAMMAR).flatMap<StyleSegment>((token) => {
    if (token.kind === 'element') return [{ content: token.content, style: token.value }]
    if (token.kind === 'text') return [{ content: token.value, style: undefined }]
    return []
  })
}

function wrapStyle(style: string | undefined, key: number, content: ReactNode[]): ReactNode {
  if (style === TEXT_STYLE.orangeMark) {
    return (
      <span key={key} data-text-style={style} style={ORANGE_MARK_STYLE}>
        {content}
      </span>
    )
  }
  return <Fragment key={key}>{content}</Fragment>
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
            const isHighlighted = styleSegment.style === TEXT_STYLE.upgradeHighlight

            const content = keywordSegments.map((segment, segmentIndex) => {
              if (segment.type === 'text') {
                if (isHighlighted) {
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

            return wrapStyle(styleSegment.style, styleIndex, content)
          })}
        </Fragment>
      ))}
    </span>
  )
}
