/**
 * Keyword Types
 *
 * Re-exports schema-derived spec types and the entity the keyword browser renders.
 */

import type { z } from 'zod'
import type { Entity } from '@/shared/filter'
import type { BattleKeywordId } from '@/shared/gameData'
import type { BattleKeywordSpec, BattleKeywordSpecListSchema } from '@/shared/gameText'

export type BattleKeywordSpecList = z.infer<typeof BattleKeywordSpecListSchema>

export type KeywordEntity = Entity<BattleKeywordId, BattleKeywordSpec>
