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
  /** The display face's gap below its descender, whichever faces drew the line. */
  lineGap: number
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
 * The distance from one line's baseline to the next, in em.
 *
 * TextMeshPro drops each new line by the deepest descender above it plus the tallest
 * ascender on it, then the display face's line gap and the node's line spacing, so a hanja
 * on either line deepens the step by its fallback face's own ascender or descender.
 */
export function linePitch(above: LineMetrics, below: LineMetrics, lineSpacingEm: number): number {
  return above.descender + below.ascender + below.lineGap + lineSpacingEm
}

/**
 * Whether `lines` stand inside the box at `size`.
 *
 * The block runs from the first line's ascender, down each pitch, to the descender under
 * the last baseline.
 */
function fitsHeight(lines: string[], size: number, spec: FitSpec): boolean {
  const metrics = lines.map((line) => spec.metricsOf(line))
  const first = metrics[0]
  const last = metrics[metrics.length - 1]
  if (first === undefined || last === undefined) return true

  let height = first.ascender + last.descender
  let above = first
  for (const below of metrics.slice(1)) {
    height += linePitch(above, below, spec.lineSpacingEm)
    above = below
  }
  return height * size <= spec.height
}

const AUTO_SIZE_MAX_PASSES = 100

const AUTO_SIZE_SETTLE_STEPS = 1.02

function roundToStep(size: number, spec: FitSpec): number {
  return spec.max + Math.floor((size - spec.max) / spec.step + 0.5) * spec.step
}

function overflows(fitted: { lines: string[]; overran: boolean }, size: number, spec: FitSpec) {
  return fitted.overran || !fitsHeight(fitted.lines, size, spec)
}

export function fitText(text: string, spec: FitSpec, measure: Measure): FittedText {
  let ceiling = spec.max
  let floor = spec.min
  let size = spec.max

  for (let pass = 0; pass < AUTO_SIZE_MAX_PASSES; pass += 1) {
    const fitted = wrapText(text, size, spec, measure)

    if (size > spec.min && overflows(fitted, size, spec)) {
      ceiling = size
      size = Math.max(roundToStep(size - Math.max((size - floor) / 2, spec.step), spec), spec.min)
      continue
    }
    if (ceiling - floor > spec.step * AUTO_SIZE_SETTLE_STEPS && size < spec.max) {
      floor = size
      size = Math.min(roundToStep(size + Math.max((ceiling - size) / 2, spec.step), spec), spec.max)
      continue
    }
    return { fontSize: size, lines: fitted.lines }
  }

  return { fontSize: size, lines: wrapText(text, size, spec, measure).lines }
}

export function fitFontSize(text: string, spec: ScaleSpec, measure: Measure): number {
  const width = measure(text, spec.max)
  if (width <= spec.width) return spec.max
  return Math.max(spec.min, (spec.max * spec.width) / width)
}
