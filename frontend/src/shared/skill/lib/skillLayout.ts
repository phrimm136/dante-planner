import type { CSSProperties } from 'react'

import {
  NAME_PLATE_UNITS,
  PART_ICON_UNITS,
  SKILL_LOCK_UNITS,
  SKILL_LOWER_UNITS,
  SKILL_UPPER_UNITS,
} from '@/lib/constants'
import { clientLength } from '@/lib/utils'
import type { AbPartType } from '@/shared/gameData'

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

function targetCountLineEm(): number {
  const { ASCENT, DESCENT } = SKILL_UPPER_UNITS.TARGET.COUNT_FACE_EM
  return Number((ASCENT + DESCENT).toFixed(6))
}

export function targetCountFontUnits(count: number): number {
  const {
    HEIGHT,
    COUNT_HEIGHT_DELTA,
    COUNT_MARGIN_TOP,
    COUNT_WIDTH,
    COUNT_FONT_MAX,
    COUNT_FONT_MIN,
  } = SKILL_UPPER_UNITS.TARGET
  const { SQUARE_ADVANCE } = SKILL_UPPER_UNITS.TARGET.COUNT_FACE_EM
  const spacing = SKILL_UPPER_UNITS.TARGET.COUNT_LETTER_SPACING_EM
  const heightBound = (HEIGHT + COUNT_HEIGHT_DELTA - COUNT_MARGIN_TOP) / targetCountLineEm()
  const widthBound = COUNT_WIDTH / (SQUARE_ADVANCE * count + spacing * (count - 1))
  const size = Math.min(COUNT_FONT_MAX, heightBound, widthBound)
  return Math.max(COUNT_FONT_MIN, size)
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
      fontFamily: SKILL_UPPER_UNITS.NUMERIC_FACE.FAMILY,
      fontSize: clientLength(COIN_SCALE.FONT),
      letterSpacing: `${String(SKILL_UPPER_UNITS.COIN_SCALE_LETTER_SPACING_EM)}em`,
    },
    coinScaleBg: centredBox(
      COIN_BG.CENTER_X,
      COIN_BG.CENTER_Y,
      COIN_BG.WIDTH,
      (COIN_BG.WIDTH * COIN_BG.SPRITE_HEIGHT) / COIN_BG.SPRITE_WIDTH,
    ),
    basePower: {
      left: '0',
      width: clientLength(BASE_POWER.RIGHT_EDGE_X),
      top: clientLength(BASE_POWER.CENTER_Y),
      transform: CENTRED_Y,
      textAlign: 'right',
      fontFamily: SKILL_UPPER_UNITS.NUMERIC_FACE.FAMILY,
      fontSize: clientLength(BASE_POWER.FONT),
    },
    coins: { left: clientLength(COINS.LEFT), top: clientLength(COINS.TOP) },
    nameArea: {
      left: clientLength(NAME_PLATE.LEFT),
      top: clientLength(NAME_PLATE.CENTER_Y - NAME_PLATE_UNITS.SKILL.HEIGHT / 2),
      right: clientLength(NAME_PLATE.RIGHT_MIN),
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
      left: clientLength(TARGET.COUNT_LEFT),
      top: clientLength(
        TARGET.TOP + TARGET.HEIGHT / 2 - TARGET.COUNT_POS_Y + TARGET.COUNT_MARGIN_TOP / 2,
      ),
      transform: CENTRED_Y,
      letterSpacing: `${String(TARGET.COUNT_LETTER_SPACING_EM)}em`,
      lineHeight: targetCountLineEm(),
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

export interface PartIconSlot {
  partType: AbPartType
  style: CSSProperties
}

export function partIconSlots(partTypes: readonly AbPartType[]): PartIconSlot[] {
  const { SIZE, RIGHT, ROW_PITCH, ROWS_PER_COLUMN, MAX_ICONS, COLOR, ALPHA } = PART_ICON_UNITS
  return partTypes.slice(0, MAX_ICONS).map((partType, index) => {
    const column = index < ROWS_PER_COLUMN ? 0 : 1
    const row = index - column * ROWS_PER_COLUMN
    return {
      partType,
      style: {
        right: clientLength(RIGHT[column]),
        top: clientLength(row * ROW_PITCH),
        width: clientLength(SIZE),
        height: clientLength(SIZE),
        backgroundColor: COLOR,
        opacity: ALPHA,
      },
    }
  })
}

export function partIconBoxFloor(count: number): CSSProperties | undefined {
  if (count === 0) return undefined
  const { ROW_PITCH, ROWS_PER_COLUMN, BOX_FLOOR_PAD } = PART_ICON_UNITS
  return { minHeight: clientLength(Math.min(count, ROWS_PER_COLUMN) * ROW_PITCH + BOX_FLOOR_PAD) }
}

export function partIconsMoveImportance(count: number): boolean {
  return count > PART_ICON_UNITS.ROWS_PER_COLUMN
}
