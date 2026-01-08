import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { SkillTags } from '@/schemas/SkillTagSchemas'
import { SkillTagSchema } from '@/schemas'

// Query key factory for skill tags
export const skillTagQueryKeys = {
  all: ['skillTag'] as const,
  byLanguage: (language: string) => [...skillTagQueryKeys.all, language] as const,
}

// Skill tag query options with validation
function createSkillTagQueryOptions(language: string) {
  return queryOptions({
    queryKey: skillTagQueryKeys.byLanguage(language),
    queryFn: async () => {
      const response = await fetch(`/i18n/${language}/skillTag.json`)
      if (!response.ok) throw new Error(`Failed to fetch skillTag.json: ${response.status}`)
      const data: unknown = await response.json()
      const result = SkillTagSchema.safeParse(data)

      if (!result.success) {
        if (import.meta.env.DEV) {
          console.error('[skillTag] Validation failed:', result.error.issues)
        }
        throw new Error(`[skillTag / ${language}] Invalid data structure`)
      }

      return result.data as SkillTags
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
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
