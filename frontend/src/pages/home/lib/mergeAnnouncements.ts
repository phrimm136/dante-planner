import { formatAnnouncementDate } from '@/lib/formatDate'

import type { Announcement, AnnouncementI18n, AnnouncementSpec } from '../types/AnnouncementTypes'

export interface AnnouncementMerge {
  /** Regular entries newest-first, then permanent entries newest-first. */
  announcements: Announcement[]
  /** Spec ids the i18n file has no entry for, in spec order. */
  missingIds: string[]
}

/**
 * Join announcement specs with their translations.
 *
 * Entries whose `expiresAt` is before `now` are dropped; entries absent from
 * `i18nData` are dropped and reported through `missingIds` so the caller can
 * report them.
 *
 * The timezone edge at a day boundary is accepted: `expiresAt` parses as UTC
 * midnight, so an entry expires at the start of its expiry date.
 */
export function mergeAnnouncements(
  specs: readonly AnnouncementSpec[],
  i18nData: AnnouncementI18n,
  language: string,
  now: Date,
): AnnouncementMerge {
  const announcements: Announcement[] = []
  const missingIds: string[] = []

  for (const spec of specs) {
    if (spec.expiresAt && new Date(spec.expiresAt) < now) {
      continue
    }

    const i18nEntry = i18nData[spec.id]
    if (!i18nEntry) {
      missingIds.push(spec.id)
      continue
    }

    announcements.push({
      id: spec.id,
      date: spec.date,
      formattedDate: formatAnnouncementDate(spec.date, language),
      title: i18nEntry.title,
      body: i18nEntry.body,
      permanent: spec.permanent ?? false,
    })
  }

  const byDateDesc = (a: Announcement, b: Announcement) => b.date.localeCompare(a.date)
  const regular = announcements.filter((a) => !a.permanent)
  const permanent = announcements.filter((a) => a.permanent)
  regular.sort(byDateDesc)
  permanent.sort(byDateDesc)

  return { announcements: [...regular, ...permanent], missingIds }
}
