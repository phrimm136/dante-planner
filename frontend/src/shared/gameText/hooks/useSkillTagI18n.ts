import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { SkillTagSchema } from '../schemas/SkillTagSchemas'

export const skillTagQueryKeys = {
  all: ['skillTag'] as const,
  byLanguage: (language: string) => [...skillTagQueryKeys.all, language] as const,
}

function createSkillTagQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    skillTagQueryKeys.byLanguage(language),
    () => import(`@static/i18n/${language}/skillTag.json`),
    SkillTagSchema,
    `skillTag / ${language}`,
  )
}

export function useSkillTagI18n() {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(createSkillTagQueryOptions(i18n.language))
  return { data }
}
