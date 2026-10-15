import type { CSSProperties } from 'react'

import {
  createAdvanceMeasure,
  fitText,
  lineMetrics,
  wrapText,
  type CardSizePx,
  type FontAdvanceTable,
  type Measure,
  type TrackingSpec,
} from '@/shared/cardLayout'
import type {
  LayoutElement,
  LayoutImage,
  LayoutText,
  StartBuffCardLayout,
  StartBuffMiniCardLayout,
  TextElement,
  UnitRect,
} from '../schemas/StartBuffCardSchemas'
import type { ColorRun, EnhancementLevel } from '@/shared/gameText'
import { START_BUFF_SLOT_WIDTH_PX } from './cardLayout'

const TMP_SPACING_EM = 0.01

const AUTO_SIZE_STEP_UNITS = 0.05

const CANVAS_PIXELS_PER_UNIT = 100

const PERCENT = 100

export interface LayoutSpace {
  frame: UnitRect
  unitCqw: number
  leftCqw: number
  topCqw: number
}

export interface CardState {
  selected: boolean
  hovered: boolean
  hoveredButton: number | null
  enhancement: EnhancementLevel
  overflows: boolean
}

export type TextRun = ColorRun

export interface FittedLines {
  fontSize: number
  lines: string[]
}

export interface DescriptionLayout {
  fontSize: number
  lines: TextRun[][]
  baselines: number[]
  height: number
  overflows: boolean
}

export function startBuffSlotSize(canvas: UnitRect): CardSizePx {
  return {
    widthPx: START_BUFF_SLOT_WIDTH_PX,
    heightPx: Math.round((START_BUFF_SLOT_WIDTH_PX * canvas.h) / canvas.w),
  }
}

export function fitSpace(frame: UnitRect, slot: CardSizePx): LayoutSpace {
  const heightCqw = (slot.heightPx / slot.widthPx) * PERCENT
  const unitCqw = Math.min(PERCENT / frame.w, heightCqw / frame.h)
  return {
    frame,
    unitCqw,
    leftCqw: (PERCENT - frame.w * unitCqw) / 2,
    topCqw: (heightCqw - frame.h * unitCqw) / 2,
  }
}

export function unitsToCqw(units: number, space: LayoutSpace): string {
  return `${String(units * space.unitCqw)}cqw`
}

export function rectStyle(rect: UnitRect, space: LayoutSpace): CSSProperties {
  return {
    position: 'absolute',
    left: `${String(space.leftCqw + (rect.x - space.frame.x) * space.unitCqw)}cqw`,
    top: `${String(space.topCqw + (rect.y - space.frame.y) * space.unitCqw)}cqw`,
    width: unitsToCqw(rect.w, space),
    height: unitsToCqw(rect.h, space),
    maxWidth: 'none',
  }
}

export function focusedButton(enhancement: EnhancementLevel): number | null {
  return enhancement === 0 ? null : enhancement - 1
}

export function isShown(element: LayoutElement, state: CardState): boolean {
  const buttonIndex = 'buttonIndex' in element ? element.buttonIndex : null
  switch (element.shownWhen) {
    case 'always':
      return true
    case 'selected':
      return state.selected
    case 'hover':
      return buttonIndex === null ? state.hovered : state.hoveredButton === buttonIndex
    case 'focused':
      return buttonIndex !== null && focusedButton(state.enhancement) === buttonIndex
    case 'notFocused':
      return buttonIndex === null || focusedButton(state.enhancement) !== buttonIndex
    case 'scrollable':
      return element.role === 'descArrowBottom' && state.overflows
  }
}

export function shownCardElements(layout: StartBuffCardLayout, state: CardState): LayoutElement[] {
  const iconsSeen = new Map<number, number>()
  return layout.elements.filter((element) => {
    if (!isShown(element, state)) return false
    if (element.role !== 'levelIcon') return true
    const seen = iconsSeen.get(element.buttonIndex) ?? 0
    iconsSeen.set(element.buttonIndex, seen + 1)
    return seen < (layout.levelButtons.iconCounts[element.buttonIndex] ?? 0)
  })
}

export function spriteFile(image: LayoutImage, baseId: number): string | null {
  if (image.file !== null) return image.file
  if (image.filePattern !== undefined) return image.filePattern.replace('{baseId}', String(baseId))
  return null
}

export function levelIconFile(
  layout: StartBuffMiniCardLayout | StartBuffCardLayout,
  enhancement: EnhancementLevel,
): string | null {
  if (enhancement === 1) return layout.levelIcons['1']
  if (enhancement === 2) return layout.levelIcons['2']
  return null
}

export function imageStyle(
  image: LayoutImage,
  rect: UnitRect,
  space: LayoutSpace,
  src: string,
): CSSProperties {
  const box = rectStyle(rect, space)
  const slice = image.slice
  if (slice === null) return { ...box, objectFit: image.preserveAspect ? 'contain' : 'fill' }
  if (image.pixelsPerUnit === null) {
    throw new Error(`sliced sprite ${src} carries no pixelsPerUnit`)
  }

  const unitsPerPixel = CANVAS_PIXELS_PER_UNIT / image.pixelsPerUnit
  const across = Math.min(1, rect.w / ((slice.left + slice.right) * unitsPerPixel))
  const down = Math.min(1, rect.h / ((slice.top + slice.bottom) * unitsPerPixel))
  const widths = [
    slice.top * unitsPerPixel * down,
    slice.right * unitsPerPixel * across,
    slice.bottom * unitsPerPixel * down,
    slice.left * unitsPerPixel * across,
  ]
    .map((units) => unitsToCqw(units, space))
    .join(' ')

  return {
    ...box,
    boxSizing: 'border-box',
    borderStyle: 'solid',
    borderColor: 'transparent',
    borderWidth: widths,
    borderImage: `url('${src}') ${String(slice.top)} ${String(slice.right)} ${String(slice.bottom)} ${String(slice.left)} fill / ${widths} stretch`,
  }
}

export function textTracking(text: LayoutText): TrackingSpec {
  return {
    letterSpacingEm: text.characterSpacing * TMP_SPACING_EM,
    wordSpacingEm: text.wordSpacing * TMP_SPACING_EM,
  }
}

export function lineSpacingEm(text: LayoutText): number {
  return text.lineSpacing * TMP_SPACING_EM
}

export function textElement(
  layout: StartBuffCardLayout | StartBuffMiniCardLayout,
  role: TextElement['role'],
): TextElement {
  const found = layout.elements.find(
    (element): element is TextElement => element.role === role && 'text' in element,
  )
  if (found === undefined) throw new Error(`${layout.prefab} has no ${role} text`)
  return found
}

export function viewportRect(layout: StartBuffCardLayout): UnitRect {
  const viewport = layout.elements.find((element) => element.role === 'descViewport')
  if (viewport === undefined) throw new Error(`${layout.prefab} has no description viewport`)
  return viewport.rect
}

export function fitElementText(
  text: string,
  element: TextElement,
  table: FontAdvanceTable,
): FittedLines {
  const node = element.text
  const measure = createAdvanceMeasure(table, textTracking(node))
  if (node.autoSize === null) {
    return {
      fontSize: node.fontSize,
      lines: wrapText(text, node.fontSize, { width: element.rect.w }, measure).lines,
    }
  }
  const [min, max] = node.autoSize
  return fitText(
    text,
    {
      max,
      min,
      step: AUTO_SIZE_STEP_UNITS,
      width: element.rect.w,
      height: element.rect.h,
      lineSpacingEm: lineSpacingEm(node),
      metricsOf: (line) => lineMetrics(table, line),
    },
    measure,
  )
}

export function costColor(layout: StartBuffCardLayout, level: number): string {
  return level > layout.cost.enhancedAboveLevel ? layout.cost.enhancedColor : layout.cost.color
}

export function bulletGlyph(layout: StartBuffCardLayout): string {
  return layout.description.bullet.prefix.trim()
}

function descriptionMeasure(layout: StartBuffCardLayout, table: FontAdvanceTable): Measure {
  const desc = textElement(layout, 'desc')
  return createAdvanceMeasure(
    table,
    textTracking(desc.text),
    new Map([[bulletGlyph(layout), layout.description.bullet.advanceEm]]),
  )
}

function sliceRuns(runs: readonly TextRun[], start: number, end: number): TextRun[] {
  const sliced: TextRun[] = []
  let offset = 0
  for (const run of runs) {
    const runEnd = offset + run.text.length
    const from = Math.max(start, offset)
    const to = Math.min(end, runEnd)
    if (from < to)
      sliced.push({ text: run.text.slice(from - offset, to - offset), color: run.color })
    offset = runEnd
  }
  return sliced
}

const LINE_FEED = '\n'

const WHITESPACE = /\s/

export function layoutDescription(
  runs: readonly TextRun[],
  layout: StartBuffCardLayout,
  table: FontAdvanceTable,
): DescriptionLayout {
  const desc = textElement(layout, 'desc')
  const fontSize = layout.description.fontSize
  const measure = descriptionMeasure(layout, table)
  const plain = runs.map((run) => run.text).join('')

  const lines: TextRun[][] = []
  const texts: string[] = []
  let segmentStart = 0
  for (const segment of plain.length === 0 ? [] : plain.split(LINE_FEED)) {
    const wrapped =
      segment.length === 0
        ? ['']
        : wrapText(segment, fontSize, { width: desc.rect.w }, measure).lines
    let cursor = segmentStart
    for (const line of wrapped) {
      while (WHITESPACE.test(plain[cursor] ?? '')) cursor += 1
      lines.push(sliceRuns(runs, cursor, cursor + line.length))
      texts.push(line)
      cursor += line.length
    }
    segmentStart += segment.length + LINE_FEED.length
  }

  const spacing = lineSpacingEm(desc.text)
  const metrics = texts.map((line) => lineMetrics(table, line))
  const baselines: number[] = []
  let baseline = (metrics[0]?.ascender ?? 0) * fontSize
  for (const lineMetric of metrics) {
    baselines.push(baseline)
    baseline += (lineMetric.lineHeight + spacing) * fontSize
  }
  const last = metrics[metrics.length - 1]
  const lastBaseline = baselines[baselines.length - 1]
  const height =
    last === undefined || lastBaseline === undefined ? 0 : lastBaseline + last.descender * fontSize

  return {
    fontSize,
    lines,
    baselines,
    height,
    overflows: height > viewportRect(layout).h,
  }
}

export function firstLineOffsetEm(
  table: FontAdvanceTable,
  line: string,
  spacingEm: number,
): number {
  const metrics = lineMetrics(table, line)
  return (metrics.ascender + metrics.descender - (metrics.lineHeight + spacingEm)) / 2
}
