import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import type { SkillTags } from '../schemas/SkillTagSchemas'
import { SkillTagSchema } from '../schemas/SkillTagSchemas'

// Query key factory for skill tags
// Hand-rolled: tuple lacks the 'list'/'i18n' segments the shared factory produces
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

/**
 * Hook that loads skill tags with i18n translations
 * Suspends while loading - wrap in Suspense boundary
 * Used for translating skill/passive description tags like [On Hit], [Clash Win]
 */
export function useSkillTagI18n() {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(createSkillTagQueryOptions(i18n.language))
  return { data }
}

/**
 * Gets translated skill tag text from skill tags data
 * @param skillTags - Skill tags dictionary (validated)
 * @param key - Tag key (e.g., "OnSucceedAttack")
 * @returns Translated display text or original key if not found (no brackets)
 */
export function getSkillTagText(skillTags: SkillTags, key: string): string {
  return skillTags[key] ?? key
}
