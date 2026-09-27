import { useTranslation } from 'react-i18next'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ReadOnlyNote } from '@/shared/noteEditor/components/ReadOnlyNote'
import type { NoteContent } from '@/shared/noteEditor'
import { isNoteEmpty } from '@/shared/noteEditor'

interface SectionNoteDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  sectionTitle: string
  noteContent: NoteContent | undefined
}

export function SectionNoteDialog({
  open,
  onOpenChange,
  sectionTitle,
  noteContent,
}: SectionNoteDialogProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{sectionTitle}</DialogTitle>
        </DialogHeader>

        <div className="mt-4 overflow-y-auto">
          {noteContent && !isNoteEmpty(noteContent) ? (
            <ReadOnlyNote value={noteContent} />
          ) : (
            <div className="p-8 text-center text-muted-foreground bg-muted/30 rounded-md">
              {t('pages.plannerMD.noteEditor.placeholderReadOnly')}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
