import { z } from 'zod'

import type { Measure } from './fitText'

/** A face the display face hands a code point it has no glyph for. */
export const FaceFallbackSchema = z
  .object({
    face: z.string(),
    ascender: z.number(),
    descender: z.number(),
    capLine: z.number(),
    lineHeight: z.number(),
    /** The face's `normalSpacingOffset`, in em, added to the spacing after each glyph it draws. */
    normalSpacingOffset: z.number(),
    /** The code points this face is the first in the chain to supply. */
    codePoints: z.array(z.number()),
  })
  .strict()

/**
 * One display face's horizontal advances, in em, keyed by code point.
 *
 * Written by `static/scripts/font_advances.py` from the game's TMP font assets (KR, EN,
 * JP) and from the TTF the site serves for Chinese.
 */
export const FontAdvanceTableSchema = z
  .object({
    face: z.string(),
    source: z.string(),
    /** The face's ascender above the baseline, in em. */
    ascender: z.number(),
    /** The face's descender below the baseline, in em. */
    descender: z.number(),
    /** The face's cap line above the baseline, in em: the top of the drawn glyphs. */
    capLine: z.number(),
    /** The face's own line height, in em, before any node's `m_lineSpacing`. */
    lineHeight: z.number(),
    /** The face's `normalSpacingOffset`, in em, added to the spacing after each glyph it draws. */
    normalSpacingOffset: z.number(),
    /** The mean advance of the table, drawn for a code point it does not carry. */
    averageAdvance: z.number(),
    advances: z.record(z.string(), z.number()),
    /** The face's own fallback chain, in the order it hands a code point on. */
    fallbacks: z.array(FaceFallbackSchema),
  })
  .strict()

export type FontAdvanceTable = z.infer<typeof FontAdvanceTableSchema>

export type FaceFallback = z.infer<typeof FaceFallbackSchema>

/** The vertical metrics a name's lines are laid out from. */
export interface FaceMetrics {
  ascender: number
  descender: number
  capLine: number
  lineHeight: number
}

/**
 * How far a line block drops, in em, to sit on its box's midline rather than its own.
 *
 * TextMeshPro's `Midline` alignment centres the drawn geometry in the box; a CSS line box
 * centres its own leading, whose middle sits `(ascender - descender) / 2` below the
 * baseline while the glyphs' middle sits `capLine / 2` above it.
 */
export function midlineOffsetEm(face: FaceMetrics): number {
  return face.capLine / 2 - (face.ascender - face.descender) / 2
}

/** One face of a chain, and the code points it draws. */
interface ChainFace {
  face: FaceMetrics
  spacingOffsetEm: number
  covers: ReadonlySet<number>
}

const chainByTable = new WeakMap<FontAdvanceTable, readonly ChainFace[]>()

/** The display face and its fallbacks, in the order a code point is handed on. */
function faceChain(table: FontAdvanceTable): readonly ChainFace[] {
  const built = chainByTable.get(table)
  if (built !== undefined) return built

  const chain: ChainFace[] = [
    {
      face: table,
      spacingOffsetEm: table.normalSpacingOffset,
      covers: new Set(Object.keys(table.advances).map(Number)),
    },
    ...table.fallbacks.map((fallback) => ({
      face: fallback,
      spacingOffsetEm: fallback.normalSpacingOffset,
      covers: new Set(fallback.codePoints),
    })),
  ]
  chainByTable.set(table, chain)
  return chain
}

/**
 * The faces one line's glyphs are drawn from, the display face first.
 *
 * TextMeshPro lays a line out from the tallest of the font assets that drew it, so a line
 * carrying one hanja is set from the face that supplied it.
 */
export function lineFaces(table: FontAdvanceTable, line: string): readonly FaceMetrics[] {
  const codePoints: number[] = []
  for (const character of line) codePoints.push(character.codePointAt(0) ?? 0)
  const used = faceChain(table)
    .filter((entry) => codePoints.some((codePoint) => entry.covers.has(codePoint)))
    .map((entry) => entry.face)
  return used.length > 0 ? used : [table]
}

/** A line's metrics, and the gap the display face sets between it and the line above. */
export interface LineFaceMetrics extends FaceMetrics {
  lineGap: number
}

/**
 * The metrics a line is laid out from: the tallest of every face that draws it.
 *
 * The gap is the display face's alone: TextMeshPro reads it once from the node's own font
 * asset, whichever fallback face drew the line.
 */
export function lineMetrics(table: FontAdvanceTable, line: string): LineFaceMetrics {
  const tallest = lineFaces(table, line).reduce(
    (metrics, face) => ({
      ascender: Math.max(metrics.ascender, face.ascender),
      descender: Math.max(metrics.descender, face.descender),
      capLine: Math.max(metrics.capLine, face.capLine),
      lineHeight: Math.max(metrics.lineHeight, face.lineHeight),
    }),
    { ascender: 0, descender: 0, capLine: 0, lineHeight: 0 },
  )
  return { ...tallest, lineGap: table.lineHeight - table.ascender - table.descender }
}

/** The tracking a node applies, in em, as TextMeshPro's character and word spacing. */
export interface TrackingSpec {
  letterSpacingEm: number
  wordSpacingEm: number
}

/** Code point ranges whose glyphs occupy a full em in every face that draws them. */
const IDEOGRAPH_RANGES: readonly (readonly [number, number])[] = [
  [0x3400, 0x4dbf],
  [0x4e00, 0x9fff],
  [0xf900, 0xfaff],
  [0x20000, 0x2a6df],
]

/** A full em, the advance an ideograph the face has no glyph for is drawn at. */
const IDEOGRAPH_ADVANCE = 1

const SPACE = ' '

function isIdeograph(codePoint: number): boolean {
  return IDEOGRAPH_RANGES.some(([low, high]) => codePoint >= low && codePoint <= high)
}

/** The advance one code point takes in a table, in em. */
export function advanceOf(table: FontAdvanceTable, codePoint: number): number {
  const known = table.advances[String(codePoint)]
  if (known !== undefined) return known
  return isIdeograph(codePoint) ? IDEOGRAPH_ADVANCE : table.averageAdvance
}

/** The spacing offset of the face that draws a code point, the display face's when none does. */
function spacingOffsetOf(table: FontAdvanceTable, codePoint: number): number {
  const drawing = faceChain(table).find((entry) => entry.covers.has(codePoint))
  return drawing?.spacingOffsetEm ?? table.normalSpacingOffset
}

/** The tracking a node is drawn with: its own, plus the display face's spacing offset. */
export function faceTracking(table: FontAdvanceTable, spacing: TrackingSpec): TrackingSpec {
  return {
    letterSpacingEm: spacing.letterSpacingEm + table.normalSpacingOffset,
    wordSpacingEm: spacing.wordSpacingEm,
  }
}

/**
 * A measurer over a face's own advance table, tracking included.
 *
 * A string's width is the sum of its code points' advances, plus the spacing the pen moves
 * after every code point but the last: the letter spacing, the spacing offset of the face
 * that drew it, and the word spacing when it is a space. TextMeshPro tests a line's width
 * before it moves the pen past the glyph it is setting.
 */
export function createAdvanceMeasure(
  table: FontAdvanceTable,
  spacing: TrackingSpec,
  advanceOverrides: ReadonlyMap<string, number> = new Map(),
): Measure {
  return (text: string, px: number): number => {
    let widthEm = 0
    let previous: string | undefined

    for (const character of text) {
      if (previous !== undefined) widthEm += penSpacingEm(table, spacing, previous)
      widthEm +=
        advanceOverrides.get(character) ?? advanceOf(table, character.codePointAt(0) ?? 0)
      previous = character
    }

    return widthEm * px
  }
}

function penSpacingEm(table: FontAdvanceTable, spacing: TrackingSpec, character: string): number {
  const offset = spacingOffsetOf(table, character.codePointAt(0) ?? 0)
  const word = character === SPACE ? spacing.wordSpacingEm : 0
  return spacing.letterSpacingEm + offset + word
}
