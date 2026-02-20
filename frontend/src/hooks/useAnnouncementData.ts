import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { AnnouncementSpecListSchema, AnnouncementI18nSchema } from '@/schemas/AnnouncementSchemas'
import { formatAnnouncementDate } from '@/lib/formatDate'
import type { Announcement } from '@/types/AnnouncementTypes'

// Query key factory for announcement data
export const announcementQueryKeys = {
  all: () => ['announcements'] as const,
  spec: () => [...announcementQueryKeys.all(), 'spec'] as const,
  i18n: (language: string) => [...announcementQueryKeys.all(), 'i18n', language] as const,
}

// Spec query options with runtime validation
function createSpecQueryOptions() {
  return queryOptions({
    queryKey: announcementQueryKeys.spec(),
    queryFn: async () => {
      const module = await import('@static/data/announcements.json')
      const result = AnnouncementSpecListSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[announcements/spec] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days — static content, changes only on deploy
  })
}

// I18n query options with runtime validation
function createI18nQueryOptions(language: string) {
  return queryOptions({
    queryKey: announcementQueryKeys.i18n(language),
    queryFn: async () => {
      let module: { default: unknown }
      try {
        module = await import(`@static/i18n/${language}/announcements.json`)
      } catch {
        throw new Error(`[announcements/i18n] Missing language file for "${language}" — add static/i18n/${language}/announcements.json`)
      }
      const result = AnnouncementI18nSchema.safeParse(module.default)
      if (!result.success) {
        throw new Error(`[announcements/i18n/${language}] Validation failed: ${result.error.message}`)
      }
      return result.data
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days — translations change only on deploy
  })
}

/**
 * Hook that loads announcement data with i18n translations.
 * Suspends while loading — wrap in Suspense boundary.
 *
 * - Filters out expired entries (expiresAt < today; timezone edge at day boundary is accepted)
 * - Sorts newest-first by date
 * - Skips entries missing from i18n (logs error, does not crash)
 *
 * @returns Sorted, filtered array of merged announcements ready for rendering
 */
export function useAnnouncementData(): Announcement[] {
  const { i18n } = useTranslation()

  const { data: specList } = useSuspenseQuery(createSpecQueryOptions())
  const { data: i18nData } = useSuspenseQuery(createI18nQueryOptions(i18n.language))

  const today = new Date()
  const announcements: Announcement[] = []

  for (const spec of specList) {
    // Filter expired entries — timezone edge at day boundary is accepted
    if (spec.expiresAt && new Date(spec.expiresAt) < today) {
      continue
    }

    // Skip entries missing from i18n file; log but do not crash
    const i18nEntry = i18nData[spec.id]
    if (!i18nEntry) {
      console.error(
        `[useAnnouncementData] Missing i18n entry for id "${spec.id}" in language "${i18n.language}"`
      )
      continue
    }

    announcements.push({
      id: spec.id,
      date: spec.date,
      formattedDate: formatAnnouncementDate(spec.date, i18n.language),
      title: i18nEntry.title,
      body: i18nEntry.body,
    })
  }

  // Sort newest-first — ISO date strings sort lexicographically = correct result
  announcements.sort((a, b) => b.date.localeCompare(a.date))

  return announcements
}
