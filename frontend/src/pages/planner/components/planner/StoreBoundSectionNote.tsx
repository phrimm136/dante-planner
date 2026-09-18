import { NoteEditor } from '@/shared/noteEditor/components/NoteEditor'
import { MAX_NOTE_BYTES } from '@/lib/constants'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import { createEmptyNoteContent } from '@/shared/noteEditor'
import type { NoteContent } from '@/shared/noteEditor'

const EMPTY_NOTE = createEmptyNoteContent()

interface StoreBoundSectionNoteProps {
  sectionKey: string
  placeholder: string
}

export function StoreBoundSectionNote({ sectionKey, placeholder }: StoreBoundSectionNoteProps) {
  const value = usePlannerEditorStore((s) => s.sectionNotes[sectionKey])
  const updateSectionNote = usePlannerEditorStore((s) => s.updateSectionNote)

  return (
    <NoteEditor
      value={value ?? EMPTY_NOTE}
      onChange={(content: NoteContent) => {
        updateSectionNote(sectionKey, content)
      }}
      placeholder={placeholder}
      maxBytes={MAX_NOTE_BYTES}
    />
  )
}
