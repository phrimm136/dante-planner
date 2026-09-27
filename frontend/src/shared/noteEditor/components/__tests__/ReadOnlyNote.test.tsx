import { readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, waitFor, cleanup, act } from '@testing-library/react'
import type { Editor, JSONContent } from '@tiptap/core'
import { stubRangeRects } from '@/test-utils'
import { NoteEditor } from '../NoteEditor'
import { ReadOnlyNote } from '../ReadOnlyNote'
import type { NoteContent } from '../../types/NoteEditorTypes'

const EMPTY_DOC: JSONContent = { type: 'doc', content: [{ type: 'paragraph' }] }
const SAFE_HREF = 'https://example.com/path?q=1'
const NOTE_CSS = readFileSync(
  path.resolve(process.cwd(), 'src/shared/noteEditor/components/NoteEditor.css'),
  'utf8',
)
const IGNORED_ATTRIBUTES = new Set(['contenteditable', 'translate', 'role', 'tabindex'])

stubRangeRects()

afterEach(() => {
  cleanup()
})

async function mountEditor(doc: JSONContent, readOnly: boolean) {
  const view = render(
    <NoteEditor value={{ content: doc }} onChange={() => {}} readOnly={readOnly} />,
  )
  const editor = await waitFor(() => {
    const el = view.container.querySelector<HTMLElement & { editor?: Editor }>('.ProseMirror')
    if (!el?.editor) throw new Error('editor not mounted')
    return el.editor
  })
  return { container: view.container, editor, unmount: view.unmount }
}

function driveEveryFeature(editor: Editor) {
  editor.commands.setHeading({ level: 1 })
  editor.commands.insertContent('Heading one')
  editor.commands.splitBlock()
  editor.commands.setHeading({ level: 2 })
  editor.commands.insertContent('Heading two')
  editor.commands.splitBlock()
  editor.commands.setHeading({ level: 3 })
  editor.commands.insertContent('Heading three')
  editor.commands.splitBlock()
  editor.commands.setParagraph()
  editor.commands.insertContent('two  spaces   three ')
  for (const mark of ['bold', 'italic', 'strike', 'underline', 'code', 'spoiler']) {
    editor.commands.toggleMark(mark)
    editor.commands.insertContent(`${mark} `)
    editor.commands.toggleMark(mark)
  }
  editor.commands.toggleBold()
  editor.commands.insertContent('bold ')
  editor.commands.toggleItalic()
  editor.commands.insertContent('bold-italic')
  editor.commands.toggleItalic()
  editor.commands.toggleBold()
  editor.commands.insertContent(' ')
  editor.commands.insertContent({
    type: 'text',
    text: 'a link',
    marks: [{ type: 'link', attrs: { href: SAFE_HREF } }],
  })
  editor.commands.unsetMark('link')
  editor.commands.insertContent(' after')
  editor.commands.setHardBreak()
  editor.commands.insertContent('after break')
  editor.commands.splitBlock()
  editor.commands.splitBlock()
  editor.commands.toggleBulletList()
  editor.commands.insertContent('bullet one')
  editor.commands.splitListItem('listItem')
  editor.commands.insertContent('bullet two')
  editor.commands.splitListItem('listItem')
  editor.commands.liftListItem('listItem')
  editor.commands.toggleOrderedList()
  editor.commands.insertContent('ordered one')
  editor.commands.splitListItem('listItem')
  editor.commands.insertContent('ordered two')
  editor.commands.updateAttributes('orderedList', { start: 3 })
  editor.commands.splitListItem('listItem')
  editor.commands.liftListItem('listItem')
  editor.commands.toggleBlockquote()
  editor.commands.insertContent('quoted')
  editor.commands.splitBlock()
  editor.commands.lift('blockquote')
  editor.commands.setHorizontalRule()
  editor.commands.setCodeBlock({ language: 'ts' })
  editor.commands.insertContent('const a = 1\n  indented\n')
  editor.commands.exitCode()
  editor.commands.insertContent('ends with a break')
  editor.commands.setHardBreak()
}

async function recordFixture() {
  const { editor, unmount } = await mountEditor(EMPTY_DOC, false)
  act(() => driveEveryFeature(editor))
  const fixture = editor.getJSON()
  const nodeNames = Object.keys(editor.schema.nodes)
  const markNames = Object.keys(editor.schema.marks)
  unmount()
  return { fixture, nodeNames, markNames }
}

function collectTypes(node: JSONContent, nodes: Set<string>, marks: Set<string>) {
  if (node.type) nodes.add(node.type)
  for (const mark of node.marks ?? []) marks.add(mark.type)
  for (const child of node.content ?? []) collectTypes(child, nodes, marks)
}

function normalizeClass(value: string): string {
  return value
    .split(/\s+/)
    .filter((token) => token && !/^(ProseMirror|tiptap$|note-read-only$)/.test(token))
    .sort()
    .join(' ')
}

function normalize(node: Node): unknown {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent
  if (!(node instanceof Element)) return null
  const attributes: Record<string, string> = {}
  for (const { name, value } of Array.from(node.attributes)) {
    if (IGNORED_ATTRIBUTES.has(name) || name.startsWith('aria-') || name.startsWith('data-pm')) {
      continue
    }
    const normalized = name === 'class' ? normalizeClass(value) : value
    if (name === 'class' && !normalized) continue
    attributes[name] = normalized
  }
  const children: unknown[] = []
  for (const child of Array.from(node.childNodes)) {
    const next = normalize(child)
    if (next === null || next === '') continue
    const last = children.at(-1)
    if (typeof next === 'string' && typeof last === 'string') {
      children[children.length - 1] = last + next
    } else {
      children.push(next)
    }
  }
  return { tag: node.tagName.toLowerCase(), attributes, children }
}

function noteRoot(container: HTMLElement): Element {
  const root = container.querySelector('.note-editor')
  if (!root) throw new Error('no note root')
  return root
}

async function renderBoth(doc: JSONContent) {
  const readOnly = render(<ReadOnlyNote value={{ content: doc }} />)
  const renderer = normalize(noteRoot(readOnly.container))
  readOnly.unmount()
  const editor = await mountEditor(doc, true)
  const reference = normalize(noteRoot(editor.container))
  editor.unmount()
  return { renderer, reference }
}

describe('ReadOnlyNote parity with the read-only editor', () => {
  it('records a fixture holding every node and mark the editor schema defines', async () => {
    const { fixture, nodeNames, markNames } = await recordFixture()
    const nodes = new Set<string>()
    const marks = new Set<string>()
    collectTypes(fixture, nodes, marks)

    expect([...nodes].sort()).toEqual([...nodeNames].sort())
    expect([...marks].sort()).toEqual([...markNames].sort())
    expect(JSON.stringify(fixture)).toContain('"start":3')
    expect(JSON.stringify(fixture)).toContain('"language":"ts"')
  })

  it('renders the recorded fixture to the same DOM as the editor', async () => {
    const { fixture } = await recordFixture()
    const { renderer, reference } = await renderBoth(fixture)
    expect(renderer).toEqual(reference)
  })

  it('renders an empty note with the same read-only placeholder as the editor', async () => {
    const { renderer, reference } = await renderBoth(EMPTY_DOC)
    expect(renderer).toEqual(reference)
  })
})

describe('ReadOnlyNote schema guard', () => {
  it('renders an element for every node and mark name in the editor schema', async () => {
    const { nodeNames, markNames } = await recordFixture()
    const blockNames = nodeNames.filter((name) => name !== 'doc' && name !== 'text')
    const renderContent = (block: JSONContent) => {
      const { container, unmount } = render(
        <ReadOnlyNote value={{ content: { type: 'doc', content: [block] } }} />,
      )
      const content = container.querySelector('.note-editor-content')
      const html = content?.innerHTML ?? ''
      unmount()
      return html
    }

    const bareNodes = blockNames.filter(
      (name) =>
        !renderContent({ type: name, content: [{ type: 'text', text: name }] }).startsWith('<'),
    )
    const bareMarks = markNames.filter((name) => {
      const html = renderContent({
        type: 'paragraph',
        content: [
          { type: 'text', text: name, marks: [{ type: name, attrs: { href: SAFE_HREF } }] },
        ],
      })
      return html.startsWith(`<p>${name}`)
    })

    expect(bareNodes).toEqual([])
    expect(bareMarks).toEqual([])
  })

  it('warns once per type it does not handle and keeps its text', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const doc: JSONContent = {
      type: 'doc',
      content: [
        {
          type: 'guardProbeNode',
          content: [{ type: 'text', text: 'probe text', marks: [{ type: 'guardProbeMark' }] }],
        },
      ],
    }

    const first = render(<ReadOnlyNote value={{ content: doc }} />)
    render(<ReadOnlyNote value={{ content: doc }} />)

    expect(first.container.textContent).toContain('probe text')
    expect(warn.mock.calls.map(([message]) => String(message)).sort()).toEqual([
      'ReadOnlyNote: unhandled mark:guardProbeMark; rendered its text only',
      'ReadOnlyNote: unhandled node:guardProbeNode; rendered its text only',
    ])
  })
})

describe('ReadOnlyNote inert content', () => {
  it('renders a javascript: link as plain text', () => {
    const note: NoteContent = {
      content: {
        type: 'doc',
        content: [
          {
            type: 'paragraph',
            content: [
              {
                type: 'text',
                text: 'click me',
                marks: [{ type: 'link', attrs: { href: 'javascript:alert(1)' } }],
              },
            ],
          },
        ],
      },
    }

    const { container } = render(<ReadOnlyNote value={note} />)

    expect(container.querySelector('a')).toBeNull()
    expect(container.textContent).toContain('click me')
  })

  it('drops event-handler attributes from nodes and marks', () => {
    const note: NoteContent = {
      content: {
        type: 'doc',
        content: [
          {
            type: 'paragraph',
            attrs: { onerror: 'alert(1)', onclick: 'alert(2)' },
            content: [
              {
                type: 'text',
                text: 'link',
                marks: [
                  {
                    type: 'link',
                    attrs: { href: SAFE_HREF, onerror: 'alert(3)', onmouseover: 'alert(4)' },
                  },
                ],
              },
            ],
          },
          { type: 'image', attrs: { src: 'x', onerror: 'alert(5)' } },
        ],
      },
    }
    vi.spyOn(console, 'warn').mockImplementation(() => {})

    const { container } = render(<ReadOnlyNote value={note} />)

    const withHandlers = Array.from(container.querySelectorAll('*')).filter((el) =>
      Array.from(el.attributes).some((attribute) => attribute.name.startsWith('on')),
    )
    expect(withHandlers).toEqual([])
    expect(container.querySelector('img')).toBeNull()
    expect(container.querySelector('a')?.getAttribute('href')).toBe(SAFE_HREF)
  })
})

describe('ReadOnlyNote whitespace', () => {
  const note: NoteContent = {
    content: {
      type: 'doc',
      content: [
        { type: 'paragraph', content: [{ type: 'text', text: 'a   b' }] },
        { type: 'paragraph' },
      ],
    },
  }

  it('keeps repeated spaces under a pre-wrap content box', () => {
    const { container } = render(<ReadOnlyNote value={note} />)
    const content = container.querySelector('.note-editor-content')

    expect(content?.classList.contains('note-read-only')).toBe(true)
    expect(content?.querySelector('p')?.textContent).toBe('a   b')
    const rule = NOTE_CSS.match(/\.note-read-only\s*\{([^}]*)\}/)?.[1] ?? ''
    expect(rule).toContain('white-space: pre-wrap')
  })

  it('gives an empty paragraph one line through a trailing break', () => {
    const { container } = render(<ReadOnlyNote value={note} />)
    const empty = container.querySelectorAll('p')[1]

    expect(empty?.innerHTML).toBe('<br>')
  })
})
