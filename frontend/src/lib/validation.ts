import { z } from 'zod'

/**
 * Page metadata of a Spring Data `PagedModel` envelope.
 */
export const PageMetadataSchema = z
  .object({
    /** Requested page size */
    size: z.number().int().nonnegative(),
    /** Current page number (0-indexed) */
    number: z.number().int().nonnegative(),
    /** Total number of items matching the query */
    totalElements: z.number().int().nonnegative(),
    /** Total number of pages */
    totalPages: z.number().int().nonnegative(),
  })
  .strict()

/**
 * Builds the schema of a Spring Data `PagedModel` envelope around an item schema.
 *
 * @param itemSchema - Schema describing one element of `content`
 * @returns Schema matching `{ content: [...], page: { size, number, totalElements, totalPages } }`
 */
export function pagedModelSchema<T extends z.ZodType>(itemSchema: T) {
  return z
    .object({
      /** Items of the current page */
      content: z.array(itemSchema),
      /** Pagination metadata */
      page: PageMetadataSchema,
    })
    .strict()
}

/**
 * Validates unknown data against a Zod schema, throwing on failure.
 *
 * Error message shape is load-bearing: existing call sites and error
 * boundaries match on `[context] Validation failed: …` — do not change it.
 *
 * @param data - Unknown input (e.g. `module.default` from a dynamic import)
 * @param schema - Zod schema describing the expected shape
 * @param context - Label identifying the data source, e.g. `identity specList`
 * @returns Parsed data (note: `z.object` strips undeclared fields)
 */
export function validateData<T>(data: unknown, schema: z.ZodType<T>, context: string): T {
  const result = schema.safeParse(data)
  if (!result.success) {
    throw new Error(`[${context}] Validation failed: ${result.error.message}`)
  }
  return result.data
}

/**
 * Validates unknown data against a Zod schema, degrading to `null` on failure
 * instead of throwing.
 *
 * For boundaries where malformed data must not surface as an error — the
 * authenticated-user query treats an unparseable body as "no user" rather than
 * failing the query — so the failure is logged rather than propagated.
 *
 * @param data - Unknown input (e.g. an API response body)
 * @param schema - Zod schema describing the expected shape
 * @param context - Label identifying the data source, e.g. `auth me`
 * @returns Parsed data, or `null` when validation fails
 */
export function validateDataOrNull<T>(
  data: unknown,
  schema: z.ZodType<T>,
  context: string,
): T | null {
  const result = schema.safeParse(data)
  if (!result.success) {
    console.error(`[${context}] Validation failed:`, result.error)
    return null
  }
  return result.data
}
