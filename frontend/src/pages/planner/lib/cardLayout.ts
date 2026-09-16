import {
  CARD_MOBILE_SCALE,
  CARD_MOBILE_SCALE_DENSE,
  CARD_MOBILE_SCALE_NONE,
  MD_ACCENT_COLORS,
} from '@/lib/constants'
import { aspectOf, type CardGeometry } from '@/shared/cardLayout'
import { IDENTITY_GEOMETRY } from '@/pages/identity'
import { EGO_GIFT_GEOMETRY } from '@/pages/egoGift'

/** `PlannerCard`. */
export const PLANNER_GEOMETRY: CardGeometry = {
  size: { widthPx: 280, heightPx: 160 },
  mobileScale: CARD_MOBILE_SCALE_NONE,
  rows: 'auto',
}

/** Start-gift keyword icon, which is square. */
export const KEYWORD_ICON_GEOMETRY: CardGeometry = {
  size: { widthPx: 64, heightPx: 64 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'card',
}

/** `CompactIdentityRow` cell: square portrait over a skill row. */
export const COMPACT_IDENTITY_GEOMETRY: CardGeometry = {
  size: { widthPx: 96, heightPx: 128 },
  mobileScale: CARD_MOBILE_SCALE_NONE,
  rows: 'card',
}

/** `SinnerSkillCard`: padding + portrait + skill row. */
export const SINNER_SKILL_GEOMETRY: CardGeometry = {
  size: { widthPx: 112, heightPx: 144 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'card',
}

/** `SkillImageSimple`, which is square. */
export const SKILL_IMAGE_GEOMETRY: CardGeometry = {
  size: { widthPx: 128, heightPx: 128 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'card',
}

/** `SkillExchangePane`: two skill images either side of an arrow, inside its border. */
export const SKILL_EXCHANGE_GEOMETRY: CardGeometry = {
  size: { widthPx: 356, heightPx: 148 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'card',
}

/** `StartBuffCard`'s pane. */
export const START_BUFF_GEOMETRY: CardGeometry = {
  size: { widthPx: 272, heightPx: 320 },
  mobileScale: CARD_MOBILE_SCALE_DENSE,
  rows: 'card',
}

/** A pixel length on a card `base` wide, as a share of that card's root. */
function pxToCardPercent(px: number, base: number): number {
  return (px / base) * 100
}

/** A share of a card root, as a CSS percentage. */
export function pct(value: number): string {
  return `${String(value)}%`
}

/** A share of a card root, as a CSS length in container-query width units. */
export function cqw(value: number): string {
  return `${String(value)}cqw`
}

const keywordIcon = (px: number) => pxToCardPercent(px, KEYWORD_ICON_GEOMETRY.size.widthPx)
const sinnerSkill = (px: number) => pxToCardPercent(px, SINNER_SKILL_GEOMETRY.size.widthPx)
const skillImage = (px: number) => pxToCardPercent(px, SKILL_IMAGE_GEOMETRY.size.widthPx)
const skillExchange = (px: number) => pxToCardPercent(px, SKILL_EXCHANGE_GEOMETRY.size.widthPx)
const deck = (px: number) => pxToCardPercent(px, IDENTITY_GEOMETRY.size.widthPx)
const compactIdentity = (px: number) => pxToCardPercent(px, COMPACT_IDENTITY_GEOMETRY.size.widthPx)
const startBuff = (px: number) => pxToCardPercent(px, START_BUFF_GEOMETRY.size.widthPx)
const startBuffTall = (px: number) => pxToCardPercent(px, START_BUFF_GEOMETRY.size.heightPx)

/** The start-gift keyword icon, a square box holding a smaller sprite. */
export const KEYWORD_ICON_CARD = {
  icon: keywordIcon(48),
} as const

/** `SinnerSkillCard`: a portrait over a row of three skill boxes. */
export const SINNER_SKILL_CARD = {
  padding: sinnerSkill(8),
  rowGap: sinnerSkill(4),
  portrait: sinnerSkill(96),
  skillGap: sinnerSkill(4),
  skillBox: sinnerSkill(28),
  atkIcon: sinnerSkill(20),
  badge: sinnerSkill(16),
  badgeOffset: sinnerSkill(4),
  badgeFontSize: sinnerSkill(10),
} as const

/** `SkillImageSimple`: the framed skill art and its attack-type composite. */
export const SKILL_IMAGE_CARD = {
  art: skillImage(64),
  atkComposite: skillImage(32),
  /** The attack-type icon, as a share of the composite it sits in */
  atkIcon: pxToCardPercent(16, 32),
  /** The EA badges `SkillEADisplay` hangs off the corners */
  badge: skillImage(28),
  badgeOffset: skillImage(4),
  badgeFontSize: skillImage(14),
} as const

/** `SkillExchangePane`: two skill images either side of an arrow. */
export const SKILL_EXCHANGE_CARD = {
  padding: skillExchange(8),
  gap: skillExchange(8),
  border: skillExchange(2),
  skill: skillExchange(128),
  arrowWidth: skillExchange(64),
  arrowHeight: skillExchange(32),
} as const

/** `SinnerDeckCard`: an identity card over a skill row and an EGO row. */
export const DECK_CARD = {
  padding: deck(8),
  rowGap: deck(4),
  skillGap: deck(4),
  skillBox: deck(28),
  atkIcon: deck(20),
  egoGap: deck(2),
  egoBox: deck(28),
  egoFallbackIcon: deck(16),
} as const

/** `SinnerDeckCard`: an identity card over a skill row and an EGO row. */
export const SINNER_DECK_GEOMETRY: CardGeometry = {
  size: { widthPx: 160, heightPx: 304 },
  mobileScale: CARD_MOBILE_SCALE,
  rows: 'card',
}

/** `CompactIdentityRow`: a square portrait over a skill row. */
export const COMPACT_IDENTITY_CARD = {
  rowGap: compactIdentity(4),
  uptieIcon: compactIdentity(16),
  uptieInset: compactIdentity(2),
  levelFontSize: compactIdentity(16),
  levelBottom: compactIdentity(2),
  levelRight: compactIdentity(4),
  orderFontSize: compactIdentity(32),
  skillGap: compactIdentity(4),
  skillBox: compactIdentity(28),
  atkIcon: compactIdentity(20),
} as const

/** The gap between `CompactIdentityRow`'s cells, in pixels of the desktop grid. */
export const COMPACT_IDENTITY_GRID_GAP = 8

/** The columns `SkillReplacementSection` lays its twelve sinners out in. */
export const SKILL_REPLACEMENT_COLUMNS = { wide: 6, narrow: 3 } as const

/** The gap between `SkillReplacementSection`'s cells, in pixels of the desktop grid. */
export const SKILL_REPLACEMENT_GRID_GAP = 2

/** The gap between `SinnerGrid`'s cells, in pixels of the desktop grid. */
export const SINNER_GRID_GAP = 8

/** The columns `SinnerGrid` lays its twelve sinners out in, by breakpoint. */
export const SINNER_GRID_COLUMNS = { lg: 6, md: 4, sm: 3, base: 2 } as const

/** Border-image geometry of one enhancement button state, in shares of the card root. */
export interface EnhancementBorder {
  /** Enhancement level whose background frame is drawn */
  bgLevel: 0 | 1 | 2
  width: number
  /** `border-image-slice`, in pixels of the source sprite */
  slice: number
  outset: number
}

/** `StartBuffCard`'s parts that differ between Mirror Dungeon card artworks. */
export interface StartBuffCardVariant {
  pane: { width: number; fit: 'cover' | 'fill' }
  cost: {
    left: number
    top: number
    translateX: number
    fontSize: number
    lift: number
    shadow?: string
  }
  nameRowHeight: number
  buffIcon: { size: number; marginLeft: number }
  name: { marginLeft: number; translateYSelf: number; maxSize: number; shadow?: string }
  description: {
    paddingX: number
    paddingY: number
    margin: number
    marginTop: number
    marginRight: number
    color?: string | undefined
  }
  enhancementRow: { gap: number; paddingX: number; paddingBottom: number }
  enhancementSlot: { height: number; marginX: number; translateX: number }
  enhancementStates: {
    unselected: EnhancementBorder
    plus1: EnhancementBorder
    plus2: EnhancementBorder
  }
  /** Extra frame drawn over a selected enhancement button */
  enhancementOverlay?: {
    width: number
    slice: number
    outset: number
    translateX: number
    translateY: number
  }
  highlight: { width: number; height: number; translateX: number; translateY: number }
}

/** The columns `StartBuffEditPane` lays its buff cards out in. */
export const START_BUFF_GRID_COLUMNS = 5

/** Mirror Dungeon versions with their own card artwork and layout */
export type StartBuffCardVersion = 6 | 7

/** `StartBuffCard`'s parts that every artwork shares. */
export const START_BUFF_CARD = {
  aspect: aspectOf(START_BUFF_GEOMETRY.size),
  contentPaddingTop: startBuff(4),
  costRowHeight: 15,
  costGap: startBuff(4),
  starIcon: startBuff(24),
  enhancementIconGap: startBuff(2),
  enhancementIcon: {
    unselected: startBuff(16),
    plus1: startBuff(16.9),
    plus2: startBuff(20.8),
  },
  effectFontSize: startBuff(16.9),
  effectLineHeight: startBuff(20),
  effectGap: startBuff(2),
  /** The band the name is fitted in: the box it may fill, and its ceiling per artwork */
  nameWidth: startBuff(160),
} as const

export const START_BUFF_CARD_VARIANTS: Record<StartBuffCardVersion, StartBuffCardVariant> = {
  6: {
    pane: { width: 100, fit: 'cover' },
    cost: {
      left: 65.625,
      top: 62.5,
      translateX: 0,
      fontSize: startBuff(25),
      lift: startBuff(4),
    },
    nameRowHeight: 12,
    buffIcon: { size: startBuff(56), marginLeft: startBuff(32) },
    name: { marginLeft: startBuff(4), translateYSelf: 0, maxSize: startBuff(20) },
    description: {
      paddingX: startBuff(12),
      paddingY: startBuff(8),
      margin: startBuff(14),
      marginTop: startBuff(14),
      marginRight: startBuff(14),
    },
    enhancementRow: { gap: startBuff(8), paddingX: startBuff(28), paddingBottom: startBuff(32) },
    enhancementSlot: { height: startBuff(24), marginX: 0, translateX: 0 },
    enhancementStates: {
      unselected: { bgLevel: 0, width: startBuff(10), slice: 20, outset: 0 },
      plus1: { bgLevel: 1, width: startBuff(13), slice: 28, outset: startBuff(2) },
      plus2: { bgLevel: 2, width: startBuff(13), slice: 32, outset: startBuff(2) },
    },
    highlight: {
      width: startBuff(264),
      height: startBuffTall(310),
      translateX: startBuff(1),
      translateY: startBuff(7),
    },
  },
  7: {
    pane: { width: startBuff(264), fit: 'fill' },
    cost: {
      left: 75,
      top: 28.125,
      translateX: startBuff(-12),
      fontSize: startBuff(30),
      lift: startBuff(3),
      shadow: '1px 1px 1px black',
    },
    nameRowHeight: 5,
    buffIcon: { size: startBuff(64), marginLeft: startBuff(24) },
    name: {
      marginLeft: 0,
      translateYSelf: 12.5,
      maxSize: startBuff(22),
      shadow: '2px 2px 1px black',
    },
    description: {
      paddingX: startBuff(8),
      paddingY: startBuff(8),
      margin: startBuff(12),
      marginTop: startBuff(40),
      marginRight: startBuff(20),
      color: MD_ACCENT_COLORS[7],
    },
    enhancementRow: { gap: 0, paddingX: startBuff(21), paddingBottom: startBuff(20) },
    enhancementSlot: { height: startBuff(28), marginX: startBuff(5), translateX: startBuff(-4) },
    enhancementStates: {
      unselected: { bgLevel: 0, width: startBuff(6), slice: 8, outset: 0 },
      plus1: { bgLevel: 0, width: startBuff(6), slice: 8, outset: 0 },
      plus2: { bgLevel: 0, width: startBuff(6), slice: 8, outset: 0 },
    },
    enhancementOverlay: {
      width: startBuff(8),
      slice: 16,
      outset: startBuff(2.5),
      translateX: startBuff(-1.5),
      translateY: startBuff(-1),
    },
    highlight: {
      width: startBuff(270),
      height: startBuffTall(324),
      translateX: startBuff(-2),
      translateY: startBuff(-1),
    },
  },
}

const FALLBACK_MD_VERSION: StartBuffCardVersion = 6

function hasCardVariant(mdVersion: number): mdVersion is StartBuffCardVersion {
  return mdVersion in START_BUFF_CARD_VARIANTS
}

/** Narrows an arbitrary MD version to one the card can actually draw. */
export function resolveStartBuffCardVersion(mdVersion: number): StartBuffCardVersion {
  return hasCardVariant(mdVersion) ? mdVersion : FALLBACK_MD_VERSION
}

/** `StartBuffMiniCard`: an icon over a two-line name, on the 96px summary card. */
export const START_BUFF_MINI_CARD = {
  icon: pxToCardPercent(48, EGO_GIFT_GEOMETRY.size.widthPx),
  rowGap: pxToCardPercent(8, EGO_GIFT_GEOMETRY.size.widthPx),
  iconPaddingTop: pxToCardPercent(4, EGO_GIFT_GEOMETRY.size.widthPx),
  namePaddingX: pxToCardPercent(4, EGO_GIFT_GEOMETRY.size.widthPx),
  nameWidth: pxToCardPercent(80, EGO_GIFT_GEOMETRY.size.widthPx),
  nameSize: pxToCardPercent(12, EGO_GIFT_GEOMETRY.size.widthPx),
  nameMaxLines: 2,
  enhancementScale: 0.5,
  enhancementTranslateX: pxToCardPercent(16, EGO_GIFT_GEOMETRY.size.widthPx),
  enhancementTranslateY: pxToCardPercent(4, EGO_GIFT_GEOMETRY.size.widthPx),
} as const
