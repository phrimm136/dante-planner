/** The single constructor turning an abnormality event spec entry into an entity. */

import { createEntityBuilder } from '@/shared/filter'
import { AbEventIdSchema } from '@/shared/gameData'

import type { AbEventId } from '@/shared/gameData'
import type { AbEventSpec } from '../types/AbEventTypes'

/**
 * Build an entity from a spec entry.
 *
 * @param id - Event ID, as the spec record keys it
 * @param spec - The event's spec entry
 * @param name - Localized name; omitted where no name catalogue is loaded
 * @returns The entity, carrying every field the spec provides
 */
export const toAbEventEntity = createEntityBuilder<AbEventId, AbEventSpec>(AbEventIdSchema)
