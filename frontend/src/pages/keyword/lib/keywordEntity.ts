import { createEntityBuilder } from '@/shared/filter'
import { BattleKeywordIdSchema } from '@/shared/gameData'

import type { BattleKeywordId } from '@/shared/gameData'
import type { BattleKeywordSpec } from '@/shared/gameText'

export const toKeywordEntity = createEntityBuilder<BattleKeywordId, BattleKeywordSpec>(
  BattleKeywordIdSchema,
)
