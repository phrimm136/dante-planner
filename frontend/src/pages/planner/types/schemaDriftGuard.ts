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
  ExportEnvelopeSchema,
} from '../schemas/PlannerSchemas'
import type {
  PlannerStatus,
  PlannerMetadata,
  MDConfig,
  RRConfig,
  PlannerConfig,
  SerializableFloorSelection,
  SerializableNoteContent,
  MDPlannerContent,
  PlannerExportItem,
  ExportEnvelope,
} from './PlannerTypes'

// ============================================================================
// Type-level assertion helpers
// ============================================================================

type Equal<X, Y> =
  (<T>() => T extends X ? 1 : 2) extends (<T>() => T extends Y ? 1 : 2)
    ? true
    : false

type Extends<A, B> = A extends B ? true : false

type Expect<T extends true> = T

/**
 * Interfaces have no implicit index signature, so they are never assignable
 * to Record<string, unknown> even when structurally compatible. A mapped
 * copy restores structural record compatibility without changing the shape.
 */
type AsRecord<T> = { [K in keyof T]: T[K] }

/** MDPlannerContent as a structural record (see AsRecord) */
type MDContentAsRecord = AsRecord<MDPlannerContent>

/** PlannerExportItem with its content normalized to a structural record */
type ExportItemAsRecord = Omit<PlannerExportItem, 'content'> & {
  content: MDContentAsRecord
}

// ============================================================================
// Assertions (exported so noUnusedLocals does not flag them)
// ============================================================================

export type PlannerSchemaDriftGuard = [
  // --- Leaves: schema and hand-written type must agree exactly ---
  Expect<Equal<z.infer<typeof PlannerStatusSchema>, PlannerStatus>>,
  Expect<Equal<z.infer<typeof FloorSelectionDraftSchema>, SerializableFloorSelection>>,
  Expect<Equal<z.infer<typeof PlannerMetadataSchema>, PlannerMetadata>>,
  Expect<Equal<z.infer<typeof MDConfigSchema>, MDConfig>>,
  Expect<Equal<z.infer<typeof RRConfigSchema>, RRConfig>>,
  Expect<Equal<z.infer<typeof PlannerConfigDiscriminatedSchema>, PlannerConfig>>,

  // --- Composites: one-directional BY DESIGN ---
  // JSONContentSchema is z.ZodType<unknown> (structural Tiptap validation),
  // so the schema side of note content is wider than the type.
  Expect<Extends<SerializableNoteContent, z.input<typeof SerializableNoteContentSchema>>>,

  // SaveablePlanner: metadata/config are pinned exactly by the leaf Equals
  // above; content must be ACCEPTED by the loose z.record(string, unknown)
  // gate — never equal to it.
  Expect<Extends<
    { metadata: PlannerMetadata; config: PlannerConfig; content: MDContentAsRecord },
    z.input<typeof SaveablePlannerSchema>
  >>,

  // PlannerContent (MD half) is accepted by SaveablePlannerSchema's content gate.
  Expect<Extends<MDContentAsRecord, z.input<typeof SaveablePlannerSchema>['content']>>,

  // Export format: every exported planner satisfies the import gate.
  Expect<Extends<ExportItemAsRecord, z.input<typeof PlannerExportItemSchema>>>,
  Expect<Extends<
    Omit<ExportEnvelope, 'planners'> & { planners: ExportItemAsRecord[] },
    z.input<typeof ExportEnvelopeSchema>
  >>,
]
