/** The single constructor turning an EGO spec entry into an entity. */

import { createEntityBuilder } from '@/shared/filter'
import { EGOIdSchema } from '@/shared/gameData'

import type { EGOId } from '@/shared/gameData'
import type { EGOSpec } from '../types/EGOTypes'

/**
 * Build an entity from a spec entry.
 *
 * @param id - EGO ID, as the spec record keys it
 * @param spec - The EGO's spec entry
 * @param name - Localized name; omitted where no name catalogue is loaded
 * @returns The entity, carrying every field the spec provides
 */
export const toEGOEntity = createEntityBuilder<EGOId, EGOSpec>(EGOIdSchema)
