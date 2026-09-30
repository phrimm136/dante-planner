import type { CSSProperties } from 'react'

import { CLIENT_TEXT_SCALE, PANEL_TEXT_FACE } from '@/lib/constants'
import { clientLength } from '@/lib/utils'

export interface PanelTextNode {
  LINE_SPACING_EM: number
  LETTER_SPACING_EM: number
  WORD_SPACING_EM: number
}

export function textBlockPitchEm(node: PanelTextNode): number {
  return PANEL_TEXT_FACE.PITCH_EM + node.LINE_SPACING_EM
}

export function textBlockTrimUnits(
  node: PanelTextNode,
  fontUnits: number = CLIENT_TEXT_SCALE.FONT_UNITS,
): number {
  return ((textBlockPitchEm(node) - PANEL_TEXT_FACE.FIRST_LINE_EM) / 2) * fontUnits
}

export function textBlockStyle(node: PanelTextNode, color?: string): CSSProperties {
  return {
    lineHeight: Number(textBlockPitchEm(node).toFixed(4)),
    letterSpacing: `${String(node.LETTER_SPACING_EM)}em`,
    wordSpacing: `${String(node.WORD_SPACING_EM)}em`,
    ...(color !== undefined && { color }),
  }
}

export function textBlockTrim(
  node: PanelTextNode,
  fontUnits: number = CLIENT_TEXT_SCALE.FONT_UNITS,
): string {
  return clientLength(-textBlockTrimUnits(node, fontUnits))
}

export function trimmedLength(
  units: number,
  node: PanelTextNode,
  fontUnits: number = CLIENT_TEXT_SCALE.FONT_UNITS,
): string {
  return clientLength(units - textBlockTrimUnits(node, fontUnits))
}
