import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CheckCircle2, FileText } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { FloorNoteDialog } from './FloorNoteDialog'
import { getThemePackImagePath } from '@/lib/assetPaths'
import { cn } from '@/lib/utils'
import { isExtremePack } from '@/types/ThemePackTypes'
import type { ThemePackEntry } from '@/types/ThemePackTypes'
import type { NoteContent } from '@/types/NoteEditorTypes'

interface ThemePackTrackerCardProps {
  packId: string
  packEntry: ThemePackEntry
  packName: string
  floorNumber: number
  noteContent: NoteContent
  isDone: boolean
  onToggleDone: () => void
  onHoverChange?: (hovering: boolean) => void
}

/**
 * Theme pack card for tracker mode with hover actions
 * Based on ThemePackSelectorPane pattern with action buttons
 */
export function ThemePackTrackerCard({
  packId,
  packEntry,
  packName,
  floorNumber,
  noteContent,
  isDone,
  onToggleDone,
  onHoverChange,
}: ThemePackTrackerCardProps) {
  const { t } = useTranslation(['planner', 'common'])
  const [isHovered, setIsHovered] = useState(false)
  const [showNotesDialog, setShowNotesDialog] = useState(false)

  const isExtreme = isExtremePack(packEntry)
  const textColor = `#${packEntry.themePackConfig.textColor}`

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
        className="relative w-56 h-104 rounded-lg overflow-hidden"
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
      >
        {/* Theme pack image */}
        <img
          src={getThemePackImagePath(packId)}
          alt={packName}
          className={cn(
            'w-full h-full object-cover object-center',
            isDone && 'brightness-50'
          )}
        />

        {/* Theme pack name overlay */}
        <div
          className={cn(
            'absolute left-0 right-0 px-2 py-1 text-center',
            isExtreme ? 'bottom-16' : 'bottom-8',
            isDone && 'brightness-50'
          )}
        >
          <span
            className="text-sm font-bold drop-shadow-[0_2px_2px_rgba(0,0,0,0.8)]"
            style={{ color: textColor }}
          >
            {packName}
          </span>
        </div>

        {/* Hover Action Buttons */}
        {isHovered && (
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
        )}
      </div>

      {/* Notes Dialog */}
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
