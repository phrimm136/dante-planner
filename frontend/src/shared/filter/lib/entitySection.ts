/**
 * The shape every database section's entity shares: the spec entry copied key
 * for key, plus a branded id and an optional localized name.
 */

import type { z } from 'zod'
import type { Facet } from './applyFacets'

export type Entity<TId extends string, TSpec extends object> = TSpec & {
  id: TId
  name?: string
}

export interface EntitySection<TId extends string, TSpec extends object, TState> {
  /** The pages/<dir> name. */
  name: string
  /** Basename under static/data. */
  specFile: string
  specListSchema: z.ZodType<Record<string, TSpec>>
  /** The entry object schema; the conformance test reads its `.shape` keys. */
  specEntrySchema: z.ZodObject<z.ZodRawShape>
  idSchema: IdSchema<TId>
  toEntity: (id: string, spec: TSpec, name?: string) => Entity<TId, TSpec>
  facets: readonly Facet<Entity<TId, TSpec>, TState>[]
}

/** Branded id schemas do not satisfy z.ZodType<branded>: zod widens `_output` to string. */
export interface IdSchema<TId extends string> {
  parse: (value: unknown) => TId
}

export type AnyEntitySection = EntitySection<string, object, unknown>

export function createEntityBuilder<TId extends string, TSpec extends object>(
  idSchema: IdSchema<TId>,
) {
  return (id: string, spec: TSpec, name?: string): Entity<TId, TSpec> =>
    ({
      id: idSchema.parse(id),
      ...(name === undefined ? {} : { name }),
      ...spec,
    }) as Entity<TId, TSpec>
}
