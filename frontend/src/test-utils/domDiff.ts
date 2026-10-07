import { structuredPatch } from 'diff'

const INDENT = '  '

function serializeNode(node: Node, depth: number, lines: string[]): void {
  const pad = INDENT.repeat(depth)
  if (node.nodeType === Node.TEXT_NODE) {
    lines.push(pad + JSON.stringify(node.textContent))
    return
  }
  if (node.nodeType === Node.COMMENT_NODE) {
    lines.push(`${pad}<!--${node.textContent}-->`)
    return
  }
  if (!(node instanceof Element)) return

  const attributes = Array.from(node.attributes)
    .sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0))
    .map((attribute) => ` ${attribute.name}=${JSON.stringify(attribute.value)}`)
    .join('')
  const children = Array.from(node.childNodes)
  if (children.length === 0) {
    lines.push(`${pad}<${node.localName}${attributes} />`)
    return
  }
  lines.push(`${pad}<${node.localName}${attributes}>`)
  for (const child of children) serializeNode(child, depth + 1, lines)
  lines.push(`${pad}</${node.localName}>`)
}

/** One tag or text node per line, indented by depth, attributes sorted by name, text JSON-quoted. */
export function normalizeHtml(html: string): string {
  const body = new DOMParser().parseFromString(html, 'text/html').body
  const lines: string[] = []
  for (const child of Array.from(body.childNodes)) serializeNode(child, 0, lines)
  return lines.join('\n') + '\n'
}

function depthOf(line: string): number {
  return (line.length - line.trimStart().length) / INDENT.length
}

function selectorOf(openTag: string): string {
  const name = openTag.match(/^<([^\s>/]+)/)?.[1] ?? ''
  const firstClass = openTag
    .match(/\sclass="([^"]*)"/)?.[1]
    ?.trim()
    .split(/\s+/)[0]
  return firstClass ? `${name}.${firstClass}` : name
}

function isClosingTag(line: string): boolean {
  return line.trimStart().startsWith('</')
}

function isElementStart(line: string): boolean {
  const trimmed = line.trimStart()
  return trimmed.startsWith('<') && !trimmed.startsWith('</') && !trimmed.startsWith('<!--')
}

/** How many lines at `lines[index]`'s depth under the same parent, up to it, `counts` accepts. */
function countSiblingsThrough(
  lines: readonly string[],
  index: number,
  counts: (line: string) => boolean,
): number {
  const depth = depthOf(lines[index] ?? '')
  let count = 0
  for (let i = index; i >= 0; i--) {
    const line = lines[i] ?? ''
    if (depthOf(line) < depth) break
    if (depthOf(line) === depth && counts(line)) count++
  }
  return count
}

/** The open tags enclosing `lines[index]`, outermost first, as `tag.firstClass[n]` steps. */
function ancestorPath(lines: readonly string[], index: number): string {
  const path: string[] = []
  let depth = depthOf(lines[index] ?? '')
  for (let i = index - 1; i >= 0 && depth > 0; i--) {
    const line = lines[i] ?? ''
    if (depthOf(line) < depth) {
      const position = countSiblingsThrough(lines, i, isElementStart)
      path.unshift(`${selectorOf(line.trimStart())}[${position}]`)
      depth = depthOf(line)
    }
  }
  return path.join(' > ')
}

function locate(sign: string, lines: readonly string[], index: number): string {
  const path = ancestorPath(lines, index)
  const line = lines[index] ?? ''
  const position = countSiblingsThrough(lines, index, (sibling) => !isClosingTag(sibling))
  return `${sign} ${path ? `${path} ` : ''}#${position}: ${line.trimStart()}`
}

/**
 * Line diff of `html` against `baselineHtml`, both normalized first. Hunks carry no context
 * lines and no line numbers. Each changed line is prefixed with its ancestor path, every step
 * numbered by its position among its element siblings, and `#n`, its own position among its
 * parent's child nodes. Beyond its own lines, a diff moves only when a node is inserted or
 * removed before one of its ancestors or before the line under the same parent, or when an
 * ancestor's tag or first class changes; changes inside other subtrees never move it.
 */
export function diffHtml(baselineHtml: string, html: string): string {
  const before = normalizeHtml(baselineHtml)
  const after = normalizeHtml(html)
  const beforeLines = before.split('\n')
  const afterLines = after.split('\n')
  const { hunks } = structuredPatch('baseline', 'cell', before, after, '', '', { context: 0 })
  return hunks
    .map((hunk) => {
      let oldIndex = hunk.oldStart - 1
      let newIndex = hunk.newStart - 1
      const changed = hunk.lines.map((line) => {
        if (line.startsWith('-')) return locate('-', beforeLines, oldIndex++)
        if (line.startsWith('+')) return locate('+', afterLines, newIndex++)
        return line
      })
      return ['@@', ...changed].join('\n')
    })
    .join('\n')
}
