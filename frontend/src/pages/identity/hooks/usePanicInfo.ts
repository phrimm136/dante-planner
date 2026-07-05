import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import type { PanicInfo, PanicInfoEntry } from '../schemas/PanicInfoSchemas'
import { PanicInfoSchema } from '../schemas/PanicInfoSchemas'

/**
 * Query keys for panic info queries
 * Hand-rolled: tuple lacks the 'list'/'i18n' segments the shared factory produces
 */
export const panicInfoQueryKeys = {
  all: ['panicInfo'] as const,
  byLanguage: (language: string) => [...panicInfoQueryKeys.all, language] as const,
}

function createPanicInfoQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    panicInfoQueryKeys.byLanguage(language),
    () => import(`@static/i18n/${language}/panicInfo.json`),
    PanicInfoSchema,
    `panicInfo / ${language}`,
  )
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
