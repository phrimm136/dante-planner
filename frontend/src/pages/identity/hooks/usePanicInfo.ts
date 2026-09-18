import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import type { PanicInfo, PanicInfoEntry } from '../schemas/PanicInfoSchemas'
import { PanicInfoSchema } from '../schemas/PanicInfoSchemas'

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

export function usePanicInfo() {
  const { i18n } = useTranslation()
  const { data } = useSuspenseQuery(createPanicInfoQueryOptions(i18n.language))
  return { data }
}

export function getPanicEntry(panicInfo: PanicInfo, panicType: string): PanicInfoEntry | undefined {
  return panicInfo[panicType]
}
