import { useState, useEffect, useRef } from 'react'
import { useEditor, EditorContent, EditorContext, type JSONContent } from '@tiptap/react'
import { ErrorBoundary, type FallbackProps } from 'react-error-boundary'
import { useTranslation } from 'react-i18next'
import { showErrorMessage } from '@/lib/errorPresentation'
import StarterKit from '@tiptap/starter-kit'

import { cn } from '@/lib/utils'
import { measureDocBytes, largestPrefixWithinLimit } from '../lib/noteUtils'
import { sanitizeUrl } from '../lib/tiptap-utils'
import { AUTO_SAVE_DEBOUNCE_MS, MAX_NOTE_BYTES } from '@/lib/constants'
import { useNoteDelivery } from '../context/NoteDeliveryRegistry'
import type { NoteEditorProps } from '../types/NoteEditorTypes'
import { SpoilerExtension } from './extensions/SpoilerExtension'
import { ByteLimitExtension, BYTE_LIMIT_BYPASS } from './extensions/ByteLimitExtension'
import { Toolbar } from './Toolbar'
import { LinkDialog } from './LinkDialog'

import './NoteEditor.css'

/**
 * EditorErrorFallback - Simple inline fallback when editor crashes
 */
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

  // What the armed debounce would have delivered, callable outside its timer.
  const pendingRef = useRef<(() => void) | null>(null)

  // Tiptap's parsed form of `value.content`, awaiting its one-time delivery.
  const normalizedRef = useRef<JSONContent | null>(null)

  // Debounced sync to parent - fires AUTO_SAVE_DEBOUNCE_MS after typing stops
  useEffect(() => {
    pendingRef.current = null
    if (!hasLocalChangesRef.current || !localContent) return

    const flush = () => {
      if (hasLocalChangesRef.current) {
        onChange?.({ content: localContent })
        hasLocalChangesRef.current = false
      }
    }
    pendingRef.current = flush

    const timer = setTimeout(() => {
      pendingRef.current = null
      flush()
    }, AUTO_SAVE_DEBOUNCE_MS)

    return () => clearTimeout(timer)
  }, [localContent, onChange])

  // The debounce effect re-runs on every keystroke and clears its timer each time,
  // so unmount would otherwise drop the last edit. Flush it from an unmount-only
  // effect, which runs after that cleanup.
  useEffect(() => () => pendingRef.current?.(), [])

  // An owner collecting pending text before the page goes away pulls it from
  // here. Listening for the unload directly would put this editor's delivery at
  // the mercy of listener registration order against the owner's own handler.
  const delivery = useNoteDelivery()
  useEffect(() => {
    if (!delivery) return
    return delivery.register(() => {
      pendingRef.current?.()
    })
  }, [delivery])

  // Measure the same { content } shape the cap and schema enforce, so the
  // counter never disagrees with what the editor actually rejects.
  const currentBytes = (() => {
    if (!localContent) return 0
    return measureDocBytes(localContent)
  })()

  // Memoize extensions to prevent recreation on every render
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

  // Initialize Tiptap editor
  const editor = useEditor({
    extensions,
    content: value.content,
    editable: !readOnly && isFocused,
    onCreate: ({ editor }) => {
      normalizedRef.current = editor.getJSON()
    },
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

  // Hand Tiptap's parsed document over at mount instead of letting it travel as a
  // debounced edit. Child effects run before the owner's, so the owner's baseline
  // already holds the normalized form: opening a planner is not a change, arms no
  // autosave, and never rewrites a saved row as a draft.
  useEffect(() => {
    const normalized = normalizedRef.current
    if (!normalized) return

    normalizedRef.current = null
    onChange?.({ content: normalized })
  }, [onChange])

  // Whether a pointer is currently pressed, and the wait for its release if one is
  // already scheduled. Read by collapseToolbar, which must not reflow mid-gesture.
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

  // Handle blur with relatedTarget for reliable focus tracking
  // Syncs local changes to parent when focus leaves the editor
  const handleBlur = (e: React.FocusEvent) => {
    const relatedTarget = e.relatedTarget as HTMLElement | null

    if (containerRef.current && !containerRef.current.contains(relatedTarget)) {
      if (!linkDialogOpen) {
        collapseToolbar()

        // Sync local changes to parent on blur
        if (hasLocalChangesRef.current && localContent) {
          onChange?.({ content: localContent })
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
      showErrorMessage('planner:pages.plannerMD.noteEditor.linkDialog.invalidUrl')
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
          FallbackComponent={EditorErrorFallback}
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
export const NoteEditor = NoteEditorInner

export default NoteEditor
