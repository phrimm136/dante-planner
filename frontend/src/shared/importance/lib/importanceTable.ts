import type { CSSProperties } from 'react'
import type { z } from 'zod'

import importanceColorCode from '@static/data/color/importanceColorCode.json'
import { IMPORTANCE_DECORATION, IMPORTANCE_STRIPE_SCROLL_S } from '@/lib/constants'
import { ImportanceColorCodeSchema, PASSIVE_IMPORTANCE_LEVELS } from '@/shared/gameData'
import type { ImportancePalette, ImportanceTier } from '@/shared/gameData'

type ImportanceColorRoles = z.infer<
  typeof ImportanceColorCodeSchema
>[ImportancePalette][ImportanceTier]

const IMPORTANCE_COLORS = ImportanceColorCodeSchema.parse(importanceColorCode)

export interface ImportanceStyle {
  tier: ImportanceTier
  iconAlpha: number
  hasGlow: boolean
  flickerPeriodS: number
  stripeBlinkAlpha: readonly [number, number]
  colors: ImportanceColorRoles
}

export function toImportanceTier(level: number): ImportanceTier | undefined {
  return PASSIVE_IMPORTANCE_LEVELS.find((tier) => tier === String(level))
}

export function hasImportance(level: number): boolean {
  return toImportanceTier(level) !== undefined
}

export function importanceStyle(
  level: number,
  palette: ImportancePalette,
): ImportanceStyle | undefined {
  const tier = toImportanceTier(level)
  if (tier === undefined) return undefined
  return {
    tier,
    iconAlpha: IMPORTANCE_DECORATION.ICON_ALPHA[tier],
    hasGlow: level >= IMPORTANCE_DECORATION.GLOW_MIN_LEVEL,
    flickerPeriodS: IMPORTANCE_DECORATION.FLICKER_PERIOD_S[tier],
    stripeBlinkAlpha: IMPORTANCE_DECORATION.STRIPE_BLINK_ALPHA[tier],
    colors: IMPORTANCE_COLORS[palette][tier],
  }
}

export function stripeTileWidth(): string {
  const { WIDTH, HEIGHT } = IMPORTANCE_DECORATION.STRIPE_TILE
  return `calc(var(--band-h) * ${String(WIDTH)} / ${String(HEIGHT)})`
}

export function importanceCssVariables(style: ImportanceStyle): CSSProperties {
  const [blinkMin, blinkMax] = style.stripeBlinkAlpha
  return {
    '--importance-icon-alpha': style.iconAlpha,
    '--importance-flicker-period': `${String(style.flickerPeriodS)}s`,
    '--importance-glow-fade': `${String(IMPORTANCE_DECORATION.GLOW_FADE_S)}s`,
    '--importance-blink-min': blinkMin,
    '--importance-blink-max': blinkMax,
    '--importance-stripe-color': style.colors.stripe,
    '--importance-stripe-scroll': `${String(IMPORTANCE_STRIPE_SCROLL_S)}s`,
    '--importance-band-height': `${String(IMPORTANCE_DECORATION.STRIPE_BAND_HEIGHT_PERCENT)}%`,
    '--importance-panel': style.colors.passivePanel,
    '--importance-panel-glow': style.colors.passiveGlow,
    '--importance-panel-pulse': `${String(IMPORTANCE_DECORATION.PANEL_GLOW_PULSE_S)}s`,
    '--importance-panel-glow-blur': `${String(IMPORTANCE_DECORATION.PANEL_GLOW_BLUR_PX)}px`,
  } as CSSProperties
}

export function importanceNameStyle(
  level: number,
  palette: ImportancePalette,
): CSSProperties | undefined {
  const style = importanceStyle(level, palette)
  if (style === undefined) return undefined
  return { '--importance-name': style.colors.passiveName } as CSSProperties
}
