import { useTranslation } from 'react-i18next'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { NoteEditor } from '@/shared/noteEditor/components/NoteEditor'
import type { NoteContent } from '@/shared/noteEditor'
import { isNoteEmpty } from '@/shared/noteEditor'

interface SectionNoteDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  sectionTitle: string
  noteContent: NoteContent
  onChange?: (content: NoteContent) => void
  readOnly?: boolean
}

/**
 * Generic dialog for viewing/editing section notes
 *
 * Pattern: Dialog wrapper with NoteEditor
 * - readOnly mode: For viewer pages (guide/tracker)
 * - Edit mode: For planner editor
 * Empty state: Shows placeholder text when no notes exist
 */
export function SectionNoteDialog({
  open,
  onOpenChange,
  sectionTitle,
  noteContent,
  onChange,
  readOnly = false,
}: SectionNoteDialogProps) {
  const { t } = useTranslation(['planner', 'common'])

  const isEmpty = isNoteEmpty(noteContent)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{sectionTitle}</DialogTitle>
        </DialogHeader>

        <div className="mt-4 overflow-y-auto">
          {isEmpty && readOnly ? (
            <div className="p-8 text-center text-muted-foreground bg-muted/30 rounded-md">
              {t('pages.plannerMD.noteEditor.placeholderReadOnly')}
            </div>
          ) : (
            <NoteEditor
              value={noteContent}
              onChange={onChange || (() => {})}
              readOnly={readOnly}
              placeholder={t('pages.plannerMD.noteEditor.placeholder')}
              maxBytes={2000}
            />
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
