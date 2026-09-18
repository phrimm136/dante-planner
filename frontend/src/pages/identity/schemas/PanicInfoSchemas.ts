import { z } from 'zod'

export const PanicInfoEntrySchema = z
  .object({
    name: z.string(),
    lowMoraleDesc: z.string(),
    panicDesc: z.string(),
  })
  .strict()

export const PanicInfoSchema = z.record(z.string(), PanicInfoEntrySchema)

export type PanicInfoEntry = z.infer<typeof PanicInfoEntrySchema>
export type PanicInfo = z.infer<typeof PanicInfoSchema>
