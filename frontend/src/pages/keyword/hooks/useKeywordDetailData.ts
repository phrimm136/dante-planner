import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { BattleKeywordSpecListSchema, BattleKeywordsSchema } from '@/shared/gameText'
import type { BattleKeywordSpec } from '@/shared/gameText'
import type { BattleKeywordI18nEntry } from '@/shared/gameText'
import { keywordListQueryKeys } from '@/shared/gameText'

function createKeywordSpecListQueryOptions() {
  return createStaticDataQueryOptions(
    keywordListQueryKeys.spec(),
    () => import('@static/data/battleKeywordSpecList.json'),
    BattleKeywordSpecListSchema,
    'keyword specList',
  )
}

function createKeywordI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    keywordListQueryKeys.i18n(language),
    () => import(`@static/i18n/${language}/battleKeywords.json`),
    BattleKeywordsSchema,
    `keyword i18n / ${language}`,
  )
}

export function useKeywordDetailSpec(id: string): BattleKeywordSpec | undefined {
  const { data: specList } = useSuspenseQuery(createKeywordSpecListQueryOptions())
  return specList[id]
}

export function useKeywordDetailI18n(id: string): BattleKeywordI18nEntry | undefined {
  const { i18n } = useTranslation()
  const { data: i18nList } = useSuspenseQuery(createKeywordI18nQueryOptions(i18n.language))
  return i18nList[id]
}
