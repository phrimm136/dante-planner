import type { CSSProperties } from 'react'

import { PANEL_TEXT_NODES, PASSIVE_BOX_UNITS, UNIT_PANEL_COLORS } from '@/lib/constants'
import { clientLength } from '@/lib/utils'
import { textBlockStyle, textBlockTrim, trimmedLength } from '@/shared/skill'

export interface PassiveBoxLayout {
  box: CSSProperties
  plateRow: CSSProperties
  sinRow: CSSProperties
  sinItem: CSSProperties
  sinIcon: CSSProperties
  sinTimes: CSSProperties
  sinCount: CSSProperties
  sinLabel: CSSProperties
  info: CSSProperties
  flavor: CSSProperties
  lock: CSSProperties
}

export function passiveBoxLayout(): PassiveBoxLayout {
  const { MIN_HEIGHT, PLATE, HEADER_GAP, SIN_ROW, INFO, FLAVOR, LOCK } = PASSIVE_BOX_UNITS
  const { PASSIVE_INFO, PASSIVE_FLAVOR } = PANEL_TEXT_NODES
  const square = (units: number): CSSProperties => ({
    width: clientLength(units),
    height: clientLength(units),
  })
  return {
    box: { minHeight: clientLength(MIN_HEIGHT) },
    plateRow: {
      marginLeft: clientLength(PLATE.LEFT),
      marginRight: clientLength(PLATE.RIGHT_MIN),
    },
    sinRow: {
      marginTop: clientLength(HEADER_GAP),
      marginLeft: clientLength(SIN_ROW.LEFT),
      height: clientLength(SIN_ROW.HEIGHT),
    },
    sinItem: { minWidth: clientLength(SIN_ROW.ITEM_MIN) },
    sinIcon: square(SIN_ROW.ICON),
    sinTimes: square(SIN_ROW.TIMES),
    sinCount: {
      fontSize: clientLength(SIN_ROW.COUNT_FONT),
      lineHeight: clientLength(SIN_ROW.COUNT_FONT),
    },
    sinLabel: {
      fontSize: clientLength(SIN_ROW.LABEL_FONT),
      marginLeft: clientLength(SIN_ROW.LABEL_LEFT),
    },
    info: {
      ...textBlockStyle(PASSIVE_INFO, UNIT_PANEL_COLORS.PASSIVE_INFO),
      marginTop: trimmedLength(HEADER_GAP, PASSIVE_INFO),
      marginBottom: textBlockTrim(PASSIVE_INFO),
      paddingLeft: clientLength(INFO.LEFT),
      paddingRight: clientLength(INFO.RIGHT),
    },
    flavor: {
      ...textBlockStyle(PASSIVE_FLAVOR, UNIT_PANEL_COLORS.FLAVOR),
      fontSize: clientLength(FLAVOR.FONT),
      marginTop: trimmedLength(FLAVOR.TOP, PASSIVE_FLAVOR, FLAVOR.FONT),
      marginBottom: trimmedLength(FLAVOR.BOTTOM, PASSIVE_FLAVOR, FLAVOR.FONT),
      paddingLeft: clientLength(FLAVOR.LEFT),
      paddingRight: clientLength(FLAVOR.RIGHT),
    },
    lock: {
      right: clientLength(LOCK.RIGHT),
      bottom: clientLength(LOCK.BOTTOM),
      width: clientLength(LOCK.WIDTH),
      height: clientLength(LOCK.HEIGHT),
      opacity: LOCK.ALPHA,
    },
  }
}
