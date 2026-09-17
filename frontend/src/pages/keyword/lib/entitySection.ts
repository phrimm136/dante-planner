import type { EntitySection } from '@/shared/filter'
import { BattleKeywordIdSchema } from '@/shared/gameData'

import type { BattleKeywordId } from '@/shared/gameData'
import { BattleKeywordSpecSchema, BattleKeywordSpecListSchema } from '@/shared/gameText'
import type { BattleKeywordSpec } from '@/shared/gameText'
import { KEYWORD_FACETS } from './keywordFilter'
import type { KeywordFacetState } from './keywordFilter'
import { toKeywordEntity } from './keywordEntity'

export const section: EntitySection<BattleKeywordId, BattleKeywordSpec, KeywordFacetState> = {
  name: 'keyword',
  specFile: 'battleKeywordSpecList.json',
  specListSchema: BattleKeywordSpecListSchema,
  specEntrySchema: BattleKeywordSpecSchema,
  idSchema: BattleKeywordIdSchema,
  toEntity: toKeywordEntity,
  facets: KEYWORD_FACETS,
}
