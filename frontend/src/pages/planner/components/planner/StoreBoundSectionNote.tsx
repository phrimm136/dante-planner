import { NoteEditor } from '@/shared/noteEditor/components/NoteEditor'
import { MAX_NOTE_BYTES } from '@/lib/constants'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import type { NoteContent } from '@/shared/noteEditor'

interface StoreBoundSectionNoteProps {
  /** Key into the store's sectionNotes record. */
  sectionKey: string
  placeholder: string
}

/**
 * Renders one section's note against the planner editor store.
 *
 * Each instance subscribes to its own key, so editing a note leaves every other
 * note and the editor shell at zero renders.
 */
export function StoreBoundSectionNote({ sectionKey, placeholder }: StoreBoundSectionNoteProps) {
  const value = usePlannerEditorStore((s) => s.sectionNotes[sectionKey])
  const updateSectionNote = usePlannerEditorStore((s) => s.updateSectionNote)

  return (
    <NoteEditor
      value={value}
      onChange={(content: NoteContent) => {
        updateSectionNote(sectionKey, content)
      }}
      placeholder={placeholder}
      maxBytes={MAX_NOTE_BYTES}
    />
  )
}
