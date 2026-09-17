/** The single constructor turning a keyword spec entry into an entity. */

import { createEntityBuilder } from '@/shared/filter'
import { BattleKeywordIdSchema } from '@/shared/gameData'

import type { BattleKeywordId } from '@/shared/gameData'
import type { BattleKeywordSpec } from '@/shared/gameText'

/**
 * Build an entity from a spec entry.
 *
 * @param id - Keyword ID, as the spec record keys it
 * @param spec - The keyword's spec entry
 * @param name - Localized name; omitted where no name catalogue is loaded
 * @returns The entity, carrying every field the spec provides
 */
export const toKeywordEntity = createEntityBuilder<BattleKeywordId, BattleKeywordSpec>(
  BattleKeywordIdSchema,
)
