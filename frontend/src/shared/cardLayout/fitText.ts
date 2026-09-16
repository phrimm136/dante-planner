/**
 * Width of one string rendered at one font size, tracking included.
 *
 * Size and width are in whichever unit the caller fits in; the cards fit in percent of
 * the card root.
 */
export type Measure = (text: string, size: number) => number

/** The band a single-line name is scaled within. */
export interface ScaleSpec {
  max: number
  min: number
  width: number
}

/** The vertical metrics one line of a name is laid out from, in em. */
export interface LineMetrics {
  ascender: number
  descender: number
  lineHeight: number
}

/** The band, box and line metrics a wrapping name is fitted into. */
export interface FitSpec {
  max: number
  min: number
  step: number
  width: number
  height: number
  /** The node's `m_lineSpacing`, in em. */
  lineSpacingEm: number
  /** The metrics the faces drawing one line set. */
  metricsOf: (line: string) => LineMetrics
}

/** The lines a name is drawn as, and the size they are drawn at. */
export interface FittedText {
  fontSize: number
  lines: string[]
}

/**
 * Characters that may not end a line, from the game's `LineBreaking Leading Characters`.
 */
const LEADING = new Set('([｛〔〈《「『【〘〖〝‘“｟«$—…‥〳〴〵\\［（{£¥"々〇＄￥￦ #')

/**
 * Characters that may not begin a line, from the game's `LineBreaking Following Characters`.
 */
const FOLLOWING = new Set(
  ')]｝〕〉》」』】〙〗〟’”｠»ヽヾーァィゥェォッャュョヮヵヶぁぃぅぇぉっゃゅょゎゕゖㇰㇱㇲㇳㇴㇵㇶㇷㇸㇹㇺㇻㇼㇽㇾㇿ々〻‐゠–〜?!‼⁇⁈⁉・、%,.:;。！？］）：；＝}¢°"†‡℃〆％，．',
)

/** Code points TextMeshPro may break between, with Hangul excluded by the modern rules. */
const EAST_ASIAN_RANGES: readonly (readonly [number, number])[] = [
  [0x2e81, 0x9ffe],
  [0xf901, 0xfafe],
  [0xfe31, 0xfe4e],
  [0xff01, 0xffee],
]

/** Code points that end a line wherever they appear. */
const SOFT_BREAK = new Set([0x20, 0x09, 0x200b, 0x2d, 0xad])

const SPACE = 0x20
const TAB = 0x09
const HYPHEN = 0x2d

function isEastAsian(codePoint: number): boolean {
  return EAST_ASIAN_RANGES.some(([low, high]) => codePoint >= low && codePoint <= high)
}

/**
 * The indices a line may end at, in TextMeshPro's `Normal` wrapping.
 *
 * Whitespace and hyphens break wherever they fall; a pair breaks when either side is an
 * East Asian character, unless the left one may not end a line or the right one may not
 * begin one. Hangul and Latin are kept whole, which is what
 * `m_UseModernHangulLineBreakingRules` selects.
 */
export function breakOpportunities(text: string): number[] {
  const points: number[] = []

  for (let index = 0; index < text.length - 1; index += 1) {
    const character = text[index] ?? ''
    const codePoint = character.codePointAt(0) ?? 0
    const nextCharacter = text[index + 1] ?? ''
    const nextCodePoint = nextCharacter.codePointAt(0) ?? 0

    if (SOFT_BREAK.has(codePoint)) {
      const previousCodePoint = index > 0 ? (text.codePointAt(index - 1) ?? 0) : 0
      if (codePoint === HYPHEN && (previousCodePoint === SPACE || previousCodePoint === TAB))
        continue
      points.push(index + 1)
      continue
    }

    if (!isEastAsian(codePoint) && !isEastAsian(nextCodePoint)) continue
    if (LEADING.has(character) || FOLLOWING.has(nextCharacter)) continue
    points.push(index + 1)
  }

  return points
}

const TRAILING_WHITESPACE = /\s+$/

/** The rendered width of one line, with its trailing whitespace hanging free of the track. */
function lineWidth(line: string, size: number, measure: Measure): number {
  return measure(line.replace(TRAILING_WHITESPACE, ''), size)
}

/** The lines a name takes at `size`, and whether a line overran the box on its own. */
export function wrapText(
  text: string,
  size: number,
  spec: Pick<FitSpec, 'width'>,
  measure: Measure,
): { lines: string[]; overran: boolean } {
  const points = breakOpportunities(text)
  const lines: string[] = []
  let start = 0
  let overran = false

  while (start < text.length) {
    if (lineWidth(text.slice(start), size, measure) <= spec.width) {
      lines.push(text.slice(start))
      break
    }

    const candidates = points.filter((point) => point > start)
    let chosen: number | undefined
    for (const point of candidates) {
      if (lineWidth(text.slice(start, point), size, measure) > spec.width) break
      chosen = point
    }

    if (chosen === undefined) {
      chosen = candidates[0] ?? text.length
      overran = true
    }

    lines.push(text.slice(start, chosen))
    start = chosen
  }

  return { lines: lines.map((line) => line.trim()), overran }
}

/**
 * Whether `lines` stand inside the box at `size`.
 *
 * The block runs from the first line's ascender, down each line's own pitch, to the
 * descender under the last baseline; every line takes the metrics of the faces that draw
 * it, so one hanja sets that line from the face that supplied it.
 */
function fitsHeight(lines: string[], size: number, spec: FitSpec): boolean {
  const metrics = lines.map((line) => spec.metricsOf(line))
  const first = metrics[0]
  const last = metrics[metrics.length - 1]
  if (first === undefined || last === undefined) return true

  let height = first.ascender + last.descender
  for (const line of metrics.slice(0, -1)) height += line.lineHeight + spec.lineSpacingEm
  return height * size <= spec.height
}

/**
 * The lines and size a wrapping name is drawn at.
 *
 * The size steps down from the band's top while a line overruns the box on its own or the
 * wrapped text stands taller than the box, and overflows at the floor rather than
 * shrinking further.
 */
export function fitText(text: string, spec: FitSpec, measure: Measure): FittedText {
  for (let size = spec.max; size > spec.min; size -= spec.step) {
    const { lines, overran } = wrapText(text, size, spec, measure)
    if (!overran && fitsHeight(lines, size, spec)) return { fontSize: size, lines }
  }
  return { fontSize: spec.min, lines: wrapText(text, spec.min, spec, measure).lines }
}

/**
 * The size a one-line name is drawn at.
 *
 * It scales by the ratio it overruns by, so it always fits until it hits the floor.
 */
export function fitFontSize(text: string, spec: ScaleSpec, measure: Measure): number {
  const width = measure(text, spec.max)
  if (width <= spec.width) return spec.max
  return Math.max(spec.min, (spec.max * spec.width) / width)
}
