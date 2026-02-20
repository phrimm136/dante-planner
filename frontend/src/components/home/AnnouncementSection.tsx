import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ANNOUNCEMENT_PREVIEW_COUNT } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { Announcement } from '@/types/AnnouncementTypes'

// ============================================================================
// Loading Skeleton
// ============================================================================

export function AnnouncementSkeleton() {
  return (
    <section className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <Skeleton className="h-7 w-24" />
        <Skeleton className="h-9 w-24" />
      </div>
      <div className="bg-muted border border-border rounded-md p-4 flex flex-col">
        {Array.from({ length: ANNOUNCEMENT_PREVIEW_COUNT }).map((_, i) => (
          <div key={i} className="flex items-center justify-between py-2 border-b border-border last:border-0">
            <Skeleton className="h-5 w-3/4" />
            <Skeleton className="h-4 w-32" />
          </div>
        ))}
      </div>
    </section>
  )
}

// ============================================================================
// Main Section Component
// ============================================================================

interface AnnouncementSectionProps {
  announcements: Announcement[]
  onViewAll: () => void
  onOpenAnnouncement: (id: string) => void
}

export function AnnouncementSection({ announcements, onViewAll, onOpenAnnouncement }: AnnouncementSectionProps) {
  const { t } = useTranslation('common')
  const preview = announcements.slice(0, ANNOUNCEMENT_PREVIEW_COUNT)

  return (
    <section className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold">{t('announcements.title')}</h2>
        <button
          size="sm"
          onClick={onViewAll}
          className={cn(
            'flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors'
          )}
        >
          {t('announcements.viewAll')}
        </button>
      </div>
      <div className="bg-muted border border-border rounded-md p-4 flex flex-col">
        {preview.map((announcement) => (
          <div
            key={announcement.id}
            className="flex items-center justify-between py-2 border-b border-border last:border-0"
          >
            <button
              type="button"
              className="text-sm font-medium truncate pr-4 text-left hover:underline cursor-pointer"
              onClick={() => onOpenAnnouncement(announcement.id)}
            >
              {announcement.title}
            </button>
            <span className="text-sm text-muted-foreground shrink-0">{announcement.formattedDate}</span>
          </div>
        ))}
      </div>
    </section>
  )
}
