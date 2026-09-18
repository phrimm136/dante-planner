import { useState, useEffect, useRef } from 'react'
import { useEditor, EditorContent, EditorContext } from '@tiptap/react'
import { ErrorBoundary as ReactErrorBoundary, type FallbackProps } from 'react-error-boundary'
import { useTranslation } from 'react-i18next'
import { showErrorMessage } from '@/lib/errorPresentation'
import StarterKit from '@tiptap/starter-kit'

import { cn } from '@/lib/utils'
import { measureDocBytes, largestPrefixWithinLimit, isNoteEmpty } from '../lib/noteUtils'
import { sanitizeUrl } from '../lib/tiptap-utils'
import { MAX_NOTE_BYTES } from '@/lib/constants'
import type { JSONContent } from '@tiptap/core'
import type { NoteEditorProps } from '../types/NoteEditorTypes'
import { SpoilerExtension } from './extensions/SpoilerExtension'
import { ByteLimitExtension, BYTE_LIMIT_BYPASS } from './extensions/ByteLimitExtension'
import { Toolbar } from './Toolbar'
import { LinkDialog } from './LinkDialog'

import './NoteEditor.css'

function EditorErrorFallback({ resetErrorBoundary }: FallbackProps) {
  const { t } = useTranslation(['planner', 'common'])
  return (
    <div className="p-3 text-center text-sm text-muted-foreground bg-muted/50 rounded min-h-[100px] flex flex-col items-center justify-center gap-2">
      <span>{t('pages.plannerMD.noteEditor.errorFallback.loadFailed')}</span>
      <button onClick={resetErrorBoundary} className="text-xs underline hover:text-foreground">
        {t('pages.plannerMD.noteEditor.errorFallback.tryAgain')}
      </button>
    </div>
  )
}

function NoteEditorInner({
  value,
  onChange,
  placeholder,
  readOnly = false,
  className,
  maxBytes,
}: NoteEditorProps) {
  const { t } = useTranslation(['planner', 'common'])
  const containerRef = useRef<HTMLDivElement>(null)

  const [isFocused, setIsFocused] = useState(false)
  const [linkDialogOpen, setLinkDialogOpen] = useState(false)
  const [selectedText, setSelectedText] = useState('')

  const byteLimit = maxBytes ?? MAX_NOTE_BYTES

  // Tiptap reports its own reparse of the loaded note as an update. Until this
  // editor has emitted something, an empty note that is still empty is a load,
  // not an edit.
  const [loadedEmpty] = useState(() => isNoteEmpty(value))
  const hasEmittedRef = useRef(false)
  const lastEmittedRef = useRef<JSONContent | null>(null)
  const emit = (content: JSONContent) => {
    if (!hasEmittedRef.current && loadedEmpty && isNoteEmpty({ content })) return
    hasEmittedRef.current = true
    lastEmittedRef.current = content
    onChange?.({ content })
  }
  const emitRef = useRef(emit)
  useEffect(() => {
    emitRef.current = emit
  })

  const currentBytes = value.content ? measureDocBytes(value.content) : 0

  const extensions = [
    // StarterKit includes Link by default in Tiptap v3
    // Configure Link through StarterKit to avoid duplicate extension warning
    StarterKit.configure({
      heading: {
        levels: [1, 2, 3],
      },
      link: {
        openOnClick: false,
        HTMLAttributes: {
          class: 'note-link',
        },
      },
    }),
    SpoilerExtension,
    ByteLimitExtension.configure({ limit: byteLimit }),
  ]

  const editor = useEditor({
    extensions,
    content: value.content,
    editable: !readOnly,
    onUpdate: ({ editor }) => emit(editor.getJSON()),
    editorProps: {
      attributes: {
        class: 'note-editor-content prose prose-sm max-w-none focus:outline-none min-h-[100px] p-3',
      },
      handleDOMEvents: {
        dragstart: () => true, // Return true to prevent default drag behavior
      },
      handlePaste: (view, event, slice) => {
        const { state } = view
        const prospective = state.tr.replaceSelection(slice)
        if (measureDocBytes(prospective.doc.toJSON()) <= byteLimit) {
          return false
        }

        const rawText =
          event.clipboardData?.getData('text/plain') ||
          slice.content.textBetween(0, slice.content.size, '\n')

        if (!rawText) {
          return false
        }

        // UTF-8 is ≤4 bytes/char and structure only adds, so no prefix longer
        // than byteLimit*4 chars can ever fit. Clamp the search domain so a
        // multi-MB paste can't drive megabyte JSON.stringify probes (self-DoS).
        const text = rawText.slice(0, byteLimit * 4)
        const { from, to } = state.selection

        const best = largestPrefixWithinLimit(
          text,
          (candidate) => measureDocBytes(state.tr.insertText(candidate, from, to).doc.toJSON()),
          byteLimit,
        )

        if (best > 0) {
          view.dispatch(state.tr.insertText(text.slice(0, best), from, to))
        }
        return true
      },
    },
  })

  useEffect(() => {
    if (!editor || hasEmittedRef.current) return
    emitRef.current(editor.getJSON())
  }, [editor])

  const gestureRef = useRef<{
    down: boolean
    pending: AbortController | null
    release: ReturnType<typeof setTimeout> | null
  }>({
    down: false,
    pending: null,
    release: null,
  })

  useEffect(() => {
    const gesture = gestureRef.current
    const markDown = () => {
      gesture.down = true
    }
    const markUp = () => {
      gesture.down = false
    }

    document.addEventListener('pointerdown', markDown, true)
    document.addEventListener('pointerup', markUp, true)
    document.addEventListener('pointercancel', markUp, true)

    return () => {
      document.removeEventListener('pointerdown', markDown, true)
      document.removeEventListener('pointerup', markUp, true)
      document.removeEventListener('pointercancel', markUp, true)
      gesture.pending?.abort()
      if (gesture.release) {
        clearTimeout(gesture.release)
        gesture.release = null
      }
    }
  }, [])

  useEffect(() => {
    if (editor) {
      editor.setEditable(!readOnly)
    }
  }, [editor, readOnly])

  useEffect(() => {
    if (editor && value.content && value.content !== lastEmittedRef.current) {
      const currentContent = JSON.stringify(editor.getJSON())
      const newContent = JSON.stringify(value.content)
      if (currentContent !== newContent) {
        editor.chain().setMeta(BYTE_LIMIT_BYPASS, true).setContent(value.content).run()
      }
    }
  }, [editor, value.content])

  const handleFocus = () => {
    if (!readOnly && !isFocused) {
      setIsFocused(true)
    }
  }

  /**
   * Drop the toolbar, but never between a press and its release.
   *
   * The toolbar is a row in the flow, so removing it lifts everything below by its
   * height. A press outside the editor blurs it, and the blur is delivered before
   * the release — collapse on arrival and the control under the pointer travels
   * out from under it, so no `click` is ever composed and nothing downstream runs.
   */
  const collapseToolbar = () => {
    const gesture = gestureRef.current

    if (!gesture.down) {
      setIsFocused(false)
      return
    }

    gesture.pending?.abort()
    const controller = new AbortController()
    gesture.pending = controller

    const finish = () => {
      controller.abort()
      gesture.pending = null
      // `click` is dispatched after `pointerup` within the same task, so yielding a
      // task lets the press land before the row disappears.
      if (gesture.release) {
        clearTimeout(gesture.release)
      }
      gesture.release = setTimeout(() => {
        gesture.release = null
        setIsFocused(false)
      }, 0)
    }

    document.addEventListener('pointerup', finish, { signal: controller.signal })
    document.addEventListener('pointercancel', finish, { signal: controller.signal })
  }

  const handleBlur = (e: React.FocusEvent) => {
    const relatedTarget = e.relatedTarget as HTMLElement | null

    if (containerRef.current && !containerRef.current.contains(relatedTarget)) {
      if (!linkDialogOpen) {
        collapseToolbar()
      }
    }
  }

  const handleLinkClick = () => {
    if (!editor) return
    const { from, to } = editor.state.selection
    const text = editor.state.doc.textBetween(from, to, ' ')
    setSelectedText(text)
    setLinkDialogOpen(true)
  }

  const handleLinkConfirm = (url: string, text?: string) => {
    if (!editor) return

    let processedUrl = url
    if (!/^https?:\/\//i.test(processedUrl)) {
      processedUrl = `https://${processedUrl}`
    }

    const safeUrl = sanitizeUrl(processedUrl, window.location.origin)
    if (safeUrl === '#') {
      showErrorMessage('planner:pages.plannerMD.noteEditor.linkDialog.invalidUrl')
      return
    }

    if (text && text !== selectedText) {
      editor
        .chain()
        .focus()
        .insertContent({
          type: 'text',
          text: text,
          marks: [{ type: 'link', attrs: { href: safeUrl } }],
        })
        .run()
    } else {
      editor.chain().focus().setLink({ href: safeUrl }).run()
    }

    setLinkDialogOpen(false)
    setSelectedText('')
  }

  const handleLinkClose = () => {
    setLinkDialogOpen(false)
    setSelectedText('')
  }

  if (!editor) {
    return null
  }

  return (
    <EditorContext.Provider value={{ editor }}>
      <div
        ref={containerRef}
        className={cn(
          'note-editor rounded-md border border-input bg-background relative',
          isFocused && 'ring-2 ring-ring ring-offset-2',
          className,
        )}
        onFocus={handleFocus}
        onBlur={handleBlur}
      >
        <Toolbar editor={editor} visible={isFocused && !readOnly} onLinkClick={handleLinkClick} />

        <ReactErrorBoundary
          FallbackComponent={EditorErrorFallback}
          onError={(error) => {
            console.error('NoteEditor error:', error)
          }}
        >
          <EditorContent editor={editor} />

          {!isFocused && editor?.isEmpty && (
            <div className="absolute top-0 left-0 p-3 text-muted-foreground pointer-events-none">
              {readOnly
                ? t('pages.plannerMD.noteEditor.placeholderReadOnly')
                : placeholder || t('pages.plannerMD.noteEditor.placeholder')}
            </div>
          )}
        </ReactErrorBoundary>

        <LinkDialog
          open={linkDialogOpen}
          onClose={handleLinkClose}
          onConfirm={handleLinkConfirm}
          initialText={selectedText}
        />

        {!readOnly && byteLimit > 0 && (
          <div className="px-3 pb-2 text-right">
            <span
              className={cn(
                'text-xs',
                currentBytes > byteLimit ? 'text-destructive' : 'text-muted-foreground',
              )}
            >
              {currentBytes}/{byteLimit} {t('pages.plannerMD.bytes')}
            </span>
          </div>
        )}
      </div>
    </EditorContext.Provider>
  )
}

export const NoteEditor = NoteEditorInner
