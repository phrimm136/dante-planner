import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CheckCircle2, FileText } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ThemePackViewer } from '@/components/floorTheme/ThemePackViewer'
import { FloorNoteDialog } from './FloorNoteDialog'
import { cn } from '@/lib/utils'
import type { ThemePackEntry } from '@/types/ThemePackTypes'
import type { NoteContent } from '@/types/NoteEditorTypes'

interface ThemePackTrackerCardProps {
  packId: string
  packEntry: ThemePackEntry
  packName: string
  specialName?: string
  floorNumber: number
  noteContent: NoteContent
  isDone: boolean
  onToggleDone: () => void
  onHoverChange?: (hovering: boolean) => void
}

/**
 * Theme pack card for tracker mode with hover actions.
 * Wraps ThemePackViewer with done-mark and notes overlays.
 */
export function ThemePackTrackerCard({
  packId,
  packEntry,
  packName,
  specialName,
  floorNumber,
  noteContent,
  isDone,
  onToggleDone,
  onHoverChange,
}: ThemePackTrackerCardProps) {
  const { t } = useTranslation(['planner', 'common'])
  const [isHovered, setIsHovered] = useState(false)
  const [showNotesDialog, setShowNotesDialog] = useState(false)

  const handleMouseEnter = () => {
    setIsHovered(true)
    onHoverChange?.(true)
  }

  const handleMouseLeave = () => {
    setIsHovered(false)
    onHoverChange?.(false)
  }

  return (
    <>
      <div
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
      >
        <ThemePackViewer
          packId={packId}
          packEntry={packEntry}
          packName={packName}
          specialName={specialName}
          enableHoverHighlight
          readOnly
          className={cn(isDone && 'brightness-50')}
          overlay={
            isHovered && (
              <div className="absolute inset-0 flex items-center justify-center gap-4">
                <Button
                  size="icon"
                  variant={isDone ? 'default' : 'secondary'}
                  onClick={(e) => {
                    e.stopPropagation()
                    onToggleDone()
                  }}
                  aria-label={isDone ? t('common.markAsNotDone', 'Mark as Not Done') : t('common.markAsDone', 'Mark as Done')}
                >
                  <CheckCircle2 className="h-5 w-5" />
                </Button>
                <Button
                  size="icon"
                  variant="secondary"
                  onClick={(e) => {
                    e.stopPropagation()
                    setShowNotesDialog(true)
                  }}
                  aria-label={t('common.viewNotes', 'View Notes')}
                >
                  <FileText className="h-5 w-5" />
                </Button>
              </div>
            )
          }
        />
      </div>
      <FloorNoteDialog
        open={showNotesDialog}
        onOpenChange={setShowNotesDialog}
        floorNumber={floorNumber}
        themePackName={packName}
        noteContent={noteContent}
      />
    </>
  )
}
