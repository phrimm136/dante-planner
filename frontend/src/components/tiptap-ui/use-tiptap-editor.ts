import type { Editor } from '@tiptap/react'
import { useCurrentEditor, useEditorState } from '@tiptap/react'
import { useMemo } from 'react'

export function useTiptapEditor(providedEditor?: Editor | null): {
  editor: Editor | null
  editorState?: Editor['state']
  canCommand?: Editor['can']
} {
  const { editor: coreEditor } = useCurrentEditor()
  const mainEditor = useMemo(() => providedEditor || coreEditor, [providedEditor, coreEditor])

  const editorState = useEditorState({
    editor: mainEditor,
    selector(context) {
      if (!context.editor) {
        return { editor: null }
      }

      return {
        editor: context.editor,
        editorState: context.editor.state,
        canCommand: context.editor.can.bind(context.editor),
      }
    },
  })

  return editorState || { editor: null }
}
