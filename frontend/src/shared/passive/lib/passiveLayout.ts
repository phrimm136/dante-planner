import type { CSSProperties } from 'react'

import { PASSIVE_BOX_UNITS } from '@/lib/constants'
import { clientLength } from '@/lib/utils'
import type { PlateGeometry } from '@/shared/gameText'

export interface PassiveBoxLayout {
  box: CSSProperties
  plateRow: CSSProperties
  plate: PlateGeometry
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
    plate: {
      height: clientLength(PLATE.HEIGHT),
      fontSize: clientLength(PLATE.FONT),
      paddingLeft: clientLength(PLATE.PAD_LEFT),
      paddingRight: clientLength(PLATE.PAD_RIGHT),
      paddingBottom: clientLength(0),
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
      marginTop: clientLength(HEADER_GAP),
      paddingLeft: clientLength(INFO.LEFT),
      paddingRight: clientLength(INFO.RIGHT),
    },
    flavor: {
      fontSize: clientLength(FLAVOR.FONT),
      marginTop: clientLength(FLAVOR.TOP),
      marginBottom: clientLength(FLAVOR.BOTTOM),
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
