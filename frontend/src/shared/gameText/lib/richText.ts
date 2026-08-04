/**
 * Unity rich text tokenizer.
 *
 * One scanner drives every rich text reader in the app. A grammar names the
 * tags it recognises, how each one finds its closing tag, and what happens to
 * the content in between; the scanner turns a raw game string into tokens and
 * each consumer renders those tokens its own way.
 *
 * Tag values are validated per grammar: game data carries `<color=#ebcaa2>`,
 * `<color=red>` and `<color=>` in different places, and the grammars below
 * differ on which of those count as a tag at all.
 */

/** Source for the strict six-digit hex color value */
export const HEX_COLOR_SOURCE = '#[0-9a-fA-F]{6}'

export const RICH_TEXT_TAG = {
  color: 'color',
  size: 'size',
  style: 'style',
  strike: 'strike',
} as const

export type RichTextTagName = (typeof RICH_TEXT_TAG)[keyof typeof RICH_TEXT_TAG]

/**
 * How a rule locates the end of its element.
 * - `lazy`: the first closing tag after the opening tag closes it
 * - `balanced`: a depth counter over {@link RichTextRule.depthOpen} closes it
 */
type CloseMatching = 'lazy' | 'balanced'

/**
 * Which rules apply to an element's content.
 * - `raw`: none, the content stays an unparsed string
 * - `here`: the rule's own layer and every layer below it
 * - `below`: the layers below the rule's own layer
 * - `top`: the whole grammar again
 */
type Descend = 'raw' | 'here' | 'below' | 'top'

export interface RichTextRule {
  name: RichTextTagName
  /** Opening tag; capture group 1 is the tag value */
  open: RegExp
  close: string
  match: CloseMatching
  /** Opening-tag prefix counted by `balanced` matching */
  depthOpen?: string
  /** Discard the element when its content is empty */
  dropEmpty?: boolean
  descend: Descend
}

export interface RichTextGrammar {
  /** Rule layers, outermost first; rules within a layer compete by position */
  layers: RichTextRule[][]
  /** Applied to the whole string before scanning */
  rewrites?: [RegExp, string][]
  /** Emit `break` tokens for newlines */
  breaks?: boolean
  /** Discard closing tags that no rule matched */
  dropStrayClose?: boolean
}

interface TextToken {
  kind: 'text'
  value: string
  index: number
}

interface BreakToken {
  kind: 'break'
  index: number
}

interface ElementToken {
  kind: 'element'
  name: RichTextTagName
  /** Tag value, e.g. `#ebcaa2` for `<color=#ebcaa2>` */
  value: string
  /** Raw content between the opening and closing tags */
  content: string
  children: RichTextToken[]
  index: number
  contentIndex: number
}

export type RichTextToken = TextToken | BreakToken | ElementToken

const COLOR_HEX_RULE: RichTextRule = {
  name: RICH_TEXT_TAG.color,
  open: new RegExp(`<color=(${HEX_COLOR_SOURCE})>`),
  close: '</color>',
  match: 'balanced',
  depthOpen: '<color=',
  dropEmpty: true,
  descend: 'here',
}

const SIZE_BLOCK_RULE: RichTextRule = {
  name: RICH_TEXT_TAG.size,
  open: /<size=([^>]*)>/,
  close: '</size>',
  match: 'lazy',
  descend: 'below',
}

const COLOR_ANY_RULE: RichTextRule = {
  name: RICH_TEXT_TAG.color,
  open: /<color=([^>]*)>/,
  close: '</color>',
  match: 'lazy',
  descend: 'raw',
}

const SIZE_INLINE_RULE: RichTextRule = {
  name: RICH_TEXT_TAG.size,
  open: /<size=([^>]*)>/,
  close: '</size>',
  match: 'lazy',
  descend: 'top',
}

const UPGRADE_HIGHLIGHT_RULE: RichTextRule = {
  name: RICH_TEXT_TAG.style,
  open: /<style="upgradeHighlight">/,
  close: '</style>',
  match: 'lazy',
  descend: 'raw',
}

const STRIKETHROUGH_RULE: RichTextRule = {
  name: RICH_TEXT_TAG.strike,
  open: /<s>/,
  close: '</s>',
  match: 'lazy',
  descend: 'raw',
}

/**
 * Six-digit hex colors only, nesting-aware, with size blocks split off before
 * colors are read. Malformed `</color=#hex>` closing tags are repaired first.
 */
export const NESTED_HEX_COLOR_GRAMMAR: RichTextGrammar = {
  layers: [[SIZE_BLOCK_RULE], [COLOR_HEX_RULE]],
  rewrites: [[/<\/color=[^>]*>/g, '</color>']],
}

/**
 * Any color value including the empty one, flat: the first `</color>` closes
 * the element and its content is not parsed further. Newlines break lines and
 * unmatched closing tags are discarded.
 */
export const FLAT_ANY_COLOR_GRAMMAR: RichTextGrammar = {
  layers: [[SIZE_INLINE_RULE, COLOR_ANY_RULE]],
  breaks: true,
  dropStrayClose: true,
}

/** `<style="upgradeHighlight">` only; every other style value stays text */
export const UPGRADE_HIGHLIGHT_GRAMMAR: RichTextGrammar = {
  layers: [[UPGRADE_HIGHLIGHT_RULE]],
}

/** Balanced `<s>` pairs, flat */
export const STRIKETHROUGH_GRAMMAR: RichTextGrammar = {
  layers: [[STRIKETHROUGH_RULE]],
}

const FIRST_COLOR_VALUE_RE = /<color=([^>]+)>/
const COLOR_OPEN_RE = /<color=[^>]+>/g
const COLOR_CLOSE_RE = /<\/color>/g
const ANY_TAG_RE = /<[^>]+>/g
const STRAY_CLOSE_RE = /^<\/[^>]*>/

export interface LeadingColor {
  /** Absent when the input carries no color tag */
  color?: string
  /** Input with every color tag removed */
  text: string
}

/**
 * Read the first color tag of a label and flatten every color tag away.
 *
 * A non-empty value is required, so `<color=>` is not a color tag here.
 *
 * @example "<color=#d40000><s>Jia Family</s></color>"
 *       -> { color: "#d40000", text: "<s>Jia Family</s>" }
 */
export function extractLeadingColor(input: string): LeadingColor {
  const colorMatch = input.match(FIRST_COLOR_VALUE_RE)
  if (!colorMatch) return { text: input }

  return {
    color: colorMatch[1],
    text: input.replace(COLOR_OPEN_RE, '').replace(COLOR_CLOSE_RE, ''),
  }
}

/** Strip every rich text tag, leaving plain searchable text */
export function stripRichTextTags(text: string): string {
  return text.replace(ANY_TAG_RE, '')
}

/** Tokenize Unity rich text under a grammar */
export function tokenizeRichText(text: string, grammar: RichTextGrammar): RichTextToken[] {
  const source =
    grammar.rewrites?.reduce(
      (acc, [pattern, replacement]) => acc.replace(pattern, replacement),
      text,
    ) ?? text

  return tokenizeLayer(source, 0, 0, grammar)
}

interface RuleMatch {
  rule: RichTextRule
  start: number
  contentStart: number
  content: string
  value: string
  end: number
}

function tokenizeLayer(
  text: string,
  layerIndex: number,
  base: number,
  grammar: RichTextGrammar,
): RichTextToken[] {
  const layer = grammar.layers[layerIndex]
  if (!layer) return tokenizeLeaf(text, base, grammar)

  const tokens: RichTextToken[] = []
  let pos = 0

  while (pos < text.length) {
    const hit = findEarliestMatch(text, pos, layer)
    if (!hit) break

    if (hit.start > pos) {
      tokens.push(...tokenizeLayer(text.slice(pos, hit.start), layerIndex + 1, base + pos, grammar))
    }
    if (!hit.rule.dropEmpty || hit.content !== '') {
      tokens.push(toElementToken(hit, layerIndex, base, grammar))
    }
    pos = hit.end
  }

  if (pos < text.length) {
    tokens.push(...tokenizeLayer(text.slice(pos), layerIndex + 1, base + pos, grammar))
  }

  return tokens
}

function toElementToken(
  hit: RuleMatch,
  layerIndex: number,
  base: number,
  grammar: RichTextGrammar,
): ElementToken {
  const contentIndex = base + hit.contentStart

  return {
    kind: 'element',
    name: hit.rule.name,
    value: hit.value,
    content: hit.content,
    index: base + hit.start,
    contentIndex,
    children:
      hit.rule.descend === 'raw'
        ? []
        : tokenizeLayer(
            hit.content,
            childLayer(hit.rule.descend, layerIndex),
            contentIndex,
            grammar,
          ),
  }
}

function childLayer(descend: Exclude<Descend, 'raw'>, layerIndex: number): number {
  switch (descend) {
    case 'top':
      return 0
    case 'here':
      return layerIndex
    case 'below':
      return layerIndex + 1
  }
}

function findEarliestMatch(text: string, from: number, layer: RichTextRule[]): RuleMatch | null {
  let earliest: RuleMatch | null = null

  for (const rule of layer) {
    const hit =
      rule.match === 'balanced' ? matchBalanced(text, from, rule) : matchLazy(text, from, rule)
    if (hit && (!earliest || hit.start < earliest.start)) earliest = hit
  }

  return earliest
}

interface OpenMatch {
  start: number
  contentStart: number
  value: string
}

function matchOpen(text: string, from: number, rule: RichTextRule): OpenMatch | null {
  const match = text.slice(from).match(rule.open)
  if (!match || match.index === undefined) return null

  const start = from + match.index

  return { start, contentStart: start + match[0].length, value: match[1] ?? '' }
}

function matchLazy(text: string, from: number, rule: RichTextRule): RuleMatch | null {
  const open = matchOpen(text, from, rule)
  if (!open) return null

  const closeIndex = text.indexOf(rule.close, open.contentStart)
  if (closeIndex === -1) return null

  return {
    rule,
    ...open,
    content: text.slice(open.contentStart, closeIndex),
    end: closeIndex + rule.close.length,
  }
}

function matchBalanced(text: string, from: number, rule: RichTextRule): RuleMatch | null {
  const open = matchOpen(text, from, rule)
  if (!open) return null

  const depthOpen = rule.depthOpen ?? rule.close
  let depth = 1
  let pos = open.contentStart

  while (pos < text.length && depth > 0) {
    if (text.startsWith(depthOpen, pos)) {
      depth++
      const tagEnd = text.indexOf('>', pos)
      pos = tagEnd === -1 ? text.length : tagEnd + 1
    } else if (text.startsWith(rule.close, pos)) {
      depth--
      if (depth > 0) pos += rule.close.length
    } else {
      pos++
    }
  }

  return {
    rule,
    ...open,
    content: text.slice(open.contentStart, pos),
    end: pos + rule.close.length,
  }
}

function tokenizeLeaf(text: string, base: number, grammar: RichTextGrammar): RichTextToken[] {
  if (!grammar.breaks && !grammar.dropStrayClose) {
    return text ? [{ kind: 'text', value: text, index: base }] : []
  }

  const tokens: RichTextToken[] = []
  let chunkStart = 0
  let pos = 0

  const flush = () => {
    if (pos > chunkStart) {
      tokens.push({ kind: 'text', value: text.slice(chunkStart, pos), index: base + chunkStart })
    }
  }

  while (pos < text.length) {
    if (grammar.breaks && text[pos] === '\n') {
      flush()
      tokens.push({ kind: 'break', index: base + pos })
      pos++
      chunkStart = pos
      continue
    }

    const strayClose = grammar.dropStrayClose ? text.slice(pos).match(STRAY_CLOSE_RE) : null
    if (strayClose) {
      flush()
      pos += strayClose[0].length
      chunkStart = pos
      continue
    }

    pos++
  }

  flush()

  return tokens
}
