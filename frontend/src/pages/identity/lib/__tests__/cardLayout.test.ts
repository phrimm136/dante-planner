import { describe, it, expect } from 'vitest'

import cnTable from '@static/data/fontAdvances/CN.json'
import enTable from '@static/data/fontAdvances/EN.json'
import jpTable from '@static/data/fontAdvances/JP.json'
import krTable from '@static/data/fontAdvances/KR.json'
import {
  FontAdvanceTableSchema,
  IDENTITY_GEOMETRY,
  createAdvanceMeasure,
  layerStyle,
  levelShadow,
  type FontAdvanceTable,
  type FontTableLanguage,
} from '@/shared/cardLayout'
import {
  FORMATION_ORDER_FONT_SIZE,
  FORMATION_SLOT_DIM,
  IDENTITY_CARD_LAYERS,
  IDENTITY_CARD_ROOT_SCALE,
  IDENTITY_CARD_ROOT_UNITS,
  IDENTITY_CARD_TEXT,
  IDENTITY_LEVEL_FACE_MEASURED,
  IDENTITY_LEVEL_LIFT,
  IDENTITY_LEVEL_LINE_HEIGHT,
  IDENTITY_NAME_CONTENT_EM_MEASURED,
  IDENTITY_NAME_LINE_CORRECTION_EM,
  IDENTITY_NAME_BLOCK,
  IDENTITY_NAME_FONT_UNITS,
  IDENTITY_NAME_TRACKING,
  IDENTITY_NAME_WRAP_WIDTH_UNITS,
  IDENTITY_PORTRAIT,
  IDENTITY_PORTRAIT_WINDOW,
  IDENTITY_SLOT_RECT_UNITS,
  cardRootStyle,
  formationOrderStyle,
  formationSlotLayers,
  identityNameLines,
  levelLineHeight,
  levelStyle,
  nameBlockStyle,
  nameFirstLineOffset,
  nameLineStyle,
  nameLinePitch,
  nameTextStyle,
  portraitStyle,
  portraitWindowStyle,
  relativeRect,
} from '../cardLayout'
import { aspectOf } from '@/shared/cardLayout'

/** The display-face advance tables the site ships, keyed as the layout tables are. */
const ADVANCE_TABLES = { KR: krTable, EN: enTable, JP: jpTable, CN: cnTable }

const LANGUAGES = ['KR', 'EN', 'JP', 'CN'] as const

/** One language's shipped advance table, validated the way the app validates it. */
function faceTable(language: FontTableLanguage): FontAdvanceTable {
  return FontAdvanceTableSchema.parse(ADVANCE_TABLES[language])
}

describe('relativeRect', () => {
  it('re-expresses a child rect as a percentage of its parent', () => {
    const parent = { left: 10, top: 20, width: 50, height: 40 }
    const child = { left: 20, top: 40, width: 25, height: 20 }

    expect(relativeRect(child, parent)).toEqual({
      left: 20,
      top: 50,
      width: 50,
      height: 50,
    })
  })

  it('keeps a child that overhangs its parent negative and oversized', () => {
    const rect = relativeRect(IDENTITY_PORTRAIT, IDENTITY_PORTRAIT_WINDOW)

    expect(rect.left).toBeLessThan(0)
    expect(rect.top).toBeLessThan(0)
    expect(rect.width).toBeGreaterThan(100)
    expect(rect.height).toBeGreaterThan(100)
  })

  it('is the identity when the child is its own parent', () => {
    const frame = IDENTITY_CARD_LAYERS.frame.rect

    expect(relativeRect(frame, frame)).toEqual({
      left: 0,
      top: 0,
      width: 100,
      height: 100,
    })
  })
})

describe('cardRootStyle', () => {
  it('fills its parent width at the game card aspect and hosts the cqw container', () => {
    expect(cardRootStyle()).toEqual({
      position: 'relative',
      containerType: 'inline-size',
      aspectRatio: String(aspectOf(IDENTITY_GEOMETRY.size)),
      width: '100%',
      transform: `scale(${String(IDENTITY_CARD_ROOT_SCALE)})`,
    })
  })

  it('carries no transform transition, so hover never animates the card', () => {
    expect(cardRootStyle()).not.toHaveProperty('transition')
  })
})

describe('portraitWindowStyle', () => {
  it('masks and clips the window at the mask rect', () => {
    const style = portraitWindowStyle('/mask.png')

    expect(style).toMatchObject({
      position: 'absolute',
      left: '1.935%',
      top: '5.778%',
      width: '90.968%',
      height: '91.556%',
      maskImage: 'url(/mask.png)',
      WebkitMaskImage: 'url(/mask.png)',
      maskSize: '100% 100%',
      maskRepeat: 'no-repeat',
      overflow: 'hidden',
    })
  })
})

describe('portraitStyle', () => {
  it('positions the portrait against the window, not the root', () => {
    const style = portraitStyle()

    const pct = (value: string | number | undefined) => Number(String(value).replace('%', ''))

    expect(style.objectFit).toBe('contain')
    expect(style.position).toBe('absolute')
    expect(pct(style.left)).toBeCloseTo(-9.99912, 5)
    expect(pct(style.top)).toBeCloseTo(-1.213465, 5)
    expect(pct(style.width)).toBeCloseTo(119.99934, 5)
    expect(pct(style.height)).toBeCloseTo(102.42693, 5)
  })
})

describe('levelStyle', () => {
  it('sizes the level line in cqw, right-aligned, and truncates like the game', () => {
    expect(levelStyle()).toEqual({
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
    })
  })

  it('carries the identity level underlay, dropped to the bottom right', () => {
    expect(levelStyle().textShadow).toBe('0.50679cqw 0.50679cqw 0cqw #040001')
  })

  it('clips nothing, so the digits are drawn whole wherever the lift puts them', () => {
    expect(levelStyle()).not.toHaveProperty('overflow')
  })

  it('lands the drawn digits on the box top before the lift', () => {
    const face = IDENTITY_LEVEL_FACE_MEASURED
    const baselineEm = (levelLineHeight() - (face.ascent + face.descent)) / 2 + face.ascent

    expect(baselineEm).toBeCloseTo(face.inkAscent, 10)
  })

  it('tops the text in the box, so the slack falls under the level glyphs', () => {
    expect(levelStyle().justifyContent).toBe('flex-start')
  })

  it('pads nothing under the box, so the name block starts at its bottom edge', () => {
    expect(levelStyle().boxSizing).toBe('content-box')
    expect(levelStyle()).not.toHaveProperty('paddingBottom')
  })

  it('takes no box of its own, so it rides on the name block', () => {
    expect(levelStyle()).not.toHaveProperty('position')
    expect(levelStyle()).not.toHaveProperty('top')
  })
})

describe('nameLinePitch', () => {
  it.each([
    ['KR', 1.051172],
    ['EN', 0.84],
    ['JP', 0.85],
    ['CN', 0.81],
  ] as const)(
    'pitches %s at the shipped face line height plus the language correction',
    (language, em) => {
      expect(nameLinePitch(faceTable(language), language)).toBeCloseTo(em, 10)
    },
  )

  it('gives Chinese the Korean correction, which the game’s table has no entry for', () => {
    expect(IDENTITY_NAME_LINE_CORRECTION_EM.CN).toBe(IDENTITY_NAME_LINE_CORRECTION_EM.KR)
  })
})

describe('nameFirstLineOffset', () => {
  it('drops a name whose face is taller than its line pitch', () => {
    for (const language of LANGUAGES) {
      const table = faceTable(language)

      expect(IDENTITY_NAME_CONTENT_EM_MEASURED[language]).toBeGreaterThan(
        nameLinePitch(table, language),
      )
      expect(nameFirstLineOffset(table, language)).toBeGreaterThan(0)
    }
  })

  it('takes back exactly the half leading the line box adds', () => {
    for (const language of LANGUAGES) {
      const table = faceTable(language)
      const halfLeading =
        ((nameLinePitch(table, language) - IDENTITY_NAME_CONTENT_EM_MEASURED[language]) / 2) *
        IDENTITY_CARD_TEXT.name.fontSize

      expect(nameFirstLineOffset(table, language)).toBeCloseTo(-halfLeading, 10)
    }
  })

  it('carries the offset into the name style, in cqw of the card root', () => {
    const table = faceTable('KR')

    expect(nameTextStyle(table, 'KR').marginTop).toBe(
      `${String(nameFirstLineOffset(table, 'KR'))}cqw`,
    )
  })
})

describe('layerStyle over the identity card layers', () => {
  it.each(['frame', 'grade', 'iconRing', 'face'] as const)(
    'contains the %s sprite at its pivot origin',
    (name) => {
      const layer = IDENTITY_CARD_LAYERS[name]
      const style = layerStyle(layer)

      expect(style.objectFit).toBe('contain')
      expect(style.objectPosition).toBe(layer.origin)
      expect(style.left).toBe(`${String(layer.rect.left)}%`)
      expect(style.width).toBe(`${String(layer.rect.width)}%`)
    },
  )

  it('draws the hover ring on the frame’s own rect, fit and origin', () => {
    expect(IDENTITY_CARD_LAYERS.hoverRing).toEqual(IDENTITY_CARD_LAYERS.frame)
  })
})

describe('nameBlockStyle', () => {
  it('stacks the level over the name and anchors both to the bottom edge', () => {
    expect(nameBlockStyle()).toEqual({
      position: 'absolute',
      left: `${String(IDENTITY_NAME_BLOCK.left)}%`,
      width: `${String(IDENTITY_NAME_BLOCK.width)}%`,
      bottom: `${String(100 - IDENTITY_NAME_BLOCK.bottomEdge)}%`,
      display: 'flex',
      flexDirection: 'column',
      textAlign: 'right',
    })
  })

  it('carries no height of its own', () => {
    expect(nameBlockStyle()).not.toHaveProperty('height')
  })
})

describe('nameTextStyle', () => {
  it('sizes the name in cqw and tracks it in em', () => {
    const table = faceTable('KR')

    expect(nameTextStyle(table, 'KR')).toEqual({
      marginTop: `${String(nameFirstLineOffset(table, 'KR'))}cqw`,
      fontSize: `${String(IDENTITY_CARD_TEXT.name.fontSize)}cqw`,
      lineHeight: nameLinePitch(table, 'KR'),
      letterSpacing: '-0.02em',
      wordSpacing: '-0.05em',
    })
  })

  it('leaves the browser no wrapping to do', () => {
    for (const language of LANGUAGES) {
      const style = nameTextStyle(faceTable(language), language)

      expect(style).not.toHaveProperty('whiteSpace')
      expect(style).not.toHaveProperty('wordBreak')
      expect(style).not.toHaveProperty('lineBreak')
    }
  })
})

describe('nameLineStyle', () => {
  it('draws one line per box, unwrapped', () => {
    expect(nameLineStyle()).toEqual({ display: 'block', whiteSpace: 'nowrap' })
  })
})

describe('the formation slot’s text box', () => {
  it('places the name’s 240u box at 21.4u of the slot’s 305u root', () => {
    expect((IDENTITY_NAME_BLOCK.left / 100) * IDENTITY_SLOT_RECT_UNITS.widthUnits).toBeCloseTo(
      21.4,
      4,
    )
    expect((IDENTITY_NAME_BLOCK.width / 100) * IDENTITY_SLOT_RECT_UNITS.widthUnits).toBeCloseTo(
      240,
      4,
    )
  })

  it('reaches further right than the scroll-list item it replaced', () => {
    expect(IDENTITY_NAME_BLOCK.left + IDENTITY_NAME_BLOCK.width).toBeCloseTo(85.704918, 5)
  })

  it('sizes the name at 35u and the level at 45u of that root', () => {
    expect(
      (IDENTITY_CARD_TEXT.name.fontSize / 100) * IDENTITY_SLOT_RECT_UNITS.widthUnits,
    ).toBeCloseTo(35, 3)
    expect(
      (IDENTITY_CARD_TEXT.level.fontSize / 100) * IDENTITY_SLOT_RECT_UNITS.widthUnits,
    ).toBeCloseTo(45, 3)
  })

  it('holds the level’s 46u box and lifts it by [Rect]Level’s 3.4u', () => {
    expect((IDENTITY_LEVEL_LINE_HEIGHT / 100) * IDENTITY_SLOT_RECT_UNITS.heightUnits).toBeCloseTo(
      46,
      1,
    )
    expect((IDENTITY_LEVEL_LIFT / 100) * IDENTITY_SLOT_RECT_UNITS.heightUnits).toBeCloseTo(3.4, 6)
  })
})

/** The face the Japanese cards are drawn in, as the site ships it. */
const JP_TABLE = FontAdvanceTableSchema.parse(ADVANCE_TABLES.JP)

const jpMeasure = createAdvanceMeasure(JP_TABLE, IDENTITY_NAME_TRACKING)

/** The lines one Japanese name takes, as the card draws them. */
function japaneseLines(name: string): string[] {
  return identityNameLines(name, JP_TABLE)
}

describe('identityNameLines over the twelve cards of the Japanese formation reference', () => {
  it.each([
    ['10110', 'ロボトミーE.G.O::\n厳粛な哀悼', ['ロボトミーE.G.O::', '厳粛な哀悼']],
    ['10214', '人差し指遂行者:\n【紙片】', ['人差し指遂行', '者:', '【紙片】']],
    [
      '10314',
      '人差し指代行者 - \n開花E.G.O::\n代行',
      ['人差し指代行者', '-', '開花E.G.O::', '代行'],
    ],
    ['10414', 'ロボトミーE.G.O::\n残香・寂しさ', ['ロボトミーE.G.O::', '残香・寂しさ']],
    [
      '10514',
      'ロボトミーE.G.O::\nホーネット【変調】',
      ['ロボトミーE.G.O::', 'ホーネット【変', '調】'],
    ],
    ['10608', '南部ディエーチ協会\n4課', ['南部ディエーチ協', '会', '4課']],
    ['10709', 'マルチクラック事務所\nフィクサー', ['マルチクラック事', '務所', 'フィクサー']],
    ['10805', 'ロボトミーE.G.O::\nたぷつき', ['ロボトミーE.G.O::', 'たぷつき']],
    ['10911', 'ラ・マンチャランドの姫', ['ラ・マンチャランド', 'の姫']],
    ['11011', '北部ヂェーヴィチ協会\n3課', ['北部ヂェーヴィチ', '協会', '3課']],
    ['11114', 'LCA\nウアジェト先鋒三隊隊長', ['LCA', 'ウアジェト先鋒三', '隊隊長']],
    ['11210', 'ラ・マンチャランド\n神父', ['ラ・マンチャランド', '神父']],
  ])('breaks %s as the game does', (_id, name, expected) => {
    expect(japaneseLines(name)).toEqual(expected)
  })

  it('never opens a line on an ASCII colon, which may not begin one', () => {
    for (const line of japaneseLines('人差し指遂行者:\n【紙片】').slice(1)) {
      expect(line.startsWith(':')).toBe(false)
    }
  })

  it('never ends a line on a lenticular bracket, which may not end one', () => {
    const lines = japaneseLines('ロボトミーE.G.O::\nホーネット【変調】')

    for (const line of lines.slice(0, -1)) expect(line.endsWith('【')).toBe(false)
  })

  it('breaks at a space rather than carrying the hyphen that follows it up', () => {
    expect(japaneseLines('人差し指代行者 - \n開花E.G.O::\n代行')[1]).toBe('-')
  })
})

describe('the wrapping name’s box', () => {
  it('stands wider than the block’s own 240u, which the lower name segment keeps', () => {
    expect((IDENTITY_NAME_BLOCK.width / 100) * IDENTITY_SLOT_RECT_UNITS.widthUnits).toBeCloseTo(
      240,
      4,
    )
    expect(IDENTITY_NAME_WRAP_WIDTH_UNITS).toBeGreaterThan(240)
  })

  it('holds the widest line the game keeps whole, which the 240u box does not', () => {
    const width = jpMeasure('ロボトミーE.G.O::', IDENTITY_NAME_FONT_UNITS)

    expect(width).toBeGreaterThan(240)
    expect(width).toBeLessThanOrEqual(IDENTITY_NAME_WRAP_WIDTH_UNITS)
  })

  it('stops short of the narrowest line the game breaks', () => {
    const width = jpMeasure('人差し指遂行者:', IDENTITY_NAME_FONT_UNITS)

    expect(width).toBeGreaterThan(IDENTITY_NAME_WRAP_WIDTH_UNITS)
  })

  it('clears both bounds by a tracking step, which the pen reading is worth', () => {
    const trackingStep = -IDENTITY_NAME_TRACKING.letterSpacingEm * IDENTITY_NAME_FONT_UNITS
    const lowerBound = jpMeasure('ロボトミーE.G.O::', IDENTITY_NAME_FONT_UNITS)
    const upperBound = jpMeasure('人差し指遂行者:', IDENTITY_NAME_FONT_UNITS)

    expect(IDENTITY_NAME_WRAP_WIDTH_UNITS - lowerBound).toBeGreaterThan(trackingStep)
    expect(upperBound - IDENTITY_NAME_WRAP_WIDTH_UNITS).toBeGreaterThan(trackingStep)
  })
})

describe('formationSlotLayers', () => {
  /** `HandleLabelRectTransform`'s horizontal offsets per state, as Unity stores them. */
  const BANNER_OFFSETS = {
    deployed: { minX: -9, maxX: -8 },
    backup: { minX: 0, maxX: -14 },
  } as const

  const STATES = ['deployed', 'backup'] as const

  it('carries `_grayColor`s own channel as the multiplier a slot with a status takes', () => {
    expect(FORMATION_SLOT_DIM).toBe(0.5)
  })

  it('names the banner sprite `GetLabelSprite` returns for each state', () => {
    expect(formationSlotLayers('deployed').sprite).toBe('selected')
    expect(formationSlotLayers('backup').sprite).toBe('backup')
  })

  it.each(STATES)('places %s at the offsets HandleLabelRectTransform writes', (state) => {
    const offsets = BANNER_OFFSETS[state]
    const { widthUnits } = IDENTITY_CARD_ROOT_UNITS
    const rect = formationSlotLayers(state).banner.rect

    expect(rect.left).toBeCloseTo((offsets.minX / widthUnits) * 100, 5)
    expect(rect.width).toBeCloseTo(
      ((widthUnits + offsets.maxX - offsets.minX) / widthUnits) * 100,
      5,
    )
  })

  it.each(STATES)(
    'gives %s the same vertical band, 19u below the root and 34u above it',
    (state) => {
      const { heightUnits } = IDENTITY_CARD_ROOT_UNITS
      const rect = formationSlotLayers(state).banner.rect

      expect(rect.top).toBeCloseTo((34 / heightUnits) * 100, 5)
      expect(rect.height).toBeCloseTo(((heightUnits + 19 - 34) / heightUnits) * 100, 5)
    },
  )

  it.each(STATES)('draws %s as a preserveAspect sprite centred in its box', (state) => {
    expect(formationSlotLayers(state).banner.fit).toBe('contain')
    expect(formationSlotLayers(state).banner.origin).toBe('center')
  })

  it.each(STATES)('centres %s`s order box on its own banner, 100u above its centre', (state) => {
    const { widthUnits, heightUnits } = IDENTITY_CARD_ROOT_UNITS
    const { banner, order } = formationSlotLayers(state)

    expect(order.left + order.width / 2).toBeCloseTo(banner.rect.left + banner.rect.width / 2, 5)
    expect(order.top + order.height / 2).toBeCloseTo(
      banner.rect.top + banner.rect.height / 2 - (100 / heightUnits) * 100,
      5,
    )
    expect(order.width).toBeCloseTo((200 / widthUnits) * 100, 5)
    expect(order.height).toBeCloseTo((50 / heightUnits) * 100, 5)
  })

  it('gives each state the ink of its own `_numberTextMaterial`', () => {
    expect(formationSlotLayers('deployed').orderInk).toEqual({
      face: '#FFCB00',
      underlay: '#C43B00',
      bloom: '#FF6C00',
    })
    expect(formationSlotLayers('backup').orderInk).toEqual({
      face: '#22FFE4',
      underlay: '#005B50',
    })
  })

  it('blooms only the state whose premultiplied face clears the 1.1 threshold', () => {
    expect(formationSlotLayers('deployed').orderInk.bloom).toBeDefined()
    expect(formationSlotLayers('backup').orderInk.bloom).toBeUndefined()
  })
})

describe('formationOrderStyle', () => {
  const rect = { left: 15, top: 28, width: 64, height: 11 }
  const deployed = formationSlotLayers('deployed')
  const backup = formationSlotLayers('backup')
  const style = formationOrderStyle(rect, deployed.orderInk)

  /** `_Underlay<field> * _ScaleRatioC * _GradientScale * fontSize / pointSize`, in cqw. */
  const DILATE = Number(
    (
      0.95 *
      ((0.35635966062545776 * 6 * 100) / 64 / IDENTITY_CARD_ROOT_UNITS.widthUnits) *
      100
    ).toFixed(5),
  )

  it('centres the digits in the box, as HorizontalAlignment 2 / VerticalAlignment 512', () => {
    expect(style.display).toBe('flex')
    expect(style.alignItems).toBe('center')
    expect(style.justifyContent).toBe('center')
  })

  it('sets the type size to the node`s own 100u, as a share of the card root', () => {
    expect(style.fontSize).toBe(`${String(FORMATION_ORDER_FONT_SIZE)}cqw`)
    expect(FORMATION_ORDER_FONT_SIZE).toBeCloseTo(
      (100 / IDENTITY_CARD_ROOT_UNITS.widthUnits) * 100,
      5,
    )
  })

  it('paints each state in its own material`s face colour', () => {
    expect(style.color).toBe('#FFCB00')
    expect(formationOrderStyle(rect, backup.orderInk).color).toBe('#22FFE4')
  })

  it('draws the underlay as one blurred layer, its softness consuming the whole dilate', () => {
    expect(String(style.textShadow).split(', ')[0]).toBe(`0cqw 0cqw ${String(DILATE)}cqw #C43B00`)
  })

  it('hangs one wide bloom layer behind the deployed underlay, and none behind the backup one', () => {
    const deployedLayers = String(style.textShadow).split(', ')
    const backupLayers = String(formationOrderStyle(rect, backup.orderInk).textShadow).split(', ')

    expect(deployedLayers).toHaveLength(2)
    expect(deployedLayers[1]).toBe('0 0 9cqw #FF6C00')
    expect(backupLayers).toHaveLength(1)
  })

  it('paints the underlay in each state`s own underlay colour', () => {
    expect(String(style.textShadow).split(', ')[0]?.endsWith('#C43B00')).toBe(true)
    expect(String(formationOrderStyle(rect, backup.orderInk).textShadow).endsWith('#005B50')).toBe(
      true,
    )
  })

  it('keeps the underlay`s visible reach at the dilate, not the dilate plus the blur', () => {
    const [dx, dy, blur] =
      String(style.textShadow)
        .split(', ')[0]
        ?.split(' ')
        .map((part) => Number(part.replace('cqw', ''))) ?? []

    expect(Math.hypot(dx ?? 0, dy ?? 0) + (blur ?? 0)).toBeCloseTo(DILATE, 5)
  })
})
