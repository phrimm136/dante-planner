import { z } from 'zod'

const UnitRectSchema = z
  .object({
    x: z.number(),
    y: z.number(),
    w: z.number(),
    h: z.number(),
  })
  .strict()

export type UnitRect = z.infer<typeof UnitRectSchema>

const SizeSchema = z.object({ w: z.number(), h: z.number() }).strict()

const SliceSchema = z
  .object({
    left: z.number(),
    bottom: z.number(),
    right: z.number(),
    top: z.number(),
  })
  .strict()

const imageBase = {
  file: z.string().nullable(),
  filePattern: z.string().optional(),
  preserveAspect: z.boolean(),
  source: z.string().optional(),
}

const ImageSchema = z.union([
  z.object({ ...imageBase, slice: z.null(), pixelsPerUnit: z.number().nullable() }).strict(),
  z.object({ ...imageBase, slice: SliceSchema, pixelsPerUnit: z.number() }).strict(),
])

export type LayoutImage = z.infer<typeof ImageSchema>

const TextSchema = z
  .object({
    faces: z.record(z.string(), z.string()),
    facesSource: z.string().optional(),
    fontSize: z.number(),
    autoSize: z.tuple([z.number(), z.number()]).nullable(),
    characterSpacing: z.number(),
    wordSpacing: z.number(),
    lineSpacing: z.number(),
    hAlign: z.enum(['left', 'center', 'right']),
    vAlign: z.enum(['top', 'geometry']),
    color: z.string(),
    wordWrap: z.boolean(),
  })
  .strict()

export type LayoutText = z.infer<typeof TextSchema>

const ShownWhenSchema = z.enum([
  'always',
  'scrollable',
  'selected',
  'hover',
  'focused',
  'notFocused',
])

export type ShownWhen = z.infer<typeof ShownWhenSchema>

const elementBase = {
  path: z.string(),
  rect: UnitRectSchema,
  shownWhen: ShownWhenSchema,
}

const ImageElementSchema = z
  .object({
    ...elementBase,
    role: z.enum([
      'pane',
      'buffIcon',
      'costIcon',
      'descArrowBottom',
      'descArrowTop',
      'cardSelected',
      'cardHover',
      'base',
      'level',
      'hover',
      'focused',
    ]),
    image: ImageSchema,
  })
  .strict()

const LevelImageElementSchema = z
  .object({
    ...elementBase,
    role: z.enum(['levelButton', 'levelIcon', 'levelHover', 'levelFocused', 'levelFocusedIcon']),
    buttonIndex: z.number().int().nonnegative(),
    image: ImageSchema,
  })
  .strict()

const TextElementSchema = z
  .object({
    ...elementBase,
    role: z.enum(['name', 'cost', 'desc']),
    text: TextSchema,
  })
  .strict()

const ViewportElementSchema = z
  .object({
    ...elementBase,
    role: z.literal('descViewport'),
  })
  .strict()

const LayoutElementSchema = z.discriminatedUnion('role', [
  ImageElementSchema,
  LevelImageElementSchema,
  TextElementSchema,
  ViewportElementSchema,
])

export type LayoutElement = z.infer<typeof LayoutElementSchema>
export type ImageElement = z.infer<typeof ImageElementSchema>
export type LevelImageElement = z.infer<typeof LevelImageElementSchema>
export type TextElement = z.infer<typeof TextElementSchema>

const LevelIconsSchema = z
  .object({
    '1': z.string(),
    '2': z.string(),
    source: z.string(),
  })
  .strict()

const StatesSchema = z.object({ source: z.string() }).strict()

const layoutBase = {
  prefab: z.string(),
  root: SizeSchema,
  canvas: UnitRectSchema,
  elements: z.array(LayoutElementSchema),
  levelIcons: LevelIconsSchema,
  states: StatesSchema,
}

export const StartBuffCardLayoutSchema = z
  .object({
    ...layoutBase,
    description: z
      .object({
        fontSize: z.number(),
        bullet: z.object({ prefix: z.string(), advanceEm: z.number() }).strict(),
        source: z.string(),
      })
      .strict(),
    cost: z
      .object({
        color: z.string(),
        enhancedColor: z.string(),
        enhancedAboveLevel: z.number().int(),
        source: z.string(),
      })
      .strict(),
    levelButtons: z
      .object({
        iconCounts: z.array(z.number().int().nonnegative()),
        source: z.string(),
      })
      .strict(),
  })
  .strict()
  .transform((layout, ctx) => {
    const desc = layout.elements.find((element): element is TextElement => element.role === 'desc')
    if (desc === undefined) {
      ctx.addIssue({ code: 'custom', message: `${layout.prefab} has no desc text` })
      return z.NEVER
    }
    const viewportElement = layout.elements.find((element) => element.role === 'descViewport')
    if (viewportElement === undefined) {
      ctx.addIssue({ code: 'custom', message: `${layout.prefab} has no description viewport` })
      return z.NEVER
    }
    return { ...layout, desc, viewport: viewportElement.rect }
  })

export type StartBuffCardLayout = z.infer<typeof StartBuffCardLayoutSchema>

export const StartBuffMiniCardLayoutSchema = z.object(layoutBase).strict()

export type StartBuffMiniCardLayout = z.infer<typeof StartBuffMiniCardLayoutSchema>
