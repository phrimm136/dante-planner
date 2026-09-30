import type { CSSProperties } from 'react'

import { SKILL_LOCK_UNITS, SKILL_LOWER_UNITS, SKILL_UPPER_UNITS } from '@/lib/constants'
import { clientLength } from '@/lib/utils'
import type { PlateGeometry } from '@/shared/gameText'

const CENTRED = 'translate(-50%, -50%)'
const CENTRED_Y = 'translateY(-50%)'

export interface SkillUpperLayout {
  card: CSSProperties
  block: CSSProperties
  icon: CSSProperties
  attackIcon: CSSProperties
  coinScale: CSSProperties
  coinScaleBg: CSSProperties
  basePower: CSSProperties
  coins: CSSProperties
  nameArea: CSSProperties
  namePlate: PlateGeometry
  levelIcon: CSSProperties
  levelText: CSSProperties
  targetLabel: CSSProperties
  targetCount: CSSProperties
  sanityCost: CSSProperties
}

export interface SkillLowerLayout {
  card: CSSProperties
  block: CSSProperties
  lock: CSSProperties
}

function box(left: number, top: number, width: number, height: number): CSSProperties {
  return {
    left: clientLength(left),
    top: clientLength(top),
    width: clientLength(width),
    height: clientLength(height),
  }
}

function centredBox(centerX: number, centerY: number, width: number, height: number) {
  return { ...box(centerX, centerY, width, height), transform: CENTRED }
}

export function skillUpperLayout(): SkillUpperLayout {
  const { ICON, ATTACK_ICON, COIN_SCALE, COIN_BG, BASE_POWER, COINS, NAME_PLATE, LEVEL, TARGET } =
    SKILL_UPPER_UNITS
  return {
    card: { paddingTop: clientLength(SKILL_UPPER_UNITS.CARD_TOP) },
    block: { height: clientLength(SKILL_UPPER_UNITS.HEIGHT) },
    icon: box(ICON.LEFT, ICON.TOP, ICON.SIZE, ICON.SIZE),
    attackIcon: centredBox(
      ATTACK_ICON.CENTER_X,
      ATTACK_ICON.CENTER_Y,
      ATTACK_ICON.SIZE,
      ATTACK_ICON.SIZE,
    ),
    coinScale: {
      left: clientLength(COIN_SCALE.CENTER_X),
      top: clientLength(COIN_SCALE.CENTER_Y),
      transform: CENTRED,
      fontSize: clientLength(COIN_SCALE.FONT),
    },
    coinScaleBg: centredBox(COIN_BG.CENTER_X, COIN_BG.CENTER_Y, COIN_BG.WIDTH, COIN_BG.HEIGHT),
    basePower: {
      left: '0',
      width: clientLength(BASE_POWER.RIGHT_EDGE_X),
      top: clientLength(BASE_POWER.CENTER_Y),
      transform: CENTRED_Y,
      textAlign: 'right',
      fontSize: clientLength(BASE_POWER.FONT),
    },
    coins: { left: clientLength(COINS.LEFT), top: clientLength(COINS.TOP) },
    nameArea: {
      left: clientLength(NAME_PLATE.LEFT),
      top: clientLength(NAME_PLATE.TOP),
      right: clientLength(NAME_PLATE.RIGHT_MIN),
    },
    namePlate: {
      height: clientLength(NAME_PLATE.HEIGHT),
      fontSize: clientLength(NAME_PLATE.FONT),
      paddingLeft: clientLength(NAME_PLATE.PAD_LEFT),
      paddingRight: clientLength(NAME_PLATE.PAD_RIGHT),
      paddingBottom: clientLength(NAME_PLATE.PAD_BOTTOM),
    },
    levelIcon: box(LEVEL.ICON_LEFT, LEVEL.ICON_TOP, LEVEL.ICON_WIDTH, LEVEL.ICON_HEIGHT),
    levelText: {
      left: clientLength(LEVEL.TEXT_LEFT),
      top: clientLength(LEVEL.TEXT_CENTER_Y),
      transform: CENTRED_Y,
      fontSize: clientLength(LEVEL.FONT),
      maxWidth: clientLength(LEVEL.TEXT_MAX_WIDTH),
      textDecorationThickness: clientLength(LEVEL.UNDERLINE),
    },
    targetLabel: {
      ...box(TARGET.LEFT, TARGET.TOP, TARGET.WIDTH, TARGET.HEIGHT),
      fontSize: clientLength(TARGET.FONT),
    },
    targetCount: {
      ...box(TARGET.COUNT_LEFT, TARGET.TOP, TARGET.COUNT_WIDTH, TARGET.HEIGHT),
      fontSize: clientLength(TARGET.COUNT_FONT),
    },
    sanityCost: {
      left: clientLength(TARGET.LEFT),
      top: clientLength(TARGET.TOP + TARGET.HEIGHT),
      fontSize: clientLength(TARGET.FONT),
    },
  }
}

export function skillLowerLayout(hasImportance: boolean): SkillLowerLayout {
  const bottom = hasImportance
    ? SKILL_LOWER_UNITS.BOTTOM.importance
    : SKILL_LOWER_UNITS.BOTTOM.plain
  return {
    card: { paddingBottom: clientLength(bottom) },
    block: { paddingInline: clientLength(SKILL_LOWER_UNITS.INSET) },
    lock: {
      right: clientLength(SKILL_LOCK_UNITS.RIGHT),
      bottom: clientLength(SKILL_LOCK_UNITS.BOTTOM),
      width: clientLength(SKILL_LOCK_UNITS.WIDTH),
      height: clientLength(SKILL_LOCK_UNITS.HEIGHT),
      opacity: SKILL_LOCK_UNITS.ALPHA,
    },
  }
}
