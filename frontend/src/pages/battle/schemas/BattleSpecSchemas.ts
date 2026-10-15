import { z } from 'zod'
import { BATTLE_CASES } from '../lib/battleVocabulary'

const SpriteBoxSchema = z.tuple([z.number(), z.number(), z.number(), z.number()])

export const SpritePlacementSchema = z
  .object({
    file: z.string(),
    feetY: z.number(),
    ppu: z.number(),
    anchorX: z.number(),
    anchorY: z.number(),
    width: z.number(),
    height: z.number(),
    massBox: SpriteBoxSchema,
    centroid: z.tuple([z.number(), z.number()]),
    drawnBox: SpriteBoxSchema,
  })
  .strict()

export const BattleSpriteSchema = z.union([
  SpritePlacementSchema,
  z.object({ file: z.string() }).strict(),
])

export const BATTLE_SPEC_SHAPE = {
  cases: z.array(z.enum(BATTLE_CASES)),
  chapters: z.array(z.string()),
  themePacks: z.array(z.string()),
  events: z.array(z.string()),
  hidden: z.boolean(),
  railwayLines: z.array(z.number().int()),
  luxcavations: z.array(z.string()),
  spriteId: z.string(),
  sprite: BattleSpriteSchema,
  sortKey: z.partialRecord(z.enum(BATTLE_CASES), z.array(z.number())),
}

export const BattleNameEntrySchema = z
  .object({
    name: z.string(),
    byCase: z.partialRecord(z.enum(BATTLE_CASES), z.string()).optional(),
    aliases: z.array(z.string()),
  })
  .strict()

export const BattleNameListSchema = z.record(z.string(), BattleNameEntrySchema)

export const BattleFacetNamesSchema = z
  .object({
    chapters: z.record(z.string(), z.string()),
    railwayLines: z.record(z.string(), z.string()),
    luxcavations: z.record(z.string(), z.string()),
    events: z.record(z.string(), z.string()),
  })
  .strict()

export type BattleSprite = z.infer<typeof BattleSpriteSchema>
export type SpritePlacement = z.infer<typeof SpritePlacementSchema>
export type BattleSortKeys = z.infer<typeof BATTLE_SPEC_SHAPE.sortKey>
export type BattleNameEntry = z.infer<typeof BattleNameEntrySchema>
export type BattleNameList = z.infer<typeof BattleNameListSchema>
export type BattleFacetNames = z.infer<typeof BattleFacetNamesSchema>
