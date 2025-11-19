import { type ZodSchema, type ZodError } from 'zod'
import {
  IdentityDataSchema,
  IdentityI18nSchema,
  IdentitySpecListSchema,
  IdentityNameListSchema,
  EGODataSchema,
  EGOI18nSchema,
  EGOSpecListSchema,
  EGONameListSchema,
  EGOGiftDataSchema,
  EGOGiftI18nSchema,
  EGOGiftSpecListSchema,
  EGOGiftNameListSchema,
} from '@/schemas'
import type { EntityType } from '@/hooks/useEntityDetailData'

/**
 * Validation Utilities
 *
 * Helper functions for schema mapping and error formatting in data loading hooks.
 * Provides runtime validation with environment-aware error verbosity.
 */

// Data kind discriminator for schema selection
export type DataKind = 'detail' | 'i18n' | 'specList' | 'nameList'

/**
 * Get the appropriate schema for entity detail data
 */
export function getDetailSchema(type: EntityType): ZodSchema {
  const schemaMap = {
    identity: IdentityDataSchema,
    ego: EGODataSchema,
    egoGift: EGOGiftDataSchema,
  } as const

  return schemaMap[type]
}

/**
 * Get the appropriate schema for entity i18n data
 */
export function getI18nSchema(type: EntityType): ZodSchema {
  const schemaMap = {
    identity: IdentityI18nSchema,
    ego: EGOI18nSchema,
    egoGift: EGOGiftI18nSchema,
  } as const

  return schemaMap[type]
}

/**
 * Get the appropriate schema for entity spec list
 */
export function getSpecListSchema(type: EntityType): ZodSchema {
  const schemaMap = {
    identity: IdentitySpecListSchema,
    ego: EGOSpecListSchema,
    egoGift: EGOGiftSpecListSchema,
  } as const

  return schemaMap[type]
}

/**
 * Get the appropriate schema for entity name list
 */
export function getNameListSchema(type: EntityType): ZodSchema {
  const schemaMap = {
    identity: IdentityNameListSchema,
    ego: EGONameListSchema,
    egoGift: EGOGiftNameListSchema,
  } as const

  return schemaMap[type]
}

/**
 * Format zod validation errors into user-friendly messages
 * with environment-aware verbosity
 */
export function formatValidationError(
  error: ZodError<unknown>,
  context: {
    entityType: EntityType
    dataKind: DataKind
    id?: string
    elementIndex?: number
  }
): string {
  const isDev = import.meta.env.DEV

  // Build context prefix
  const contextParts: string[] = [context.entityType, context.dataKind]
  if (context.id) contextParts.push(`id: ${context.id}`)
  if (context.elementIndex !== undefined) contextParts.push(`element: ${context.elementIndex}`)
  const contextPrefix = `[${contextParts.join(' / ')}]`

  // Development: Show detailed field-level errors
  if (isDev) {
    const errorDetails = error.issues
      .map((err: any) => {
        const path = err.path.length > 0 ? err.path.join('.') : 'root'
        return `  - ${path}: ${err.message}`
      })
      .join('\n')

    return `${contextPrefix} Validation failed:\n${errorDetails}`
  }

  // Production: Show concise error message
  const errorCount = error.issues.length
  const firstError = error.issues[0]
  const firstPath = firstError?.path.length > 0 ? firstError.path.join('.') : 'root'

  if (errorCount === 1) {
    return `${contextPrefix} Invalid data at ${firstPath}: ${firstError.message}`
  }

  return `${contextPrefix} Invalid data: ${errorCount} validation errors found (first at ${firstPath})`
}

/**
 * Validate data against schema with formatted error messages
 */
export function validateData<T>(
  data: unknown,
  schema: ZodSchema,
  context: {
    entityType: EntityType
    dataKind: DataKind
    id?: string
    elementIndex?: number
  }
): T {
  const result = schema.safeParse(data)

  if (!result.success) {
    throw new Error(formatValidationError(result.error, context))
  }

  return result.data as T
}
