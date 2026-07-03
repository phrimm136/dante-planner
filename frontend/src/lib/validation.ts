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
