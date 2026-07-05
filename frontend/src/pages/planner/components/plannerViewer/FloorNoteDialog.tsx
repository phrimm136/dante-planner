import { useTranslation } from 'react-i18next'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { NoteEditor } from '@/shared/noteEditor/components/NoteEditor'
import type { NoteContent } from '@/shared/noteEditor'
import { isNoteEmpty } from '@/shared/noteEditor'

interface FloorNoteDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  floorNumber: number
  themePackName: string
  noteContent: NoteContent
}

/**
 * Read-only dialog for viewing theme pack notes in tracker mode
 *
 * Pattern: Dialog wrapper with disabled NoteEditor (read-only display)
 * Empty state: Shows placeholder text when no notes exist
 */
export function FloorNoteDialog({
  open,
  onOpenChange,
  floorNumber,
  themePackName,
  noteContent,
}: FloorNoteDialogProps) {
  const { t } = useTranslation(['planner', 'common'])

  const isEmpty = isNoteEmpty(noteContent)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>
            {t('pages.plannerMD.floor', { number: floorNumber })} - {themePackName}
          </DialogTitle>
        </DialogHeader>

        <div className="mt-4 overflow-y-auto">
          {isEmpty ? (
            <div className="p-8 text-center text-muted-foreground bg-muted/30 rounded-md">
              {t('pages.plannerMD.noteEditor.noNotes', 'No notes for this theme pack')}
            </div>
          ) : (
            <NoteEditor value={noteContent} onChange={() => {}} readOnly={true} placeholder="" />
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
