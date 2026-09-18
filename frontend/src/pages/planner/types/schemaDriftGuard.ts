/**
 * Planner Schema Drift Guard
 *
 * Compile-time assertions pinning the relationship between the hand-written
 * planner types (PlannerTypes.ts) and their Zod schemas (PlannerSchemas.ts).
 * Compiling under `tsc -b` IS the test — this file must live under src/
 * (not __tests__/) because test files are excluded from every tsconfig.
 *
 * The planner is exempt from the z.infer type-direction rule: its reader
 * schema is deliberately LOOSER than the writer type. SaveablePlannerSchema
 * validates `content` as z.record(string, unknown) so that a strict gate can
 * never mass-discard older saves on load (two-step validation / blast-radius
 * invariant). Therefore:
 *
 * - LEAF shapes must match the schema exactly: Expect<Equal<...>>
 * - COMPOSITES are asserted one-directionally ONLY (type extends schema
 *   input) — equality is false BY DESIGN. Do not "fix" the looseness.
 */

import type { z } from 'zod'
import type {
  PlannerStatusSchema,
  PlannerMetadataSchema,
  MDConfigSchema,
  RRConfigSchema,
  PlannerConfigDiscriminatedSchema,
  FloorSelectionDraftSchema,
  SerializableNoteContentSchema,
  SaveablePlannerSchema,
  PlannerExportItemSchema,
  ExportEnvelopeShapeSchema,
} from '../schemas/PlannerSchemas'
import type {
  PlannerStatus,
  PlannerMetadata,
  MDConfig,
  RRConfig,
  PlannerEditorConfig,
  SerializableFloorSelection,
  SerializableNoteContent,
  MDPlannerContent,
  RRPlannerContent,
  MDSaveablePlanner,
  RRSaveablePlanner,
  SaveablePlanner,
  PlannerExportItem,
  ExportEnvelope,
} from './PlannerTypes'
import type { isMDPlanner } from './PlannerTypes'

type Equal<X, Y> =
  (<T>() => T extends X ? 1 : 2) extends <T>() => T extends Y ? 1 : 2 ? true : false

type Extends<A, B> = A extends B ? true : false

type Expect<T extends true> = T

/**
 * Interfaces have no implicit index signature, so they are never assignable
 * to Record<string, unknown> even when structurally compatible. A mapped
 * copy restores structural record compatibility without changing the shape.
 */
type AsRecord<T> = { [K in keyof T]: T[K] }

type MDContentAsRecord = AsRecord<MDPlannerContent>

type ExportItemAsRecord = Omit<PlannerExportItem, 'content'> & {
  content: MDContentAsRecord
}

/**
 * Reaching an MD-only content field without selecting a branch must not compile.
 * The `@ts-expect-error` IS the assertion: if `content` ever widens back to a
 * castable sibling of `config`, this line stops erroring and the build fails.
 */
// @ts-expect-error content is MDPlannerContent | RRPlannerContent until a branch is chosen
export type UnguardedMDContentField = SaveablePlanner['content']['selectedKeywords']

export type PlannerRootDiscriminationGuard = [
  Expect<Equal<Extract<SaveablePlanner, { config: MDConfig }>, MDSaveablePlanner>>,
  Expect<Equal<MDSaveablePlanner['content'], MDPlannerContent>>,
  Expect<Equal<Extract<SaveablePlanner, { config: RRConfig }>, RRSaveablePlanner>>,
  Expect<Equal<RRSaveablePlanner['content'], RRPlannerContent>>,

  Expect<Equal<typeof isMDPlanner, (planner: SaveablePlanner) => planner is MDSaveablePlanner>>,
]

export type PlannerSchemaDriftGuard = [
  Expect<Equal<z.infer<typeof PlannerStatusSchema>, PlannerStatus>>,
  Expect<Equal<z.infer<typeof FloorSelectionDraftSchema>, SerializableFloorSelection>>,
  Expect<Equal<z.infer<typeof PlannerMetadataSchema>, PlannerMetadata>>,
  Expect<Equal<z.infer<typeof MDConfigSchema>, MDConfig>>,
  Expect<Equal<z.infer<typeof RRConfigSchema>, RRConfig>>,
  Expect<Equal<z.infer<typeof PlannerConfigDiscriminatedSchema>, PlannerEditorConfig>>,

  // JSONContentSchema is z.ZodType<unknown> (structural Tiptap validation),
  // so the schema side of note content is wider than the type.
  Expect<Extends<SerializableNoteContent, z.input<typeof SerializableNoteContentSchema>>>,

  Expect<
    Extends<
      { metadata: PlannerMetadata; config: PlannerEditorConfig; content: MDContentAsRecord },
      z.input<typeof SaveablePlannerSchema>
    >
  >,

  Expect<Extends<MDContentAsRecord, z.input<typeof SaveablePlannerSchema>['content']>>,

  Expect<Extends<ExportItemAsRecord, z.input<typeof PlannerExportItemSchema>>>,
  Expect<
    Extends<
      Omit<ExportEnvelope, 'planners'> & { planners: ExportItemAsRecord[] },
      z.input<typeof ExportEnvelopeShapeSchema>
    >
  >,
]
