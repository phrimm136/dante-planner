import { Suspense, type CSSProperties } from 'react'

import { Skeleton } from '@/components/ui/skeleton'
import {
  lineMetrics,
  midlineOffsetEm,
  useFontAdvances,
  type FontTableRole,
} from '@/shared/cardLayout'
import type { TextElement } from '../../schemas/StartBuffCardSchemas'
import {
  fitElementText,
  lineSpacingEm,
  rectStyle,
  textTracking,
  unitsToCqw,
  type LayoutSpace,
} from '../../lib/startBuffCardLayout'

const ITEM_ALIGN = {
  left: 'flex-start',
  center: 'center',
  right: 'flex-end',
} as const

interface CardTextProps {
  text: string
  element: TextElement
  tableRole: FontTableRole
  language: string
  space: LayoutSpace
  color: string
  fontFamily: string | undefined
  testId: string
}

export function CardText(props: CardTextProps) {
  return (
    <Suspense
      fallback={
        <Skeleton
          style={{ ...rectStyle(props.element.rect, props.space), pointerEvents: 'none' }}
        />
      }
    >
      <CardTextBody {...props} />
    </Suspense>
  )
}

function CardTextBody({
  text,
  element,
  tableRole,
  language,
  space,
  color,
  fontFamily,
  testId,
}: CardTextProps) {
  const table = useFontAdvances(language, tableRole)
  const node = element.text
  const fitted = fitElementText(text, element, table)
  const tracking = textTracking(node)
  const geometry = node.vAlign === 'geometry'

  const boxStyle: CSSProperties = {
    ...rectStyle(element.rect, space),
    display: 'flex',
    flexDirection: 'column',
    justifyContent: geometry ? 'center' : 'flex-start',
    alignItems: ITEM_ALIGN[node.hAlign],
    textAlign: node.hAlign,
    pointerEvents: 'none',
    ...(geometry && {
      transform: `translateY(${unitsToCqw(midlineOffsetEm(table) * fitted.fontSize, space)})`,
    }),
  }

  return (
    <div data-testid={testId} style={boxStyle}>
      {fitted.lines.map((line, index) => (
        <span
          key={`${String(index)}:${line}`}
          data-testid={`${testId}-line`}
          style={{
            fontFamily,
            fontSize: unitsToCqw(fitted.fontSize, space),
            lineHeight: lineMetrics(table, line).lineHeight + lineSpacingEm(node),
            letterSpacing: `${String(tracking.letterSpacingEm)}em`,
            wordSpacing: `${String(tracking.wordSpacingEm)}em`,
            whiteSpace: 'nowrap',
            color,
          }}
        >
          {line}
        </span>
      ))}
    </div>
  )
}
