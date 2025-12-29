import { useState, useEffect, useRef } from 'react'
import { useEditor, EditorContent, EditorContext } from '@tiptap/react'
import { ErrorBoundary } from 'react-error-boundary'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Image from '@tiptap/extension-image'

import { cn } from '@/lib/utils'
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
export function NoteEditor({
  value,
  onChange,
  placeholder,
  disabled = false,
  className,
}: NoteEditorProps) {
  const { t } = useTranslation()
  const containerRef = useRef<HTMLDivElement>(null)

  const [isFocused, setIsFocused] = useState(false)
  const [linkDialogOpen, setLinkDialogOpen] = useState(false)
  const [selectedText, setSelectedText] = useState('')

  // Initialize Tiptap editor
  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: {
          levels: [1, 2, 3],
        },
      }),
      Link.configure({
        openOnClick: false,
        HTMLAttributes: {
          class: 'note-link',
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
    content: value.content,
    editable: !disabled && isFocused,
    onUpdate: ({ editor }) => {
      onChange({
        content: editor.getJSON(),
      })
    },
    editorProps: {
      attributes: {
        class: 'note-editor-content prose prose-sm max-w-none focus:outline-none min-h-[100px] p-3',
        ...(placeholder ? { 'data-placeholder': placeholder } : {}),
      },
      // Disable drag-and-drop to allow normal text selection by dragging
      handleDOMEvents: {
        dragstart: () => true, // Return true to prevent default drag behavior
      },
    },
  })

  // Update editable state when focus or disabled changes
  useEffect(() => {
    if (editor) {
      editor.setEditable(!disabled && isFocused)
    }
  }, [editor, isFocused, disabled])

  // Sync content when value prop changes (controlled component)
  useEffect(() => {
    if (editor && value.content) {
      const currentContent = JSON.stringify(editor.getJSON())
      const newContent = JSON.stringify(value.content)
      if (currentContent !== newContent) {
        editor.commands.setContent(value.content)
      }
    }
  }, [editor, value.content])

  // Handle click to activate editor - synchronous focus for React Compiler compatibility
  const handleClick = () => {
    if (!disabled && !isFocused) {
      setIsFocused(true)
      // Direct synchronous focus - no RAF to avoid race conditions
      if (editor && !editor.isDestroyed) {
        editor.commands.focus()
      }
    }
  }

  // Handle blur with relatedTarget for reliable focus tracking
  const handleBlur = (e: React.FocusEvent) => {
    const relatedTarget = e.relatedTarget as HTMLElement | null

    if (containerRef.current && !containerRef.current.contains(relatedTarget)) {
      if (!linkDialogOpen) {
        setIsFocused(false)
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
          'note-editor rounded-md border border-input bg-background',
          isFocused && 'ring-2 ring-ring ring-offset-2',
          disabled && 'opacity-50 cursor-not-allowed',
          className
        )}
        onClick={handleClick}
        onBlur={handleBlur}
      >
        {/* Toolbar - only visible when focused */}
        <Toolbar
          editor={editor}
          visible={isFocused && !disabled}
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
        </ErrorBoundary>

        {/* Link dialog */}
        <LinkDialog
          open={linkDialogOpen}
          onClose={handleLinkClose}
          onConfirm={handleLinkConfirm}
          initialText={selectedText}
        />
      </div>
    </EditorContext.Provider>
  )
}

export default NoteEditor
