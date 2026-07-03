import { z } from 'zod'

/**
 * Panic Info Schemas
 *
 * Zod schemas for runtime validation of panic info i18n data.
 * Maps panic type ID to name and description.
 *
 * Data source: static/i18n/{LANG}/panicInfo.json
 */

/**
 * PanicInfoEntry schema - individual panic type entry
 */
export const PanicInfoEntrySchema = z.object({
  name: z.string(),
  lowMoraleDesc: z.string(),
  panicDesc: z.string(),
}).strict()

/**
 * PanicInfo schema - Record of panic entries keyed by ID string
 */
export const PanicInfoSchema = z.record(
  z.string(),
  PanicInfoEntrySchema
)

// Type exports
export type PanicInfoEntry = z.infer<typeof PanicInfoEntrySchema>
export type PanicInfo = z.infer<typeof PanicInfoSchema>
