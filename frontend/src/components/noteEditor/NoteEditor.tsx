import { useState, useEffect, useRef, useMemo, memo } from 'react'
import { useEditor, EditorContent, EditorContext } from '@tiptap/react'
import { ErrorBoundary } from 'react-error-boundary'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'

import { cn, calculateByteLength } from '@/lib/utils'
import { handleImageUpload, sanitizeUrl } from '@/lib/tiptap-utils'
import type { NoteEditorProps } from '@/types/NoteEditorTypes'
import { ImageUploadNode } from '@/components/tiptap-node/image-upload-node'
import { SpoilerExtension } from './extensions/SpoilerExtension'
import { Toolbar } from './Toolbar'
import { LinkDialog } from './LinkDialog'

import './NoteEditor.css'
import '@/components/tiptap-node/image-upload-node/image-upload-node.scss'

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
      <button
        onClick={resetErrorBoundary}
        className="text-xs underline hover:text-foreground"
      >
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
function areNoteEditorPropsEqual(
  prev: NoteEditorProps,
  next: NoteEditorProps
): boolean {
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

  // Memoize byte calculation to avoid recalculating on every render
  const currentBytes = useMemo(() => {
    if (!maxBytes || !localContent) return 0
    const serializedContent = JSON.stringify(localContent)
    return calculateByteLength(serializedContent)
  }, [localContent, maxBytes])

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
      Image.configure({
        HTMLAttributes: {
          class: 'note-image',
        },
      }),
      ImageUploadNode.configure({
        accept: 'image/*',
        maxSize: 5 * 1024 * 1024, // 5MB
        limit: 1,
        upload: handleImageUpload,
        onError: (error) => {
          console.error('Image upload error:', error)
        },
      }),
      SpoilerExtension,
    ],
    []
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
        editor.commands.setContent(value.content)
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

  // Image upload handler - uses Tiptap's ImageUploadNode
  const handleImageClick = () => {
    if (!editor) return
    editor.chain().focus().insertContent({ type: 'imageUpload' }).run()
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
          className
        )}
        onClick={handleClick}
        onBlur={handleBlur}
      >
        {/* Toolbar - only visible when focused */}
        <Toolbar
          editor={editor}
          visible={isFocused && !readOnly}
          onLinkClick={handleLinkClick}
          onImageClick={handleImageClick}
        />

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
                : (placeholder || t('pages.plannerMD.noteEditor.placeholder'))}
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

        {/* Byte counter - displays when maxBytes is provided and not readOnly */}
        {maxBytes && !readOnly && (
          <div className="px-3 pb-2 text-right">
            <span
              className={cn(
                'text-xs',
                currentBytes > maxBytes ? 'text-destructive' : 'text-muted-foreground'
              )}
            >
              {currentBytes}/{maxBytes} {t('pages.plannerMD.bytes')}
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
