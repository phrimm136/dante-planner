import type { z } from 'zod'
import { createEntityListQueryKeys } from '@/lib/queryKeys'
import {
  useEntityListI18n,
  useEntityListSpec,
  type EntityListDataConfig,
} from '@/shared/entityCatalog'
import { BattleKeywordSpecListSchema } from '../schemas/KeywordSchemas'
import { BattleKeywordsSchema } from '../schemas/BattleKeywordsSchemas'
import type { BattleKeywordSpec } from '../types/KeywordTypes'
import type { BattleKeywordI18nEntry } from '../types/StartBuffTypes'

export const keywordListQueryKeys = createEntityListQueryKeys('keyword')

export const KEYWORD_LIST: EntityListDataConfig<
  z.infer<typeof BattleKeywordSpecListSchema>,
  z.infer<typeof BattleKeywordsSchema>
> = {
  kind: 'keyword',
  specImport: () => import('@static/data/battleKeywordSpecList.json'),
  specSchema: BattleKeywordSpecListSchema,
  i18nImport: (language) => import(`@static/i18n/${language}/battleKeywords.json`),
  i18nSchema: BattleKeywordsSchema,
}

export function useKeywordListSpec(): Record<string, BattleKeywordSpec> {
  return useEntityListSpec(KEYWORD_LIST)
}

export function useKeywordListI18n(): Record<string, BattleKeywordI18nEntry> {
  return useEntityListI18n(KEYWORD_LIST)
}
