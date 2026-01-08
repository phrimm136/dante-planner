import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ThemePackListSchema, ThemePackI18nSchema } from '@/schemas'

// Query key factory for theme pack list data
export const themePackListQueryKeys = {
  all: () => ['themePack', 'list'] as const,
  data: () => ['themePack', 'list', 'data'] as const,
  i18n: (language: string) => ['themePack', 'list', 'i18n', language] as const,
}

// Theme pack list query options with runtime validation
function createThemePackListQueryOptions() {
  return queryOptions({
    queryKey: themePackListQueryKeys.data(),
    queryFn: async () => {
      const module = await import('@static/data/themePackList.json')
      const result = ThemePackListSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[themePack list] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// Theme pack i18n query options (names)
function createThemePackI18nQueryOptions(language: string) {
  return queryOptions({
    queryKey: themePackListQueryKeys.i18n(language),
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
 * Hook that loads theme pack i18n only (language-dependent)
 * Suspends while loading - wrap in Suspense boundary
 *
 * Use this for components that only need translated names.
 *
 * @returns Theme pack i18n map (ID -> {name, specialName?})
 */
export function useThemePackI18n() {
  const { i18n } = useTranslation()
  const { data: themePackI18n } = useSuspenseQuery(
    createThemePackI18nQueryOptions(i18n.language)
  )
  return themePackI18n
}

/**
 * Hook that loads and validates theme pack list data
 * Suspends while loading - wrap in Suspense boundary
 *
 * @returns Validated theme pack list and i18n data
 */
export function useThemePackListData() {
  const { i18n } = useTranslation()

  const { data: themePackList } = useSuspenseQuery(createThemePackListQueryOptions())
  const { data: themePackI18n } = useSuspenseQuery(
    createThemePackI18nQueryOptions(i18n.language)
  )

  return {
    themePackList,
    themePackI18n,
  }
}
