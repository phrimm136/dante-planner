/** The single constructor turning an identity spec entry into an entity. */

import { createEntityBuilder } from '@/shared/filter'
import { IdentityIdSchema } from '@/shared/gameData'

import type { IdentityId } from '@/shared/gameData'
import type { IdentitySpec } from '../types/IdentityTypes'

/**
 * Build an entity from a spec entry.
 *
 * @param id - Identity ID, as the spec record keys it
 * @param spec - The identity's spec entry
 * @param name - Localized name; omitted where no name catalogue is loaded
 * @returns The entity, carrying every field the spec provides
 */
export const toIdentityEntity = createEntityBuilder<IdentityId, IdentitySpec>(IdentityIdSchema)
