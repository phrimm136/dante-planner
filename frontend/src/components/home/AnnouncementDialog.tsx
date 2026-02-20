import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronLeft } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import type { Announcement } from '@/types/AnnouncementTypes'

// ============================================================================
// Inner Content Component
// ============================================================================

interface AnnouncementDialogContentProps {
  announcements: Announcement[]
  initialSelectedId: string | null
}

/**
 * Inner component owning list/detail selection state.
 * Separated from the Dialog shell so selection re-renders stay isolated.
 */
function AnnouncementDialogContent({ announcements, initialSelectedId }: AnnouncementDialogContentProps) {
  const { t } = useTranslation('common')
  const [selectedId, setSelectedId] = useState<string | null>(initialSelectedId)

  useEffect(() => {
    setSelectedId(initialSelectedId)
  }, [initialSelectedId])

  const selected = selectedId ? announcements.find((a) => a.id === selectedId) ?? null : null

  if (selected) {
    return (
      <div className="flex flex-col gap-2">
        <Button
          variant="ghost"
          size="sm"
          className="self-start -ml-2"
          onClick={() => setSelectedId(null)}
        >
          <ChevronLeft className="size-4 mr-1" />
          {t('announcements.backToList')}
        </Button>
        <h3 className="text-base font-semibold">{selected.title}</h3>
        <p className="text-sm text-muted-foreground">{selected.formattedDate}</p>
        <p className="whitespace-pre-wrap text-sm mt-2">{selected.body}</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col">
      {announcements.length === 0 ? (
        <p className="text-sm text-muted-foreground text-center py-8">
          {t('announcements.noAnnouncements')}
        </p>
      ) : (
        announcements.map((announcement) => (
          <button
            key={announcement.id}
            type="button"
            className="flex items-center justify-between cursor-pointer hover:bg-accent rounded-md px-3 py-2 transition-colors text-left"
            onClick={() => setSelectedId(announcement.id)}
          >
            <span className="text-sm font-medium truncate pr-4">{announcement.title}</span>
            <span className="text-sm text-muted-foreground shrink-0">{announcement.formattedDate}</span>
          </button>
        ))
      )}
    </div>
  )
}

// ============================================================================
// Dialog Shell
// ============================================================================

interface AnnouncementDialogProps {
  announcements: Announcement[]
  open: boolean
  onOpenChange: (open: boolean) => void
  /** When set, dialog opens directly in detail view for this id */
  initialSelectedId?: string | null
}

/**
 * Announcement dialog — shell component.
 * Owns open/close and seeds the initial selection; content state lives in AnnouncementDialogContent.
 */
export function AnnouncementDialog({ announcements, open, onOpenChange, initialSelectedId = null }: AnnouncementDialogProps) {
  const { t } = useTranslation('common')

  // Derive seed: null when closed so content resets to list view on reopen
  const seed = open ? (initialSelectedId ?? null) : null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-full max-w-lg">
        <DialogHeader>
          <DialogTitle>{t('announcements.title')}</DialogTitle>
        </DialogHeader>

        <div className="overflow-y-auto max-h-[60vh]">
          <AnnouncementDialogContent
            announcements={announcements}
            initialSelectedId={seed}
          />
        </div>
      </DialogContent>
    </Dialog>
  )
}
