import { fitText, type FitSpec, type FittedText } from './fitText'
import { createAdvanceMeasure, lineMetrics, type FontAdvanceTable } from './fontAdvances'

export interface TextNodeSpec {
  maxSize: number
  minSize: number
  letterSpacingEm: number
  wordSpacingEm: number
  lineSpacingEm: number
}

export interface TextBox {
  width: number
  height: number
}

export const NODE_FIT_STEP = 0.05

export function nodeFitSpec(table: FontAdvanceTable, node: TextNodeSpec, box: TextBox): FitSpec {
  return {
    max: node.maxSize,
    min: node.minSize,
    step: NODE_FIT_STEP,
    width: box.width,
    height: box.height,
    lineSpacingEm: node.lineSpacingEm,
    metricsOf: (line) => lineMetrics(table, line),
  }
}

export function fitNodeText(
  text: string,
  table: FontAdvanceTable,
  node: TextNodeSpec,
  box: TextBox,
): FittedText {
  const measure = createAdvanceMeasure(table, node)
  return fitText(text, nodeFitSpec(table, node, box), measure)
}

export function fitNodeLine(
  text: string,
  table: FontAdvanceTable,
  node: TextNodeSpec,
  width: number,
): number {
  const measure = createAdvanceMeasure(table, node)
  const steps = Math.floor((node.maxSize - node.minSize) / NODE_FIT_STEP)
  for (let step = 0; step < steps; step += 1) {
    const size = node.maxSize - step * NODE_FIT_STEP
    if (measure(text, size) <= width) return size
  }
  return node.minSize
}
