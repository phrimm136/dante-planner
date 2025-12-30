import { z } from 'zod'
import { SEASONS, ASSOCIATIONS } from '@/lib/constants'

/**
 * Filter I18n Schemas
 *
 * Zod schemas for runtime validation of filter-related i18n data.
 * Mirrors types from FilterTypes.ts.
 */

/**
 * Seasons i18n schema - maps season ID string to localized name
 * Dynamically generated from SEASONS constant
 */
export const SeasonsI18nSchema = z.object(
  Object.fromEntries(SEASONS.map((season) => [String(season), z.string()])) as {
    [K in `${typeof SEASONS[number]}`]: z.ZodString
  }
).strict()

/**
 * Associations i18n schema - maps association code to localized name
 * Dynamically generated from ASSOCIATIONS constant
 */
export const AssociationsI18nSchema = z.object(
  Object.fromEntries(ASSOCIATIONS.map((assoc) => [assoc, z.string()])) as {
    [K in typeof ASSOCIATIONS[number]]: z.ZodString
  }
).strict()
