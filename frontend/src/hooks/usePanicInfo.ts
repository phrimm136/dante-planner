import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import type { PanicInfo, PanicInfoEntry } from '@/schemas/PanicInfoSchemas'
import { PanicInfoSchema } from '@/schemas'

/**
 * Query keys for panic info queries
 */
export const panicInfoQueryKeys = {
  all: ['panicInfo'] as const,
  byLanguage: (language: string) => [...panicInfoQueryKeys.all, language] as const,
}

/**
 * Query options for fetching panic info with validation
 */
function createPanicInfoQueryOptions(language: string) {
  return queryOptions({
    queryKey: panicInfoQueryKeys.byLanguage(language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/panicInfo.json`)
      const result = PanicInfoSchema.safeParse(module.default)

      if (!result.success) {
        const isDev = import.meta.env.DEV
        if (isDev) {
          console.error('[panicInfo] Validation failed:', result.error.issues)
        }
        throw new Error(`[panicInfo / ${language}] Invalid data structure`)
      }

      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads panic info with i18n translations
 * Suspends while loading - wrap in Suspense boundary
 * Used for displaying panic type name and description on identity pages
 *
 * @example
 * ```tsx
 * function PanicDisplay({ panicType }: { panicType: number }) {
 *   const { data: panicInfo } = usePanicInfo();
 *   const entry = getPanicEntry(panicInfo, panicType);
 *
 *   return <div>{entry?.name}: {entry?.panicDesc}</div>;
 * }
 * ```
 */
export function usePanicInfo() {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(createPanicInfoQueryOptions(i18n.language))
  return { data }
}

/**
 * Gets panic entry by panic type ID
 * @param panicInfo - Panic info dictionary (validated)
 * @param panicType - Panic type ID (number)
 * @returns Panic info entry or undefined if not found
 */
export function getPanicEntry(panicInfo: PanicInfo, panicType: number): PanicInfoEntry | undefined {
  return panicInfo[String(panicType)]
}
