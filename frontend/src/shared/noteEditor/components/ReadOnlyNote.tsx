import { Fragment, useEffect, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { isSafeUserHref } from '@/shared/sanitize'
import type { NoteContent } from '../types/NoteEditorTypes'

import './NoteEditor.css'

type NoteNode = NoteContent['content']
type NoteMark = NonNullable<NoteNode['marks']>[number]

const HEADING_LEVELS = [1, 2, 3] as const
const ORDERED_LIST_TYPES = ['1', 'a', 'A', 'i', 'I'] as const
const LINK_TARGET = '_blank'
const LINK_REL = 'noopener noreferrer nofollow'
const CODE_LANGUAGE_CLASS_PREFIX = 'language-'

const warnedTypes = new Set<string>()

interface RenderContext {
  unknownTypes: string[]
}

function attr(item: NoteNode | NoteMark, name: string): unknown {
  const attrs: Record<string, unknown> | undefined = item.attrs
  return attrs?.[name]
}

function reportUnknown(ctx: RenderContext, kind: 'node' | 'mark', type: string | undefined) {
  ctx.unknownTypes.push(`${kind}:${type ?? '(none)'}`)
}

function headingTag(node: NoteNode): 'h1' | 'h2' | 'h3' {
  const level = attr(node, 'level')
  const known = HEADING_LEVELS.find((candidate) => candidate === level)
  return `h${known ?? HEADING_LEVELS[0]}`
}

function needsTrailingBreak(node: NoteNode): boolean {
  const last = node.content?.at(-1)
  if (!last) return true
  if (last.type !== 'text') return true
  return (last.text ?? '').endsWith('\n')
}

function isOrderedListType(value: unknown): value is (typeof ORDERED_LIST_TYPES)[number] {
  return ORDERED_LIST_TYPES.some((type) => type === value)
}

function sameMark(a: NoteMark, b: NoteMark): boolean {
  return a.type === b.type && JSON.stringify(a.attrs ?? {}) === JSON.stringify(b.attrs ?? {})
}

function wrapInMark(mark: NoteMark, children: ReactNode[], key: number, ctx: RenderContext) {
  switch (mark.type) {
    case 'bold':
      return <strong key={key}>{children}</strong>
    case 'italic':
      return <em key={key}>{children}</em>
    case 'strike':
      return <s key={key}>{children}</s>
    case 'underline':
      return <u key={key}>{children}</u>
    case 'code':
      return <code key={key}>{children}</code>
    case 'spoiler':
      return (
        <span key={key} className="spoiler" data-spoiler="true">
          {children}
        </span>
      )
    case 'link': {
      const href = attr(mark, 'href')
      if (typeof href !== 'string' || !isSafeUserHref(href)) {
        return <Fragment key={key}>{children}</Fragment>
      }
      return (
        <a key={key} href={href} target={LINK_TARGET} rel={LINK_REL} className="note-link">
          {children}
        </a>
      )
    }
    default:
      reportUnknown(ctx, 'mark', mark.type)
      return <Fragment key={key}>{children}</Fragment>
  }
}

function renderInlineLeaf(node: NoteNode, key: number, ctx: RenderContext): ReactNode {
  if (node.type === 'text') return node.text ?? ''
  return renderNode(node, key, ctx)
}

function renderChildren(parent: NoteNode, ctx: RenderContext): ReactNode[] {
  const children = parent.content ?? []
  const root: ReactNode[] = []
  const open: { mark: NoteMark; children: ReactNode[] }[] = []
  let key = 0

  const closeTo = (depth: number) => {
    while (open.length > depth) {
      const frame = open.pop()
      if (!frame) break
      const target = open.at(-1)?.children ?? root
      target.push(wrapInMark(frame.mark, frame.children, key++, ctx))
    }
  }

  for (const child of children) {
    const marks = child.marks ?? []
    let shared = 0
    for (const [index, mark] of marks.entries()) {
      const frame = open[index]
      if (!frame || !sameMark(frame.mark, mark)) break
      shared = index + 1
    }
    closeTo(shared)
    for (const mark of marks.slice(shared)) {
      open.push({ mark, children: [] })
    }
    const target = open.at(-1)?.children ?? root
    target.push(renderInlineLeaf(child, key++, ctx))
  }
  closeTo(0)
  return root
}

function renderTextblockChildren(node: NoteNode, ctx: RenderContext): ReactNode[] {
  const children = renderChildren(node, ctx)
  if (needsTrailingBreak(node)) children.push(<br key="trailing-break" />)
  return children
}

function renderNode(node: NoteNode, key: number, ctx: RenderContext): ReactNode {
  switch (node.type) {
    case 'paragraph':
      return <p key={key}>{renderTextblockChildren(node, ctx)}</p>
    case 'heading': {
      const Tag = headingTag(node)
      return <Tag key={key}>{renderTextblockChildren(node, ctx)}</Tag>
    }
    case 'blockquote':
      return <blockquote key={key}>{renderChildren(node, ctx)}</blockquote>
    case 'bulletList':
      return <ul key={key}>{renderChildren(node, ctx)}</ul>
    case 'orderedList': {
      const start = attr(node, 'start')
      const type = attr(node, 'type')
      return (
        <ol
          key={key}
          start={typeof start === 'number' && start !== 1 ? start : undefined}
          type={isOrderedListType(type) ? type : undefined}
        >
          {renderChildren(node, ctx)}
        </ol>
      )
    }
    case 'listItem':
      return <li key={key}>{renderChildren(node, ctx)}</li>
    case 'codeBlock': {
      const language = attr(node, 'language')
      return (
        <pre key={key}>
          <code
            className={
              typeof language === 'string' && language
                ? `${CODE_LANGUAGE_CLASS_PREFIX}${language}`
                : undefined
            }
          >
            {renderTextblockChildren(node, ctx)}
          </code>
        </pre>
      )
    }
    case 'hardBreak':
      return <br key={key} />
    case 'horizontalRule':
      return <hr key={key} />
    default:
      reportUnknown(ctx, 'node', node.type)
      return <Fragment key={key}>{renderChildren(node, ctx)}</Fragment>
  }
}

function isEmptyNode(node: NoteNode): boolean {
  if (node.type === 'text') return !node.text
  if (node.type === 'hardBreak' || node.type === 'horizontalRule') return false
  return (node.content ?? []).every(isEmptyNode)
}

interface ReadOnlyNoteProps {
  value: NoteContent
}

export function ReadOnlyNote({ value }: ReadOnlyNoteProps) {
  const { t } = useTranslation(['planner', 'common'])
  const ctx: RenderContext = { unknownTypes: [] }
  const doc = value.content
  const children = renderChildren(doc, ctx)
  const unknownKey = ctx.unknownTypes.join(',')

  useEffect(() => {
    if (!import.meta.env.DEV || !unknownKey) return
    for (const type of unknownKey.split(',')) {
      if (warnedTypes.has(type)) continue
      warnedTypes.add(type)
      console.warn(`ReadOnlyNote: unhandled ${type}; rendered its text only`)
    }
  }, [unknownKey])

  return (
    <div className="note-editor rounded-md border border-input bg-background relative">
      <div>
        <div className="note-editor-content note-read-only prose prose-sm max-w-none focus:outline-none min-h-[100px] p-3">
          {children}
        </div>
      </div>
      {isEmptyNode(doc) && (
        <div className="absolute top-0 left-0 p-3 text-muted-foreground pointer-events-none">
          {t('pages.plannerMD.noteEditor.placeholderReadOnly')}
        </div>
      )}
    </div>
  )
}
