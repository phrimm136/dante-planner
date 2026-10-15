import { Suspense } from 'react'

import { Skeleton } from '@/components/ui/skeleton'
import { getDisplayFontForFallback } from '@/lib/utils'
import { lineMetrics, useFontAdvances } from '@/shared/cardLayout'
import type { MDVersion } from '@/shared/gameData'
import type { StartBuffCardLayout } from '../../schemas/StartBuffCardSchemas'
import {
  bulletGlyph,
  firstLineOffsetEm,
  isShown,
  layoutDescription,
  lineSpacingEm,
  rectStyle,
  spriteFile,
  textTracking,
  unitsToCqw,
  type CardState,
  type LayoutSpace,
  type TextRun,
} from '../../lib/startBuffCardLayout'
import { CardSprite } from './CardSprite'

interface StartBuffDescriptionProps {
  version: MDVersion
  layout: StartBuffCardLayout
  runs: TextRun[]
  language: string
  state: CardState
  baseId: number
  space: LayoutSpace
  onClick: () => void
}

function lineText(line: readonly TextRun[]): string {
  return line.map((run) => run.text).join('')
}

function RunText({
  run,
  bullet,
  bulletWidthEm,
}: {
  run: TextRun
  bullet: string
  bulletWidthEm: number
}) {
  const parts = run.text.split(bullet)
  return (
    <span style={{ color: run.color }}>
      {parts.map((part, index) => (
        <span key={`${String(index)}:${part}`}>
          {index > 0 && (
            <span
              data-testid="start-buff-description-bullet"
              style={{
                display: 'inline-block',
                width: `${String(bulletWidthEm)}em`,
                textAlign: 'left',
                letterSpacing: 0,
                fontFamily: getDisplayFontForFallback(),
              }}
            >
              {bullet}
            </span>
          )}
          {part}
        </span>
      ))}
    </span>
  )
}

export function StartBuffDescription(props: StartBuffDescriptionProps) {
  return (
    <Suspense
      fallback={
        <Skeleton
          style={{ ...rectStyle(props.layout.viewport, props.space), pointerEvents: 'none' }}
        />
      }
    >
      <StartBuffDescriptionBody {...props} />
    </Suspense>
  )
}

function StartBuffDescriptionBody({
  version,
  layout,
  runs,
  language,
  state,
  baseId,
  space,
  onClick,
}: StartBuffDescriptionProps) {
  const table = useFontAdvances(language, 'description')
  const description = layoutDescription(runs, layout, table)
  const viewport = layout.viewport
  const desc = layout.desc
  const node = desc.text
  const tracking = textTracking(node)
  const spacing = lineSpacingEm(node)
  const bullet = bulletGlyph(layout)
  const bulletWidthEm = layout.description.bullet.advanceEm + tracking.letterSpacingEm
  const firstLine = lineText(description.lines[0] ?? [])
  const scrollState: CardState = { ...state, overflows: description.overflows }

  return (
    <>
      <div
        role="presentation"
        data-testid="start-buff-description"
        className="overflow-y-auto scrollbar-hide"
        style={{ ...rectStyle(viewport, space), pointerEvents: 'auto' }}
        onClick={onClick}
      >
        <div
          style={{
            marginLeft: unitsToCqw(desc.rect.x - viewport.x, space),
            marginTop: `calc(${unitsToCqw(desc.rect.y - viewport.y, space)} + ${String(firstLineOffsetEm(table, firstLine, spacing))}em)`,
            width: unitsToCqw(desc.rect.w, space),
            fontSize: unitsToCqw(description.fontSize, space),
            letterSpacing: `${String(tracking.letterSpacingEm)}em`,
            wordSpacing: `${String(tracking.wordSpacingEm)}em`,
            color: node.color,
          }}
        >
          {description.lines.map((line, index) => (
            <span
              key={`${String(index)}:${lineText(line)}`}
              data-testid="start-buff-description-line"
              style={{
                display: 'block',
                whiteSpace: 'pre',
                lineHeight: lineMetrics(table, lineText(line)).lineHeight + spacing,
              }}
            >
              {line.map((run, runIndex) => (
                <RunText
                  key={`${String(runIndex)}:${run.text}`}
                  run={run}
                  bullet={bullet}
                  bulletWidthEm={bulletWidthEm}
                />
              ))}
            </span>
          ))}
        </div>
      </div>
      {layout.elements
        .filter((element) => element.shownWhen === 'scrollable' && isShown(element, scrollState))
        .map((element) => {
          if (!('image' in element)) return null
          const file = spriteFile(element.image, baseId)
          if (file === null) return null
          return (
            <CardSprite
              key={element.path}
              version={version}
              file={file}
              image={element.image}
              rect={element.rect}
              space={space}
            />
          )
        })}
    </>
  )
}
