/**
 * Unity-style rich text rendering helpers.
 *
 * Game data carries Unity rich text tags (e.g. <s>, <color=...>) in i18n
 * strings. These helpers convert them to React elements at render time,
 * preserving the position of each tag within the surrounding text.
 */

import { Fragment, type CSSProperties, type ReactNode } from 'react'

import { cn } from '@/lib/utils'
import { scanTmpRuns, type TmpRun, type TmpToken } from './tmpRichText'

type RunStyle = CSSProperties & Partial<Record<`--rt-${string}`, string>>

function runStyle(run: TmpRun): RunStyle {
  const lines = [run.underline && 'underline', run.strike && 'line-through'].filter(Boolean)
  return {
    ...(run.color && { '--rt-color': run.color }),
    ...(run.mark && { '--rt-mark-color': run.mark.rgb, '--rt-mark-alpha': run.mark.alphaPct }),
    ...(run.decorationColor && { '--rt-decoration-color': run.decorationColor }),
    ...(run.bold && { fontWeight: 'bold' }),
    ...(lines.length > 0 && { textDecorationLine: lines.join(' ') }),
    ...(run.fontSize && { fontSize: run.fontSize }),
  }
}

function isPlain(run: TmpRun): boolean {
  return Object.keys(runStyle(run)).length === 0
}

export function renderTmpRun(run: TmpRun, key: number, content: ReactNode): ReactNode {
  if (isPlain(run)) return <Fragment key={key}>{content}</Fragment>

  return (
    <span
      key={key}
      className={cn(
        (run.color || run.mark) && 'rt-ink',
        run.mark && 'rt-mark',
        run.decorationColor && 'rt-deco',
      )}
      style={runStyle(run)}
    >
      {content}
    </span>
  )
}

function renderTmpToken(token: TmpToken, index: number): ReactNode {
  if (token.kind === 'break') return <br key={index} />
  return renderTmpRun(token, index, token.text)
}

export function applyTextStyles(text: string): ReactNode {
  const tokens = scanTmpRuns(text)
  const [only] = tokens
  if (tokens.length === 0 || (tokens.length === 1 && only?.kind === 'run' && isPlain(only))) {
    return only?.kind === 'run' ? only.text : text
  }

  return <Fragment>{tokens.map(renderTmpToken)}</Fragment>
}
