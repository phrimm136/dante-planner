/** The single constructor turning a theme pack spec entry into an entity. */

import { createEntityBuilder } from '@/shared/filter'
import { ThemePackIdSchema } from '@/shared/gameData'

import type { ThemePackId } from '@/shared/gameData'
import type { ThemePackSpec } from '../types/ThemePackTypes'

/**
 * Build an entity from a spec entry.
 *
 * @param id - Theme pack ID, as the spec record keys it
 * @param spec - The theme pack's spec entry
 * @param name - Localized name; omitted where no name catalogue is loaded
 * @returns The entity, carrying every field the spec provides
 */
export const toThemePackEntity = createEntityBuilder<ThemePackId, ThemePackSpec>(ThemePackIdSchema)
