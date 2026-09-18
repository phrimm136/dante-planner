import { z } from 'zod'

export const PageMetadataSchema = z
  .object({
    size: z.number().int().nonnegative(),
    number: z.number().int().nonnegative(),
    totalElements: z.number().int().nonnegative(),
    totalPages: z.number().int().nonnegative(),
  })
  .strict()

export function pagedModelSchema<T extends z.ZodType>(itemSchema: T) {
  return z
    .object({
      content: z.array(itemSchema),
      page: PageMetadataSchema,
    })
    .strict()
}

/**
 * Validates unknown data against a Zod schema, throwing on failure.
 *
 * Error message shape is load-bearing: existing call sites and error
 * boundaries match on `[context] Validation failed: …` — do not change it.
 */
export function validateData<T>(data: unknown, schema: z.ZodType<T>, context: string): T {
  const result = schema.safeParse(data)
  if (!result.success) {
    throw new Error(`[${context}] Validation failed: ${result.error.message}`)
  }
  return result.data
}

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
