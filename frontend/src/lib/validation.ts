import type { z } from 'zod'

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
