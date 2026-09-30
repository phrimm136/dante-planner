import type { CSSProperties } from 'react'

import { SANITY_UNITS } from '@/lib/constants'
import { clientLength } from '@/lib/utils'

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
      fontSize: clientLength(PANIC.NAME.FONT),
      padding: `${clientLength(PANIC.NAME.PAD_TOP)} ${clientLength(PANIC.NAME.PAD_X)} ${clientLength(0)}`,
    },
    desc: {
      position: 'relative',
      top: clientLength(PANIC.DESC.SHIFT),
      marginLeft: clientLength(PANIC.DESC.LEFT),
      paddingTop: clientLength(PANIC.DESC.PAD_TOP),
    },
  }
}

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
      fontSize: clientLength(TAG.FONT),
    },
    list: {
      display: 'flex',
      flexDirection: 'column',
      rowGap: clientLength(CONDITION.ITEM_GAP),
      paddingTop: clientLength(DESC.TOP),
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
