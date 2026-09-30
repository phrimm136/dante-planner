import { ACCENT_COLORS } from '@/lib/constants'

export interface TmpMark {
  rgb: string
  alphaPct: string
}

export interface TmpRun {
  kind: 'run'
  text: string
  color?: string
  mark?: TmpMark
  bold: boolean
  underline: boolean
  strike: boolean
  decorationColor?: string
  fontSize?: string
}

export interface TmpBreak {
  kind: 'break'
}

export type TmpToken = TmpRun | TmpBreak

type Rgba = readonly [number, number, number, number]

type Unit = 'px' | 'em' | '%'

type ValueType = 'none' | 'number' | 'color' | 'string'

interface Attribute {
  name: string
  valueType: ValueType
  value: string
  started: boolean
}

interface Tag {
  chars: string
  end: number
  attributes: Attribute[]
  unit: Unit
}

const MAX_TAG_CHARS = 128
const MAX_MARKUP_NAME_CHARS = 16
const OPAQUE = 255
const PERCENT_DECIMALS = 3
const SIZE_DECIMALS = 4
const MAX_TAG_VALUE = 32767

const NAMED_COLORS: Record<string, Rgba> = {
  RED: [255, 0, 0, 255],
  BLUE: [0, 0, 255, 255],
  BLACK: [0, 0, 0, 255],
  GREEN: [0, 255, 0, 255],
  WHITE: [255, 255, 255, 255],
  ORANGE: [255, 128, 0, 255],
  PURPLE: [160, 32, 240, 255],
  YELLOW: [255, 235, 4, 255],
}

const DEFAULT_MARK: Rgba = [255, 255, 0, 64]
const WHITE: Rgba = [255, 255, 255, 255]

const HIGHLIGHT_STYLE = 'UPGRADEHIGHLIGHT'

const ESCAPES: Record<string, string> = { '\\': '\\', n: '\n' }

class TagStack<T> {
  private readonly items: T[]
  private index = 1

  constructor(base: T) {
    this.items = [base]
  }

  add(item: T): void {
    this.items[this.index] = item
    this.index += 1
  }

  remove(): T {
    this.index -= 1
    if (this.index <= 0) {
      this.index = 1
      return this.base()
    }
    return this.top()
  }

  top(): T {
    return this.items[this.index - 1] ?? this.base()
  }

  private base(): T {
    return this.items[0] as T
  }
}

function hexDigit(char: string | undefined): number {
  const value = Number.parseInt(char ?? '', 16)
  return Number.isNaN(value) ? 15 : value
}

function hexByte(high: string | undefined, low: string | undefined): number {
  return hexDigit(high) * 16 + hexDigit(low)
}

function tagHexColor(chars: string, count: number): Rgba {
  const at = (index: number) => chars[index]
  switch (count) {
    case 10:
      return [hexByte(at(7), at(7)), hexByte(at(8), at(8)), hexByte(at(9), at(9)), OPAQUE]
    case 11:
      return [
        hexByte(at(7), at(7)),
        hexByte(at(8), at(8)),
        hexByte(at(9), at(9)),
        hexByte(at(10), at(10)),
      ]
    case 13:
      return [hexByte(at(7), at(8)), hexByte(at(9), at(10)), hexByte(at(11), at(12)), OPAQUE]
    case 15:
      return [
        hexByte(at(7), at(8)),
        hexByte(at(9), at(10)),
        hexByte(at(11), at(12)),
        hexByte(at(13), at(14)),
      ]
    default:
      return WHITE
  }
}

function shortHexColor(chars: string): Rgba {
  const at = (index: number) => chars[index]
  switch (chars.length) {
    case 4:
      return [hexByte(at(1), at(1)), hexByte(at(2), at(2)), hexByte(at(3), at(3)), OPAQUE]
    case 5:
      return [
        hexByte(at(1), at(1)),
        hexByte(at(2), at(2)),
        hexByte(at(3), at(3)),
        hexByte(at(4), at(4)),
      ]
    case 7:
      return [hexByte(at(1), at(2)), hexByte(at(3), at(4)), hexByte(at(5), at(6)), OPAQUE]
    default:
      return [
        hexByte(at(1), at(2)),
        hexByte(at(3), at(4)),
        hexByte(at(5), at(6)),
        hexByte(at(7), at(8)),
      ]
  }
}

function attributeHexColor(value: string): Rgba {
  const at = (index: number) => value[index]
  if (value.length === 7) {
    return [hexByte(at(1), at(2)), hexByte(at(3), at(4)), hexByte(at(5), at(6)), OPAQUE]
  }
  if (value.length === 9) {
    return [
      hexByte(at(1), at(2)),
      hexByte(at(3), at(4)),
      hexByte(at(5), at(6)),
      hexByte(at(7), at(8)),
    ]
  }
  return WHITE
}

function isNumberStart(char: string): boolean {
  return char === '+' || char === '-' || char === '.' || (char >= '0' && char <= '9')
}

function newAttribute(): Attribute {
  return { name: '', valueType: 'none', value: '', started: false }
}

function readTag(text: string, start: number): Tag | null {
  const attributes: Attribute[] = [newAttribute()]
  let chars = ''
  let flag = 0
  let isTagSet = false
  let valueType: ValueType = 'none'
  let unit: Unit = 'px'

  const current = () => attributes[attributes.length - 1] as Attribute
  const nextAttribute = () => {
    valueType = 'none'
    attributes.push(newAttribute())
  }

  for (let i = start; i < text.length && chars.length < MAX_TAG_CHARS; i++) {
    const char = text[i] as string
    if (char === '<' || char === '\0') return null
    if (char === '>') return { chars, end: i, attributes, unit }

    chars += char

    if (flag === 1) {
      const attribute = current()
      if (valueType === 'none') {
        unit = 'px'
        attribute.started = true
        if (isNumberStart(char)) {
          valueType = attribute.valueType = 'number'
          attribute.value += char
        } else if (char === '#') {
          valueType = attribute.valueType = 'color'
          attribute.value += char
        } else if (char === '"') {
          valueType = attribute.valueType = 'string'
        } else {
          valueType = attribute.valueType = 'string'
          attribute.value += char
        }
      } else if (valueType === 'number') {
        if (char === 'p' || char === 'e' || char === '%' || char === ' ') {
          flag = 2
          unit = char === 'e' ? 'em' : char === '%' ? '%' : 'px'
          nextAttribute()
        } else {
          attribute.value += char
        }
      } else if (valueType === 'color' ? char !== ' ' : char !== '"') {
        attribute.value += char
      } else {
        flag = 2
        unit = 'px'
        nextAttribute()
      }
    }

    if (char === '=') flag = 1

    if (flag === 0 && char === ' ') {
      if (isTagSet) return null
      isTagSet = true
      flag = 2
      unit = 'px'
      nextAttribute()
    }

    if (flag === 0) current().name += char.toUpperCase()

    if (flag === 2 && char === ' ') flag = 0
  }

  return null
}

function markupName(text: string, start: number): string {
  let name = ''
  for (let i = start; i < start + MAX_MARKUP_NAME_CHARS && i < text.length; i++) {
    const char = text[i] as string
    if (char === '>' || char === '=' || char === ' ') break
    name += char.toUpperCase()
  }
  return name
}

function tagFloat(attribute: Attribute): number | null {
  if (!attribute.started) return null
  const chars = attribute.value
  let i = 0
  let sign = 1
  if (chars[0] === '+') i = 1
  else if (chars[0] === '-') {
    sign = -1
    i = 1
  }

  let value = 0
  let isInteger = true
  let multiplier = 0
  for (; i < chars.length; i++) {
    const char = chars[i] as string
    if (char === ',') break
    if (char === '.') {
      isInteger = false
      multiplier = 0.1
    } else if (char >= '0' && char <= '9') {
      const digit = Number(char)
      if (isInteger) value = value * 10 + digit * sign
      else {
        value += digit * multiplier * sign
        multiplier *= 0.1
      }
    }
  }

  return value > MAX_TAG_VALUE ? null : value
}

function hex2(value: number): string {
  return value.toString(16).padStart(2, '0')
}

function cssColor([r, g, b, a]: Rgba): string {
  return `#${hex2(r)}${hex2(g)}${hex2(b)}${a === OPAQUE ? '' : hex2(a)}`
}

function withAlphaCap(color: Rgba, cap: Rgba | null): Rgba {
  const limit = cap?.[3] ?? OPAQUE
  return [color[0], color[1], color[2], Math.min(limit, color[3])]
}

function decodeEscapes(text: string): string {
  return text.replace(/\\([\\n])/g, (_, char: string) => ESCAPES[char] ?? char)
}

class TmpState {
  color = new TagStack<Rgba | null>(null)
  underlineColor = new TagStack<Rgba | null>(null)
  strikeColor = new TagStack<Rgba | null>(null)
  highlight = new TagStack<Rgba | null>(null)
  size = new TagStack<string | undefined>(undefined)
  styles: string[] = []
  bold = 0
  underline = 0
  strike = 0
  marks = 0
  noParse = false

  run(text: string): TmpRun {
    const color = this.color.top()
    const mark = this.marks > 0 ? this.highlight.top() : null
    const decoration =
      this.underline > 0
        ? this.underlineColor.top()
        : this.strike > 0
          ? this.strikeColor.top()
          : null
    const fontSize = this.size.top()

    return {
      kind: 'run',
      text,
      ...(color && { color: cssColor(color) }),
      ...(mark && {
        mark: {
          rgb: cssColor([mark[0], mark[1], mark[2], OPAQUE]),
          alphaPct: `${((mark[3] / OPAQUE) * 100).toFixed(PERCENT_DECIMALS)}%`,
        },
      }),
      bold: this.bold > 0,
      underline: this.underline > 0,
      strike: this.strike > 0,
      ...(decoration && { decorationColor: cssColor(decoration) }),
      ...(fontSize !== undefined && { fontSize }),
    }
  }

  decorationOpen(stack: TagStack<Rgba | null>, tag: Tag): void {
    const attribute = tag.attributes[1]
    const current = this.color.top()
    stack.add(
      attribute?.name === 'COLOR'
        ? withAlphaCap(attributeHexColor(attribute.value), current)
        : current,
    )
  }

  openStyle(name: string): void {
    this.styles.push(name)
    if (name === HIGHLIGHT_STYLE) this.color.add(shortHexColor(ACCENT_COLORS.ENHANCED))
  }

  closeStyle(): void {
    if (this.styles.pop() === HIGHLIGHT_STYLE) this.color.remove()
  }

  apply(tag: Tag): boolean {
    const name = tag.attributes[0]?.name ?? ''
    if (this.noParse && name !== '/NOPARSE') return false
    if (name === '/NOPARSE') {
      this.noParse = false
      return true
    }

    if (tag.chars[0] === '#' && [4, 5, 7, 9].includes(tag.chars.length)) {
      this.color.add(shortHexColor(tag.chars))
      return true
    }

    switch (name) {
      case 'B':
        this.bold += 1
        return true
      case '/B':
        this.bold = Math.max(0, this.bold - 1)
        return true
      case 'I':
      case '/I':
        return true
      case 'S':
        this.strike += 1
        this.decorationOpen(this.strikeColor, tag)
        return true
      case '/S':
        this.strike = Math.max(0, this.strike - 1)
        this.strikeColor.remove()
        return true
      case 'U':
        this.underline += 1
        this.decorationOpen(this.underlineColor, tag)
        return true
      case '/U':
        this.underline = Math.max(0, this.underline - 1)
        this.underlineColor.remove()
        return true
      case 'MARK':
        this.marks += 1
        this.highlight.add(withAlphaCap(this.markColor(tag), this.color.top()))
        return true
      case '/MARK':
        this.highlight.remove()
        this.marks = Math.max(0, this.marks - 1)
        return true
      case 'SIZE':
        return this.openSize(tag)
      case '/SIZE':
        this.size.remove()
        return true
      case 'COLOR':
        return this.openColor(tag)
      case '/COLOR':
        this.color.remove()
        return true
      case 'NOPARSE':
        this.noParse = true
        return true
      default:
        return false
    }
  }

  private markColor(tag: Tag): Rgba {
    let color = DEFAULT_MARK
    for (const attribute of tag.attributes) {
      if (attribute.name === '') break
      if (attribute.name === 'MARK' && attribute.valueType === 'color') {
        color = attributeHexColor(tag.attributes[0]?.value ?? '')
      } else if (attribute.name === 'COLOR') {
        color = attributeHexColor(attribute.value)
      }
    }
    return color
  }

  private openColor(tag: Tag): boolean {
    if (tag.chars[6] === '#' && [10, 11, 13, 15].includes(tag.chars.length)) {
      this.color.add(tagHexColor(tag.chars, tag.chars.length))
      return true
    }
    const value = tag.attributes[0]
    const named =
      value?.valueType === 'string' ? NAMED_COLORS[value.value.toUpperCase()] : undefined
    if (named === undefined) return false
    this.color.add(named)
    return true
  }

  private openSize(tag: Tag): boolean {
    const value = tag.attributes[0] && tagFloat(tag.attributes[0])
    if (value === null || value === undefined) return false
    const rounded = Number(value.toFixed(SIZE_DECIMALS))
    if (tag.unit === '%') this.size.add(`${String(rounded)}%`)
    else if (tag.unit === 'em') this.size.add(`${String(rounded)}em`)
    else this.size.add(undefined)
    return true
  }
}

function sameState(a: TmpRun, b: TmpRun): boolean {
  return (
    a.color === b.color &&
    a.mark?.rgb === b.mark?.rgb &&
    a.mark?.alphaPct === b.mark?.alphaPct &&
    a.bold === b.bold &&
    a.underline === b.underline &&
    a.strike === b.strike &&
    a.decorationColor === b.decorationColor &&
    a.fontSize === b.fontSize
  )
}

export function scanTmpRuns(input: string): TmpToken[] {
  const text = decodeEscapes(input)
  const state = new TmpState()
  const tokens: TmpToken[] = []

  const emit = (chars: string) => {
    const run = state.run(chars)
    const last = tokens[tokens.length - 1]
    if (last?.kind === 'run' && sameState(last, run)) last.text += chars
    else tokens.push(run)
  }

  let i = 0
  while (i < text.length) {
    const char = text[i] as string

    if (char === '\n') {
      tokens.push({ kind: 'break' })
      i += 1
      continue
    }

    if (char === '<') {
      const name = state.noParse ? '' : markupName(text, i + 1)
      if (name === 'BR') {
        tokens.push({ kind: 'break' })
        i += 4
        continue
      }
      if (name === '/STYLE') {
        state.closeStyle()
        i += 8
        continue
      }
      const styleEnd = name === 'STYLE' ? text.indexOf('>', i + 7) : -1
      if (styleEnd !== -1) {
        state.openStyle(
          text
            .slice(i + 7, styleEnd)
            .replaceAll('"', '')
            .toUpperCase(),
        )
        i = styleEnd + 1
        continue
      }

      const tag = readTag(text, i + 1)
      if (tag && state.apply(tag)) {
        i = tag.end + 1
        continue
      }
    }

    emit(char)
    i += 1
  }

  return tokens
}

export function tmpPlainText(input: string): string {
  return scanTmpRuns(input)
    .map((token) => (token.kind === 'run' ? token.text : ' '))
    .join('')
}

export interface ColorRun {
  text: string
  color: string | undefined
}

export function colorRuns(input: string): ColorRun[] {
  const runs: ColorRun[] = []
  const append = (text: string, color: string | undefined) => {
    const last = runs[runs.length - 1]
    if (last && last.color === color) last.text += text
    else runs.push({ text, color })
  }
  for (const token of scanTmpRuns(input)) {
    if (token.kind === 'run') append(token.text, token.color)
    else append('\n', runs[runs.length - 1]?.color)
  }
  return runs
}
