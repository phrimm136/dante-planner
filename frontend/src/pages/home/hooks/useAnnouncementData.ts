import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { AnnouncementSpecListSchema, AnnouncementI18nSchema } from '../schemas/AnnouncementSchemas'
import { mergeAnnouncements } from '../lib/mergeAnnouncements'
import type { Announcement } from '../types/AnnouncementTypes'

// Query key factory for announcement data
// Hand-rolled: tuples lack the 'list' segment the shared factory produces
export const announcementQueryKeys = {
  all: () => ['announcements'] as const,
  spec: () => [...announcementQueryKeys.all(), 'spec'] as const,
  i18n: (language: string) => [...announcementQueryKeys.all(), 'i18n', language] as const,
}

function createSpecQueryOptions() {
  return createStaticDataQueryOptions(
    announcementQueryKeys.spec(),
    () => import('@static/data/announcements.json'),
    AnnouncementSpecListSchema,
    'announcements/spec',
  )
}

function createI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    announcementQueryKeys.i18n(language),
    async () => {
      try {
        return await import(`@static/i18n/${language}/announcements.json`)
      } catch {
        throw new Error(
          `[announcements/i18n] Missing language file for "${language}" — add static/i18n/${language}/announcements.json`,
        )
      }
    },
    AnnouncementI18nSchema,
    `announcements/i18n/${language}`,
  )
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

  const { announcements, missingIds } = mergeAnnouncements(
    specList,
    i18nData,
    i18n.language,
    new Date(),
  )

  for (const id of missingIds) {
    console.error(
      `[useAnnouncementData] Missing i18n entry for id "${id}" in language "${i18n.language}"`,
    )
  }

  return announcements
}
