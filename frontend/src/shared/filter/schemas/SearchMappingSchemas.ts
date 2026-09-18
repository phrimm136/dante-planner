import { z } from 'zod'

export const KeywordMatchSchema = z.record(z.string(), z.string())

export const UnitKeywordsSchema = z.record(z.string(), z.string())

export type UnitKeywords = z.infer<typeof UnitKeywordsSchema>
