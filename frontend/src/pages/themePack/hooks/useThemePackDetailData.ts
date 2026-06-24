import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ThemePackDetailSchema, ThemePackI18nSchema } from '../schemas/ThemePackSchemas'

// Query key factory for theme pack detail data
export const themePackDetailQueryKeys = {
  all: () => ['themePack'] as const,
  detail: (id: string) => ['themePack', id] as const,
  i18n: (language: string) => ['themePack', 'list', 'i18n', language] as const,
}

// Theme pack detail query options with runtime validation
function createThemePackDetailQueryOptions(id: string) {
  return queryOptions({
    queryKey: themePackDetailQueryKeys.detail(id),
    queryFn: async () => {
      const module = await import(`@static/data/themePack/${id}.json`)
      const result = ThemePackDetailSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[themePack / ${id}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// Reuse the i18n query from list data (same file: themePack.json)
function createThemePackI18nQueryOptions(language: string) {
  return queryOptions({
    queryKey: themePackDetailQueryKeys.i18n(language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/themePack.json`)
      const result = ThemePackI18nSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[themePack i18n] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads theme pack detail spec data (individual file with egoGiftPool, eventPool, etc.)
 *
 * @param id - Theme pack ID
 * @returns Validated theme pack detail data
 */
export function useThemePackDetailSpec(id: string) {
  const { data: spec } = useSuspenseQuery(createThemePackDetailQueryOptions(id))
  return spec
}

/**
 * Hook that loads theme pack detail data (spec + i18n)
 * Suspends while loading - wrap in Suspense boundary
 *
 * @param id - Theme pack ID
 * @returns Validated theme pack detail spec and i18n
 */
export function useThemePackDetailData(id: string) {
  const { i18n } = useTranslation()

  const { data: spec } = useSuspenseQuery(createThemePackDetailQueryOptions(id))
  const { data: i18nData } = useSuspenseQuery(
    createThemePackI18nQueryOptions(i18n.language)
  )

  return {
    spec,
    i18n: i18nData,
  }
}
