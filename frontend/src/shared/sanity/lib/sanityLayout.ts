import type { CSSProperties } from 'react'

import { PANEL_TEXT_NODES, SANITY_UNITS, UNIT_PANEL_COLORS } from '@/lib/constants'
import { clientLength } from '@/lib/utils'
import type { TextBox, TextNodeSpec } from '@/shared/cardLayout'
import { textBlockStyle, textBlockTrim, trimmedLength } from '@/shared/skill'

export type SanityConditionKind = 'add' | 'min'

interface ClientBox {
  LEFT: number
  TOP: number
  WIDTH: number
  HEIGHT: number
}

function absoluteBox(box: ClientBox): CSSProperties {
  return {
    position: 'absolute',
    left: clientLength(box.LEFT),
    top: clientLength(box.TOP),
    width: clientLength(box.WIDTH),
    height: clientLength(box.HEIGHT),
  }
}

export interface PanicLayout {
  section: CSSProperties
  tagBg: CSSProperties
  tagText: CSSProperties
  icon: CSSProperties
  name: CSSProperties
  desc: CSSProperties
}

export function panicLayout(): PanicLayout {
  const { PANIC, SECTION_GAP } = SANITY_UNITS
  return {
    section: {
      position: 'relative',
      minHeight: clientLength(PANIC.MIN_HEIGHT),
      marginBottom: clientLength(SECTION_GAP),
    },
    tagBg: absoluteBox(PANIC.TAG_BG),
    tagText: {
      ...absoluteBox(PANIC.TAG_TEXT),
      fontSize: clientLength(PANIC.TAG_TEXT.FONT),
      whiteSpace: 'nowrap',
    },
    icon: absoluteBox(PANIC.ICON),
    name: {
      ...absoluteBox(PANIC.NAME),
      padding: `${clientLength(PANIC.NAME.PAD_TOP)} ${clientLength(PANIC.NAME.PAD_X)} ${clientLength(0)}`,
    },
    desc: {
      ...textBlockStyle(PANEL_TEXT_NODES.PANIC_DESC, UNIT_PANEL_COLORS.PANIC_DESC),
      position: 'relative',
      top: clientLength(PANIC.DESC.SHIFT),
      marginLeft: clientLength(PANIC.DESC.LEFT),
      paddingTop: trimmedLength(PANIC.DESC.PAD_TOP, PANEL_TEXT_NODES.PANIC_DESC),
      marginBottom: textBlockTrim(PANEL_TEXT_NODES.PANIC_DESC),
    },
  }
}

export const PANIC_NAME_NODE: TextNodeSpec = {
  maxSize: SANITY_UNITS.PANIC.NAME.FONT,
  minSize: SANITY_UNITS.PANIC.NAME.MIN_FONT,
  letterSpacingEm: SANITY_UNITS.PANIC.NAME.LETTER_SPACING_EM,
  wordSpacingEm: SANITY_UNITS.PANIC.NAME.WORD_SPACING_EM,
  lineSpacingEm: SANITY_UNITS.PANIC.NAME.LINE_SPACING_EM,
}

export const PANIC_TAG_NODE: TextNodeSpec = {
  maxSize: SANITY_UNITS.PANIC.TAG_TEXT.FONT,
  minSize: SANITY_UNITS.PANIC.TAG_TEXT.MIN_FONT,
  letterSpacingEm: SANITY_UNITS.PANIC.TAG_TEXT.LETTER_SPACING_EM,
  wordSpacingEm: SANITY_UNITS.PANIC.TAG_TEXT.WORD_SPACING_EM,
  lineSpacingEm: 0,
}

export const PANIC_TAG_TEXT_WIDTH =
  SANITY_UNITS.PANIC.TAG_TEXT.WIDTH - 2 * SANITY_UNITS.PANIC.TAG_TEXT.PAD_X

export const PANIC_NAME_BOX: TextBox = {
  width: SANITY_UNITS.PANIC.NAME.WIDTH - 2 * SANITY_UNITS.PANIC.NAME.PAD_X,
  height: SANITY_UNITS.PANIC.NAME.HEIGHT - SANITY_UNITS.PANIC.NAME.PAD_TOP,
}

export const CONDITION_TAG_NODE: TextNodeSpec = {
  maxSize: SANITY_UNITS.CONDITION.TAG.FONT,
  minSize: SANITY_UNITS.CONDITION.TAG.MIN_FONT,
  letterSpacingEm: SANITY_UNITS.CONDITION.TAG.LETTER_SPACING_EM,
  wordSpacingEm: SANITY_UNITS.CONDITION.TAG.WORD_SPACING_EM,
  lineSpacingEm: 0,
}

export const CONDITION_TAG_TEXT_WIDTH =
  SANITY_UNITS.CONDITION.TAG.MAX_WIDTH - 2 * SANITY_UNITS.CONDITION.TAG.PAD_X

const CONDITION_TEXT_NODES = {
  add: PANEL_TEXT_NODES.CONDITION_ADD,
  min: PANEL_TEXT_NODES.CONDITION_MIN,
} as const

export interface ConditionLayout {
  section: CSSProperties
  tag: CSSProperties
  list: CSSProperties
  icon: CSSProperties
}

export function conditionLayout(kind: SanityConditionKind): ConditionLayout {
  const { CONDITION, SECTION_GAP } = SANITY_UNITS
  const { TAG, DESC, ICON } = CONDITION
  return {
    section: {
      position: 'relative',
      minHeight: clientLength(CONDITION.MIN_HEIGHT),
      marginBottom: clientLength(SECTION_GAP),
    },
    tag: {
      position: 'absolute',
      left: clientLength(TAG.LEFT),
      top: clientLength(0),
      height: clientLength(TAG.HEIGHT[kind]),
      minWidth: clientLength(TAG.MIN_WIDTH),
      maxWidth: clientLength(TAG.MAX_WIDTH),
      paddingInline: clientLength(TAG.PAD_X),
      color: UNIT_PANEL_COLORS.CONDITION_TAG[kind],
    },
    list: {
      ...textBlockStyle(CONDITION_TEXT_NODES[kind]),
      display: 'flex',
      flexDirection: 'column',
      rowGap: clientLength(CONDITION.ITEM_GAP),
      paddingTop: trimmedLength(DESC.TOP, CONDITION_TEXT_NODES[kind]),
      marginBottom: textBlockTrim(CONDITION_TEXT_NODES[kind]),
      paddingRight: clientLength(DESC[kind].RIGHT),
      paddingLeft: clientLength(DESC[kind].LEFT),
    },
    icon: {
      position: 'absolute',
      right: clientLength(ICON.RIGHT[kind]),
      bottom: clientLength(0),
      width: clientLength(ICON.WIDTH),
      height: clientLength(ICON.HEIGHT[kind]),
      opacity: ICON.ALPHA,
    },
  }
}
