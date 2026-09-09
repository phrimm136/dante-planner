import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import type { MDVersion } from '@/shared/gameData'
import { StartBuffDataListSchema, StartBuffI18nSchema } from '@/shared/gameText'

export const startBuffQueryKeys = {
  all: (version: MDVersion) => ['startBuff', `md${version}`] as const,
  data: (version: MDVersion) => [...startBuffQueryKeys.all(version), 'data'] as const,
  i18n: (version: MDVersion, language: string) =>
    [...startBuffQueryKeys.all(version), 'i18n', language] as const,
}

function createSpecQueryOptions(version: MDVersion) {
  return createStaticDataQueryOptions(
    startBuffQueryKeys.data(version),
    () => import(`@static/data/MD${version}/startBuffs.json`),
    StartBuffDataListSchema,
    `startBuffs/md${version}`,
  )
}

function createI18nQueryOptions(version: MDVersion, language: string) {
  return createStaticDataQueryOptions(
    startBuffQueryKeys.i18n(version, language),
    () => import(`@static/i18n/${language}/MD${version}/startBuffs.json`),
    StartBuffI18nSchema,
    `startBuffs i18n/${language}/md${version}`,
  )
}

/** Start buff specs for one Mirror Dungeon version; suspends on initial load, not on language change */
export function useStartBuffListSpec(version: MDVersion) {
  const { data } = useSuspenseQuery(createSpecQueryOptions(version))
  return data
}

/** Start buff names (localizeId -> text); suspends while loading */
export function useStartBuffListI18n(version: MDVersion) {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(createI18nQueryOptions(version, i18n.language))
  return data
}
