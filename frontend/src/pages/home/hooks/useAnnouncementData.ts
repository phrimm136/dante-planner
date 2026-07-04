import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { AnnouncementSpecListSchema, AnnouncementI18nSchema } from '../schemas/AnnouncementSchemas'
import { formatAnnouncementDate } from '@/lib/formatDate'
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
        `[useAnnouncementData] Missing i18n entry for id "${spec.id}" in language "${i18n.language}"`,
      )
      continue
    }

    announcements.push({
      id: spec.id,
      date: spec.date,
      formattedDate: formatAnnouncementDate(spec.date, i18n.language),
      title: i18nEntry.title,
      body: i18nEntry.body,
      permanent: spec.permanent ?? false,
    })
  }

  // Partition: regular (newest-first), then permanent (newest-first)
  const regular = announcements.filter((a) => !a.permanent)
  const permanent = announcements.filter((a) => a.permanent)
  regular.sort((a, b) => b.date.localeCompare(a.date))
  permanent.sort((a, b) => b.date.localeCompare(a.date))

  return [...regular, ...permanent]
}
