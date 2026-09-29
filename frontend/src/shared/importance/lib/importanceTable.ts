import type { CSSProperties } from 'react'
import type { z } from 'zod'

import importanceColorCode from '@static/data/color/importanceColorCode.json'
import {
  CLIENT_TEXT_SCALE,
  IMPORTANCE_BOX_LAYOUT,
  IMPORTANCE_ICON_UNITS,
  IMPORTANCE_PLATE_GLOW_UNITS,
  PASSIVE_PANE_BLEED_REM,
  IMPORTANCE_DECORATION,
  IMPORTANCE_STRIPE_BLINK_PERIOD_S,
  IMPORTANCE_STRIPE_SCROLL_S,
} from '@/lib/constants'
import { clientUnitsToRem } from '@/lib/utils'
import { ImportanceColorCodeSchema, PASSIVE_IMPORTANCE_LEVELS } from '@/shared/gameData'
import type { ImportancePalette, ImportanceTier } from '@/shared/gameData'

type ImportanceColorRoles = z.infer<
  typeof ImportanceColorCodeSchema
>[ImportancePalette][ImportanceTier]

const IMPORTANCE_COLORS = ImportanceColorCodeSchema.parse(importanceColorCode)

export type ImportanceVariant = 'skill' | 'passive'

type AlphaRange = readonly [number, number]

export interface ImportanceStyle {
  tier: ImportanceTier
  iconAlpha: number
  hasGlow: boolean
  flickerTarget: 'icon' | 'glow'
  flickerPeriodS: number
  flickerAlpha: AlphaRange
  stripeBlinkAlpha: AlphaRange
  outlineAlpha: AlphaRange
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
  const hasGlow = level >= IMPORTANCE_DECORATION.GLOW_MIN_LEVEL
  return {
    tier,
    iconAlpha: IMPORTANCE_DECORATION.ICON_ALPHA[tier],
    hasGlow,
    flickerTarget: hasGlow ? 'glow' : 'icon',
    flickerPeriodS: IMPORTANCE_DECORATION.FLICKER_PERIOD_S[tier],
    flickerAlpha: IMPORTANCE_DECORATION.FLICKER_ALPHA[tier],
    stripeBlinkAlpha: IMPORTANCE_DECORATION.STRIPE_BLINK_ALPHA[tier],
    outlineAlpha: IMPORTANCE_DECORATION.PASSIVE_OUTLINE_ALPHA[tier],
    colors: IMPORTANCE_COLORS[palette][tier],
  }
}

export function stripeTileWidth(): string {
  const { WIDTH, HEIGHT } = IMPORTANCE_DECORATION.STRIPE_TILE
  return `calc(var(--band-h) * ${String(WIDTH)} / ${String(HEIGHT)})`
}

export function clientLength(units: number): string {
  return clientUnitsToRem(units, CLIENT_TEXT_SCALE.FONT_UNITS, CLIENT_TEXT_SCALE.FONT_REM)
}

export interface ImportanceGeometry {
  iconSize: string
  iconTop: string
  iconRight: string
  bandHeight: string
  bandOffset: string
}

function boxLength(percent: number): string {
  return `${String(percent)}cqw`
}

export function importanceGeometry(variant: ImportanceVariant): ImportanceGeometry {
  return {
    iconSize: clientLength(IMPORTANCE_ICON_UNITS.SIZE),
    iconTop: clientLength(IMPORTANCE_ICON_UNITS.TOP[variant]),
    iconRight: clientLength(IMPORTANCE_ICON_UNITS.RIGHT[variant]),
    bandHeight: boxLength(IMPORTANCE_BOX_LAYOUT.BAND[variant]),
    bandOffset: boxLength(0),
  }
}

export function importanceBoxPadding(level: number): CSSProperties | undefined {
  if (!hasImportance(level)) return undefined
  return {
    paddingBlock: `${String(IMPORTANCE_BOX_LAYOUT.BAND.passive)}%`,
    paddingInline: `${String(PASSIVE_PANE_BLEED_REM)}rem`,
    marginInline: `${String(-PASSIVE_PANE_BLEED_REM)}rem`,
  }
}

export function importanceCssVariables(
  style: ImportanceStyle,
  geometry: ImportanceGeometry,
): CSSProperties {
  const [flickerMin, flickerMax] = style.flickerAlpha
  const [blinkMin, blinkMax] = style.stripeBlinkAlpha
  return {
    '--importance-icon-alpha': style.iconAlpha,
    '--importance-flicker-period': `${String(style.flickerPeriodS)}s`,
    '--importance-flicker-min': flickerMin,
    '--importance-flicker-max': flickerMax,
    '--importance-blink-period': `${String(IMPORTANCE_STRIPE_BLINK_PERIOD_S)}s`,
    '--importance-blink-min': blinkMin,
    '--importance-blink-max': blinkMax,
    '--importance-stripe-color': style.colors.stripe,
    '--importance-stripe-scroll': `${String(IMPORTANCE_STRIPE_SCROLL_S)}s`,
    '--importance-icon-size': geometry.iconSize,
    '--importance-icon-top': geometry.iconTop,
    '--importance-icon-right': geometry.iconRight,
    '--importance-band-height': geometry.bandHeight,
    '--importance-band-offset': geometry.bandOffset,
  } as CSSProperties
}

export function importanceBannerStyle(
  level: number,
  palette: ImportancePalette,
): CSSProperties | undefined {
  const style = importanceStyle(level, palette)
  if (style === undefined) return undefined
  const [outlineMin, outlineMax] = style.outlineAlpha
  return {
    '--name-body': style.colors.passivePanel,
    '--name-tail': `color-mix(in srgb, ${style.colors.passivePanel} 55%, black)`,
    '--name-text': style.colors.passiveName,
    '--importance-panel-glow': style.colors.passiveGlow,
    '--importance-glow-radius': clientLength(IMPORTANCE_PLATE_GLOW_UNITS),
    '--importance-flicker-period': `${String(style.flickerPeriodS)}s`,
    '--importance-glow-min': `color-mix(in srgb, ${style.colors.passiveGlow} ${String(outlineMin * 100)}%, transparent)`,
    '--importance-glow-max': `color-mix(in srgb, ${style.colors.passiveGlow} ${String(outlineMax * 100)}%, transparent)`,
  } as CSSProperties
}
