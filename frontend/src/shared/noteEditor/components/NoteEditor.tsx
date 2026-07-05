import { useState, useEffect, useRef, useMemo, memo } from 'react'
import { useEditor, EditorContent, EditorContext } from '@tiptap/react'
import { ErrorBoundary } from 'react-error-boundary'
import { useTranslation } from 'react-i18next'
import { toast } from '@/lib/toast'
import StarterKit from '@tiptap/starter-kit'

import { cn } from '@/lib/utils'
import { measureDocBytes, largestPrefixWithinLimit } from '../lib/noteUtils'
import { sanitizeUrl } from '../lib/tiptap-utils'
import { MAX_NOTE_BYTES } from '@/lib/constants'
import type { NoteEditorProps } from '../types/NoteEditorTypes'
import { SpoilerExtension } from './extensions/SpoilerExtension'
import { ByteLimitExtension, BYTE_LIMIT_BYPASS } from './extensions/ByteLimitExtension'
import { Toolbar } from './Toolbar'
import { LinkDialog } from './LinkDialog'

import './NoteEditor.css'

/**
 * EditorErrorFallback - Simple inline fallback when editor crashes
 */
function EditorErrorFallback({
  resetErrorBoundary,
  t,
}: {
  resetErrorBoundary: () => void
  t: (key: string) => string
}) {
  return (
    <div className="p-3 text-center text-sm text-muted-foreground bg-muted/50 rounded min-h-[100px] flex flex-col items-center justify-center gap-2">
      <span>{t('pages.plannerMD.noteEditor.errorFallback.loadFailed')}</span>
      <button onClick={resetErrorBoundary} className="text-xs underline hover:text-foreground">
        {t('pages.plannerMD.noteEditor.errorFallback.tryAgain')}
      </button>
    </div>
  )
}

/**
 * NoteEditor - WYSIWYG rich text editor using Tiptap
 *
 * Features:
 * - Focus-based preview mode (unfocused = read-only preview)
 * - Controlled component pattern (value + onChange)
 * - Text formatting: bold, italic, strikethrough, headings, lists, quotes, code
 * - Custom spoiler mark extension
 * - Image upload using Tiptap's ImageUploadNode
 * - Link insertion dialog
 */
// Custom comparison for memo - compares value.content by reference equality
// since parent should provide stable NoteContent objects
function areNoteEditorPropsEqual(prev: NoteEditorProps, next: NoteEditorProps): boolean {
  return (
    prev.value === next.value &&
    prev.placeholder === next.placeholder &&
    prev.readOnly === next.readOnly &&
    prev.className === next.className &&
    prev.maxBytes === next.maxBytes
    // onChange intentionally excluded - should be stable from parent
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

  // Editor cap aligns with the persistence gate (Zod refine / backend),
  // so an accepted edit is always saveable. Falls back to MAX_NOTE_BYTES
  // when no explicit prop is given, enforcing the limit wherever NoteEditor mounts.
  const byteLimit = maxBytes ?? MAX_NOTE_BYTES

  // Local content state for fast typing - syncs to parent after debounce
  // This prevents parent re-renders on every keystroke
  const [localContent, setLocalContent] = useState(value.content)
  const hasLocalChangesRef = useRef(false)

  // Debounced sync to parent - fires 500ms after typing stops
  useEffect(() => {
    if (!hasLocalChangesRef.current || !localContent) return

    const timer = setTimeout(() => {
      if (hasLocalChangesRef.current) {
        onChange({ content: localContent })
        hasLocalChangesRef.current = false
      }
    }, 500)

    return () => clearTimeout(timer)
  }, [localContent, onChange])

  // Measure the same { content } shape the cap and schema enforce, so the
  // counter never disagrees with what the editor actually rejects.
  const currentBytes = useMemo(() => {
    if (!localContent) return 0
    return measureDocBytes(localContent)
  }, [localContent])

  // Memoize extensions to prevent recreation on every render
  const extensions = useMemo(
    () => [
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
    ],
    [byteLimit],
  )

  // Initialize Tiptap editor
  const editor = useEditor({
    extensions,
    content: value.content,
    editable: !readOnly && isFocused,
    onUpdate: ({ editor }) => {
      // Update local state only - parent sync happens on blur
      const newContent = editor.getJSON()
      setLocalContent(newContent)
      hasLocalChangesRef.current = true
    },
    editorProps: {
      attributes: {
        class: 'note-editor-content prose prose-sm max-w-none focus:outline-none min-h-[100px] p-3',
      },
      // Disable drag-and-drop to allow normal text selection by dragging
      handleDOMEvents: {
        dragstart: () => true, // Return true to prevent default drag behavior
      },
      // In-limit pastes keep default rich behavior; an over-limit paste is
      // truncated to the largest plain-text prefix that still fits the cap.
      handlePaste: (view, event, slice) => {
        const { state } = view
        const prospective = state.tr.replaceSelection(slice)
        if (measureDocBytes(prospective.doc.toJSON()) <= byteLimit) {
          return false
        }

        const rawText =
          event.clipboardData?.getData('text/plain') ||
          slice.content.textBetween(0, slice.content.size, '\n')

        // No plain text to truncate (image-only / unsupported clipboard):
        // hand back to the default handler rather than silently swallowing —
        // ByteLimitExtension still gates whatever it inserts.
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

  // Update editable state when focus or readOnly changes
  useEffect(() => {
    if (editor) {
      editor.setEditable(!readOnly && isFocused)
    }
  }, [editor, isFocused, readOnly])

  // Sync from parent when value prop changes externally (load/import)
  // Skip if we have local changes to avoid overwriting user's typing
  useEffect(() => {
    if (editor && value.content && !hasLocalChangesRef.current) {
      const currentContent = JSON.stringify(editor.getJSON())
      const newContent = JSON.stringify(value.content)
      if (currentContent !== newContent) {
        setLocalContent(value.content)
        // Trusted external load: exempt from the byte cap so an oversized
        // legacy/server note still populates the editor instead of being
        // silently rejected by ByteLimitExtension (which would desync the
        // editor from the React state set just above).
        editor.chain().setMeta(BYTE_LIMIT_BYPASS, true).setContent(value.content).run()
      }
    }
  }, [editor, value.content])

  // Handle click to activate editor - synchronous focus for React Compiler compatibility
  const handleClick = () => {
    if (!readOnly && !isFocused) {
      setIsFocused(true)
      // Direct synchronous focus - no RAF to avoid race conditions
      if (editor && !editor.isDestroyed) {
        editor.commands.focus()
      }
    }
  }

  // Handle blur with relatedTarget for reliable focus tracking
  // Syncs local changes to parent when focus leaves the editor
  const handleBlur = (e: React.FocusEvent) => {
    const relatedTarget = e.relatedTarget as HTMLElement | null

    if (containerRef.current && !containerRef.current.contains(relatedTarget)) {
      if (!linkDialogOpen) {
        setIsFocused(false)

        // Sync local changes to parent on blur
        if (hasLocalChangesRef.current && localContent) {
          onChange({ content: localContent })
          hasLocalChangesRef.current = false
        }
      }
    }
  }

  // Link dialog handlers
  const handleLinkClick = () => {
    if (!editor) return
    const { from, to } = editor.state.selection
    const text = editor.state.doc.textBetween(from, to, ' ')
    setSelectedText(text)
    setLinkDialogOpen(true)
  }

  // XSS-safe link insertion using structured content instead of raw HTML
  const handleLinkConfirm = (url: string, text?: string) => {
    if (!editor) return

    // Add protocol if missing, then sanitize to prevent XSS
    let processedUrl = url
    if (!/^https?:\/\//i.test(processedUrl)) {
      processedUrl = `https://${processedUrl}`
    }

    const safeUrl = sanitizeUrl(processedUrl, window.location.origin)
    if (safeUrl === '#') {
      toast.error(t('pages.plannerMD.noteEditor.linkDialog.invalidUrl'))
      return
    }

    if (text && text !== selectedText) {
      // Use structured content instead of raw HTML to prevent XSS
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
        onClick={handleClick}
        onBlur={handleBlur}
      >
        {/* Toolbar - only visible when focused */}
        <Toolbar editor={editor} visible={isFocused && !readOnly} onLinkClick={handleLinkClick} />

        {/* Editor content with error boundary */}
        <ErrorBoundary
          fallbackRender={({ resetErrorBoundary }) => (
            <EditorErrorFallback resetErrorBoundary={resetErrorBoundary} t={t} />
          )}
          onError={(error) => {
            console.error('NoteEditor error:', error)
          }}
        >
          <EditorContent editor={editor} />

          {/* Show placeholder in preview mode when empty */}
          {!isFocused && editor?.isEmpty && (
            <div className="absolute top-0 left-0 p-3 text-muted-foreground pointer-events-none">
              {readOnly
                ? t('pages.plannerMD.noteEditor.placeholderReadOnly')
                : placeholder || t('pages.plannerMD.noteEditor.placeholder')}
            </div>
          )}
        </ErrorBoundary>

        {/* Link dialog */}
        <LinkDialog
          open={linkDialogOpen}
          onClose={handleLinkClose}
          onConfirm={handleLinkConfirm}
          initialText={selectedText}
        />

        {/* Byte counter - shown wherever the cap is enforced (editable) */}
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

// Wrap with memo using custom prop comparison to prevent unnecessary re-renders
export const NoteEditor = memo(NoteEditorInner, areNoteEditorPropsEqual)

export default NoteEditor
