import type { CSSProperties } from 'react'

import {
  aspectOf,
  createAdvanceMeasure,
  IDENTITY_GEOMETRY,
  layerStyle,
  levelShadow,
  pctStyle,
  underlayShadow,
  wrapText,
  type CardLayer,
  type FontAdvanceTable,
  type FontTableLanguage,
  type PctRect,
  type TrackingSpec,
  type Underlay,
} from '@/shared/cardLayout'

/** A block anchored to its ancestor's bottom edge, sized by its own content. */
interface PctBottomBlock {
  left: number
  width: number
  bottomEdge: number
}

/**
 * The formation slot's card root, in its own units: the root the card's text follows.
 *
 * `PersonalityFormationSlot_<Sinner>` takes its size from the formation grid's 305 x 450
 * cell, five units narrower than the scroll-list item every other rect comes from.
 */
export const IDENTITY_SLOT_RECT_UNITS = { widthUnits: 305, heightUnits: 450 } as const

/** The scroll-list card root every sprite rect is a percentage of, in its own units. */
export const IDENTITY_CARD_ROOT_UNITS = { widthUnits: 310, heightUnits: 450 } as const

/** `[Image]MaskArea`, as a percentage of the 310x450 root. */
export const IDENTITY_PORTRAIT_WINDOW: PctRect = {
  left: 1.935,
  top: 5.778,
  width: 90.968,
  height: 91.556,
}

/** The portrait itself, which overhangs the window it is drawn through. */
export const IDENTITY_PORTRAIT: PctRect = {
  left: -7.161,
  top: 4.667,
  width: 109.161,
  height: 93.778,
}

/**
 * `[Image]Frame`, and with it the hover ring: each ring sprite carries its frame sprite's
 * canvas with the ring's ink on the frame's ink, so the two layers share one box.
 */
const FRAME_LAYER: CardLayer = {
  rect: { left: -3.871, top: 2.222, width: 104.516, height: 98.889 },
  fit: 'contain',
  origin: 'center',
}

/**
 * Every sprite layer of the identity card, as percentages of the 310x450 root.
 *
 * Unity offsets a `preserveAspect` sprite by the node's pivot, so `[Image]Grade` (0, 1)
 * parks at its rect's top left and `[Rect]CharacterIcon` (1, 1) at its top right.
 */
export const IDENTITY_CARD_LAYERS = {
  frame: FRAME_LAYER,
  hoverRing: FRAME_LAYER,
  grade: {
    rect: { left: 6.452, top: 8.889, width: 35.484, height: 11.111 },
    fit: 'contain',
    origin: 'left top',
  },
  iconRing: {
    rect: { left: 69.355, top: 0.444, width: 32.258, height: 22.222 },
    fit: 'contain',
    origin: 'right top',
  },
  face: {
    rect: { left: 72.581, top: 2.667, width: 25.806, height: 17.778 },
    fit: 'contain',
    origin: 'center',
  },
} satisfies Record<string, CardLayer>

/**
 * The level and name grow upward from their shared bottom edge, so the block carries no
 * height. `[Text]GroupName` is a 240u box whose right edge sits at 261.4u of the slot's
 * 305u root.
 */
export const IDENTITY_NAME_BLOCK: PctBottomBlock = {
  left: 7.016393,
  width: 78.688525,
  bottomEdge: 93.335556,
}

/** The level line's height, as a percentage of the 450u root. */
export const IDENTITY_LEVEL_LINE_HEIGHT = 10.222

/** Type sizes in cqw of the card root; the level's tracking is em, as TextMeshPro applies it. */
export const IDENTITY_CARD_TEXT = {
  name: { fontSize: 11.47541 },
  level: { fontSize: 14.754098, letterSpacing: '-0.02em', wordSpacing: '-0.05em' },
} as const

/**
 * The tracking the name is measured and drawn with, in em.
 *
 * `m_characterSpacing` on `[Text]GroupUpperName` is -2 in hundredths of an em; the slot's
 * other name node, `[Text]GroupName`, carries -5.
 */
export const IDENTITY_NAME_TRACKING: TrackingSpec = {
  letterSpacingEm: -0.02,
  wordSpacingEm: -0.05,
}

/** The name's type size in the slot's own units, where one unit renders as one pixel. */
export const IDENTITY_NAME_FONT_UNITS =
  (IDENTITY_CARD_TEXT.name.fontSize / 100) * IDENTITY_SLOT_RECT_UNITS.widthUnits

/**
 * The box a name segment wraps in, in the slot's own units.
 *
 * The block's own 240u is `[Text]GroupName`'s box, and the segment that wraps is drawn by
 * `[Text]GroupUpperName`, whose box the prefab transcription never covered. The twelve
 * Japanese formation cards bracket it: the widest line the game keeps whole measures
 * 240.75u and the narrowest it breaks measures 243.00u, and TextMeshPro's pen reading —
 * tracking after every glyph, or only between them — moves both bounds by one 0.7u
 * tracking step. 242 is the one whole unit inside the bracket either reading allows.
 */
export const IDENTITY_NAME_WRAP_WIDTH_UNITS = 242

/**
 * The lines a name is drawn as: its own newlines kept, each segment wrapped to the box.
 *
 * The advance table decides the breaks, not the browser, whose hinted whole-pixel advances
 * run up to three percent narrow of the face's own.
 */
export function identityNameLines(name: string, table: FontAdvanceTable): string[] {
  const measure = createAdvanceMeasure(table, IDENTITY_NAME_TRACKING)
  const box = {
    width: (IDENTITY_NAME_WRAP_WIDTH_UNITS / IDENTITY_SLOT_RECT_UNITS.widthUnits) * 100,
  }

  return name
    .split('\n')
    .flatMap((segment) => wrapText(segment, IDENTITY_CARD_TEXT.name.fontSize, box, measure).lines)
}

/**
 * The level face as the browser draws it, in em, measured on the rendered card.
 *
 * Chrome takes a face's ascent and descent from the font file's own OS/2 and hhea tables
 * and rounds them to whole pixels, and CSS half leading is computed from those — not from
 * the TextMeshPro asset's, which for `ExcelsiorSans SDF` carries a descent line above the
 * baseline and an ascent 0.028em taller.
 */
export const IDENTITY_LEVEL_FACE_MEASURED = {
  ascent: 0.731951,
  descent: 0.21528,
  /** How far the drawn digits reach above the baseline. */
  inkAscent: 0.688895,
} as const

/**
 * The level line's `line-height`, which lands the drawn digits on the box's top edge.
 *
 * A CSS line box puts its baseline at `(lineHeight - (ascent + descent)) / 2 + ascent`,
 * so this is the line height that makes that distance the ink's own ascent.
 */
export function levelLineHeight(): number {
  const face = IDENTITY_LEVEL_FACE_MEASURED
  return 2 * face.inkAscent - face.ascent + face.descent
}

/**
 * How far the level line rides above the top of its box, as a percentage of the 450u root.
 *
 * The slot hangs the level off `[Rect]Level`, whose `anchoredPosition.y` holds it 3.4u
 * above the name's top edge.
 */
export const IDENTITY_LEVEL_LIFT = (3.4 / IDENTITY_SLOT_RECT_UNITS.heightUnits) * 100

/** Each name face's own content box as the browser draws it, in em, measured on the card. */
export const IDENTITY_NAME_CONTENT_EM_MEASURED = {
  KR: 1.328609,
  EN: 0.996457,
  JP: 0.996457,
  CN: 1.162533,
} as const satisfies Record<FontTableLanguage, number>

/**
 * `TextSizeCheckerByTextLine._fontLineCorrection`, in em.
 *
 * The component overwrites the name node's `m_lineSpacing` with its language's entry, so
 * the node's own spacing never reaches the layout. The game's table has no Chinese entry.
 */
export const IDENTITY_NAME_LINE_CORRECTION_EM = {
  KR: -0.4,
  EN: -0.25,
  JP: -0.65,
  CN: -0.4,
} as const satisfies Record<FontTableLanguage, number>

/** One name line's pitch, in em: the face's own line height plus the language's correction. */
export function nameLinePitch(table: FontAdvanceTable, language: FontTableLanguage): number {
  return table.lineHeight + IDENTITY_NAME_LINE_CORRECTION_EM[language]
}

/** The card root's own scale in the game's scroll view. */
export const IDENTITY_CARD_ROOT_SCALE = 1.02

/** Re-expresses `child` — both rects in root percentages — as a percentage of `parent`. */
export function relativeRect(child: PctRect, parent: PctRect): PctRect {
  return {
    left: ((child.left - parent.left) / parent.width) * 100,
    top: ((child.top - parent.top) / parent.height) * 100,
    width: (child.width / parent.width) * 100,
    height: (child.height / parent.height) * 100,
  }
}

export function cardRootStyle(): CSSProperties {
  return {
    position: 'relative',
    containerType: 'inline-size',
    aspectRatio: String(aspectOf(IDENTITY_GEOMETRY.size)),
    width: '100%',
    transform: `scale(${String(IDENTITY_CARD_ROOT_SCALE)})`,
  }
}

export function portraitWindowStyle(maskUrl: string): CSSProperties {
  const mask = `url(${maskUrl})`

  return {
    ...pctStyle(IDENTITY_PORTRAIT_WINDOW),
    WebkitMaskImage: mask,
    maskImage: mask,
    WebkitMaskSize: '100% 100%',
    maskSize: '100% 100%',
    WebkitMaskRepeat: 'no-repeat',
    maskRepeat: 'no-repeat',
    overflow: 'hidden',
  }
}

export function portraitStyle(): CSSProperties {
  return layerStyle({
    rect: relativeRect(IDENTITY_PORTRAIT, IDENTITY_PORTRAIT_WINDOW),
    fit: 'contain',
    origin: 'center',
  })
}

export function levelStyle(): CSSProperties {
  return {
    boxSizing: 'content-box',
    height: `${String(IDENTITY_LEVEL_LINE_HEIGHT / aspectOf(IDENTITY_GEOMETRY.size))}cqw`,
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'flex-start',
    lineHeight: levelLineHeight(),
    fontSize: `${String(IDENTITY_CARD_TEXT.level.fontSize)}cqw`,
    letterSpacing: IDENTITY_CARD_TEXT.level.letterSpacing,
    wordSpacing: IDENTITY_CARD_TEXT.level.wordSpacing,
    textAlign: 'right',
    transform: `translateY(${String(-IDENTITY_LEVEL_LIFT / aspectOf(IDENTITY_GEOMETRY.size))}cqw)`,
    textShadow: levelShadow('identity'),
  }
}

export function nameBlockStyle(): CSSProperties {
  return {
    position: 'absolute',
    left: `${String(IDENTITY_NAME_BLOCK.left)}%`,
    width: `${String(IDENTITY_NAME_BLOCK.width)}%`,
    bottom: `${String(100 - IDENTITY_NAME_BLOCK.bottomEdge)}%`,
    display: 'flex',
    flexDirection: 'column',
    textAlign: 'right',
  }
}

/**
 * How far a name's first line drops to start on the block's top edge, in cqw of the root.
 *
 * A CSS line box centres its leading, so the first line's ink sits half the difference
 * between the face's content box and the line's pitch off the block edge. TextMeshPro
 * lays the first line's ascender on the edge itself, so the block takes that half back —
 * negative where the face is shorter than the pitch.
 */
export function nameFirstLineOffset(table: FontAdvanceTable, language: FontTableLanguage): number {
  const slack = IDENTITY_NAME_CONTENT_EM_MEASURED[language] - nameLinePitch(table, language)
  return (slack / 2) * IDENTITY_CARD_TEXT.name.fontSize
}

export function nameTextStyle(table: FontAdvanceTable, language: FontTableLanguage): CSSProperties {
  return {
    marginTop: `${String(nameFirstLineOffset(table, language))}cqw`,
    fontSize: `${String(IDENTITY_CARD_TEXT.name.fontSize)}cqw`,
    lineHeight: nameLinePitch(table, language),
    letterSpacing: `${String(IDENTITY_NAME_TRACKING.letterSpacingEm)}em`,
    wordSpacing: `${String(IDENTITY_NAME_TRACKING.wordSpacingEm)}em`,
  }
}

/** One drawn line of a name; `identityNameLines` has already decided where it ends. */
export function nameLineStyle(): CSSProperties {
  return { display: 'block', whiteSpace: 'nowrap' }
}
/**
 * The two `FormationPersonalityUI_LabelTypes` a deck slot's order maps onto.
 *
 * `deployed` is the game's `Participated` and `backup` its `Baton`; the other seven statuses
 * cover restriction and swap states the deck builder has no input for.
 */
export type FormationSlotState = 'deployed' | 'backup'

/** One `_numberTextMaterial`'s ink on screen, after the UI camera's bloom. */
interface FormationInk {
  face: string
  underlay: string
  /** The glow the face drives, or `undefined` where it stays under the bloom threshold */
  bloom?: string
}

/** The banner and order number one formation state draws. */
export interface FormationSlotLayers {
  /** The `deploy-<sprite>` file `PersonalityUILabel.GetLabelSprite` returns for the state */
  sprite: string
  /** `[Image]ParticipateSlotUI`, at the box `HandleLabelRectTransform` gives the state */
  banner: CardLayer
  /** `text_participateOrder`'s box, which follows its banner sideways */
  order: PctRect
  /** The material `SetNumberText` gives `text_participateOrder` in the state */
  orderInk: FormationInk
}

/**
 * The ink of the two `ExcelsiorSans SDF` materials the order number is drawn in.
 *
 * `TMP_SDF.shader` premultiplies each colour by its own alpha, so the value that reaches the
 * buffer is `rgb * a`. The MainUI camera renders HDR and carries one volume component —
 * `PostProcessing Profile_UI`'s Bloom, threshold 1.1, intensity 1.5, scatter 0.5 — and no
 * tonemapper, so anything under the threshold reaches the screen as `clamp01(rgb * a)`.
 *
 * Only `Burning_ver_2`'s face clears that threshold: premultiplied it is (2.248, 0.624, 0),
 * whose red drives the bloom that clips the core to yellow and spreads the surrounding glow.
 * Its underlay (0.767, 0.233, 0) and both of `FormationBaton`'s colours stay under it.
 */
const FORMATION_INKS = {
  deployed: { face: '#FFCB00', underlay: '#C43B00', bloom: '#FF6C00' },
  backup: { face: '#22FFE4', underlay: '#005B50' },
} as const satisfies Record<FormationSlotState, FormationInk>

const FORMATION_BLOOM_RADIUS_CQW_MEASURED = 9

/**
 * `[Image]ParticipateSlotUI`'s box per state, as percentages of the 310x450 root.
 *
 * `HandleLabelRectTransform` overwrites the node's serialized offsets on every refresh, so
 * the scene's own rect never reaches the screen: every state takes `offsetMin.y` -19 and
 * `offsetMax.y` -34, and the state decides the horizontal pair — (-9, -8) for `Participated`
 * and (0, -14) for `Baton`.
 */
const FORMATION_BANNERS = {
  deployed: { left: -2.903226, top: 7.555556, width: 100.322581, height: 96.666667 },
  backup: { left: 0, top: 7.555556, width: 95.483871, height: 96.666667 },
} as const satisfies Record<FormationSlotState, PctRect>

/**
 * `text_participateOrder`'s box, as percentages of the 310x450 root.
 *
 * The node is a 200x50 box centred on its banner, so it follows the banner's own box
 * sideways and sits 100u above its centre in both states.
 */
const FORMATION_ORDER_RECTS = {
  deployed: { left: 15, top: 28.111111, width: 64.516129, height: 11.111111 },
  backup: { left: 15.483871, top: 28.111111, width: 64.516129, height: 11.111111 },
} as const satisfies Record<FormationSlotState, PctRect>

/** The `deploy-<sprite>` banner each state carries. */
const FORMATION_SPRITES: Record<FormationSlotState, string> = {
  deployed: 'selected',
  backup: 'backup',
}

/**
 * `PersonalitySlotGraphics._grayColor`, as one channel of its RGBA (0.5, 0.5, 0.5, 1).
 *
 * `SetGray` writes the colour over every `Graphic` in the slot's `_graphic` array and
 * `SetWhite` writes (1, 1, 1, 1) back, so the slot's whole body multiplies by the value.
 * Both states take it: `PersonalityUILabel.GetIsGray` answers true for `Baton` as it does
 * for `Participated`, and a slot carrying no status keeps (1, 1, 1, 1).
 * `[Image]ParticipateSlotUI`, its `text_participateOrder` child and the status label are
 * not in `_graphic`, so the banner and the order number keep their own ink.
 */
export const FORMATION_SLOT_DIM = 0.5

/** `text_participateOrder`'s `m_fontSize`, as a share of the 310u root. */
export const FORMATION_ORDER_FONT_SIZE = 32.258065

/**
 * The burning underlay the order number draws, in cqw of the 310u root.
 *
 * Both materials carry `_UnderlayOffsetX/Y` 0 and `_UnderlayDilate` = `_UnderlaySoftness`
 * = 0.95 over the same `_ScaleRatioC` and `_GradientScale`, taken through
 * `_Underlay<field> * _ScaleRatioC * _GradientScale * fontSize / pointSize` —
 * 0.95 x 0.35636 x 6 x 100 / 64 — and divided by the card's own width.
 */
const FORMATION_ORDER_UNDERLAY: Underlay = {
  dx: 0,
  dy: 0,
  softness: 1.02382,
  dilate: 1.02382,
}

/** The banner, its sprite and the order number's box and ink for one formation state. */
export function formationSlotLayers(state: FormationSlotState): FormationSlotLayers {
  return {
    sprite: FORMATION_SPRITES[state],
    banner: { rect: FORMATION_BANNERS[state], fit: 'contain', origin: 'center' },
    order: FORMATION_ORDER_RECTS[state],
    orderInk: FORMATION_INKS[state],
  }
}

/** The `text-shadow` an order number draws: the underlay ring, then the bloom behind it. */
export function formationOrderShadow(ink: FormationInk): string {
  const ring = underlayShadow(FORMATION_ORDER_UNDERLAY, ink.underlay)
  if (ink.bloom === undefined) return ring

  return `${ring}, 0 0 ${String(FORMATION_BLOOM_RADIUS_CQW_MEASURED)}cqw ${ink.bloom}`
}

/** `text_participateOrder`, centred in its box as `HorizontalAlignment` 2 / `Vertical` 512. */
export function formationOrderStyle(rect: PctRect, ink: FormationInk): CSSProperties {
  return {
    ...pctStyle(rect),
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    lineHeight: 1,
    fontSize: `${String(FORMATION_ORDER_FONT_SIZE)}cqw`,
    color: ink.face,
    textShadow: formationOrderShadow(ink),
  }
}
