import { z } from 'zod'

/**
 * User Settings Schemas
 *
 * Zod schemas for runtime validation of user settings data structures.
 * These schemas mirror the TypeScript interfaces in types/UserSettingsTypes.ts.
 */

// Association schema for a single keyword option
export const AssociationSchema = z.object({
  keyword: z.string(),
  displayName: z.string(),
})

// Association list response schema
export const AssociationListResponseSchema = z.object({
  associations: z.array(AssociationSchema),
})

// Update keyword request schema (for validation)
export const UpdateUsernameKeywordRequestSchema = z.object({
  keyword: z.string().min(1, 'Keyword is required'),
})

// Type exports from schemas
export type Association = z.infer<typeof AssociationSchema>
export type AssociationListResponse = z.infer<typeof AssociationListResponseSchema>
export type UpdateUsernameKeywordRequest = z.infer<typeof UpdateUsernameKeywordRequestSchema>
