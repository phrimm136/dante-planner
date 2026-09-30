/**
 * Unity-style rich text rendering helpers.
 *
 * Game data carries Unity rich text tags (e.g. <s>, <color=...>) in i18n
 * strings. These helpers convert them to React elements at render time,
 * preserving the position of each tag within the surrounding text.
 */

import { Fragment, type CSSProperties, type ReactNode } from 'react'

import { cn } from '@/lib/utils'
import { STRIKETHROUGH_GRAMMAR, tokenizeRichText, type RichTextToken } from './richText'
import { scanTmpRuns, type TmpRun, type TmpToken } from './tmpRichText'

function renderToken(token: RichTextToken): ReactNode {
  if (token.kind === 'element') return <s key={`strike-${token.index}`}>{token.content}</s>
  if (token.kind === 'text') return token.value
  return null
}

/**
 * Render Unity <s>...</s> strikethrough, preserving tag position.
 *
 * Splits the input on each balanced <s>...</s> pair: wrapped substrings
 * render inside a real <s> element, surrounding substrings render as
 * plain text in place. Text without a pair is returned unchanged.
 *
 * Flat parser, balanced pairs only. Nested <s> is not supported.
 * Orphan <s> or </s> with no partner render as literal text.
 */
export function applyStrikethrough(text: string): ReactNode {
  if (!text.includes('<s>')) return text

  const tokens = tokenizeRichText(text, STRIKETHROUGH_GRAMMAR)
  if (!tokens.some((token) => token.kind === 'element')) return text

  return <Fragment>{tokens.map(renderToken)}</Fragment>
}

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
