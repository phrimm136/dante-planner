import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { AnnouncementSpecListSchema, AnnouncementI18nSchema } from '../schemas/AnnouncementSchemas'
import { mergeAnnouncements } from '../lib/mergeAnnouncements'
import type { Announcement } from '../types/AnnouncementTypes'

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
