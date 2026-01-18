/**
 * CommentEditor
 *
 * Simplified WYSIWYG editor for comments based on NoteEditor pattern.
 * Differences from NoteEditor:
 * - No images or spoilers - just StarterKit (bold, italic, lists, etc.)
 * - Submit button pattern instead of auto-save
 * - Character counter (always visible)
 * - Cancel/Submit buttons when focused
 */

import { useState, useRef, useMemo } from 'react'
import { useEditor, EditorContent, EditorContext } from '@tiptap/react'
import { ErrorBoundary } from 'react-error-boundary'
import { useTranslation } from 'react-i18next'
import StarterKit from '@tiptap/starter-kit'

import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { COMMENT_MAX_CHARS } from '@/lib/constants'

import './CommentEditor.css'

interface CommentEditorProps {
  /** Placeholder text */
  placeholder?: string
  /** Whether the editor is disabled */
  disabled?: boolean
  /** Called when user submits */
  onSubmit: (content: string) => void
  /** Called when user cancels */
  onCancel?: () => void
  /** Initial HTML content (for editing existing comments) */
  initialContent?: string
  /** Whether this is a reply (always shows cancel button) */
  isReply?: boolean
  /** Loading state for submit button */
  isSubmitting?: boolean
}

export function CommentEditor({
  placeholder,
  disabled = false,
  onSubmit,
  onCancel,
  initialContent = '',
  isReply = false,
  isSubmitting = false,
}: CommentEditorProps) {
  const { t } = useTranslation(['planner', 'common'])
  const containerRef = useRef<HTMLDivElement>(null)
  const [isFocused, setIsFocused] = useState(false)
  const [charCount, setCharCount] = useState(0)

  // Extensions - StarterKit only (no images, no spoilers)
  const extensions = useMemo(
    () => [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] },
      }),
    ],
    []
  )

  const editor = useEditor({
    extensions,
    content: initialContent,
    editable: !disabled,
    editorProps: {
      attributes: {
        class: 'comment-editor-content prose prose-sm max-w-none focus:outline-none min-h-[80px] p-3',
      },
    },
    onUpdate: ({ editor }) => {
      setCharCount(editor.getText().length)
    },
    onCreate: ({ editor }) => {
      setCharCount(editor.getText().length)
    },
  })

  const isOverLimit = charCount > COMMENT_MAX_CHARS
  const canSubmit = charCount > 0 && !isOverLimit && !isSubmitting

  const handleFocus = () => {
    if (!disabled) {
      setIsFocused(true)
    }
  }

  const handleBlur = (e: React.FocusEvent) => {
    const relatedTarget = e.relatedTarget as HTMLElement | null

    // Keep focused if clicking within container (e.g., buttons)
    if (containerRef.current && !containerRef.current.contains(relatedTarget)) {
      // Empty content: collapse buttons (both main and reply editors)
      if (charCount === 0) {
        setIsFocused(false)
      }
    }
  }

  const handleSubmit = () => {
    if (!editor || !canSubmit) return

    const html = editor.getHTML()
    onSubmit(html)
    editor.commands.clearContent()

    if (!isReply) {
      setIsFocused(false)
    }
  }

  const handleCancel = () => {
    if (!editor) return

    editor.commands.clearContent()
    setIsFocused(false)
    onCancel?.()
  }

  if (!editor) return null

  return (
    <div
      ref={containerRef}
      className={cn(
        'comment-editor rounded-md border border-input bg-background',
        isFocused && 'ring-2 ring-ring ring-offset-2',
        disabled && 'opacity-50 cursor-not-allowed'
      )}
      onClick={handleFocus}
      onBlur={handleBlur}
    >
      <ErrorBoundary
        fallbackRender={() => (
          <div className="p-3 text-sm text-muted-foreground">
            {t('common:error.editorFailed', 'Editor failed to load')}
          </div>
        )}
      >
        <EditorContext.Provider value={{ editor }}>
          <div className="relative">
            <EditorContent editor={editor} />

            {/* Placeholder when empty and not focused */}
            {!isFocused && editor.isEmpty && (
              <div className="absolute top-0 left-0 p-3 text-muted-foreground pointer-events-none">
                {placeholder || t('pages.plannerMD.comments.placeholder', 'Write a comment...')}
              </div>
            )}
          </div>
        </EditorContext.Provider>
      </ErrorBoundary>

      {/* Footer: character count + buttons */}
      <div className="flex items-center justify-end gap-3 px-3 py-2 border-t border-input">
        <span
          className={cn(
            'text-xs',
            isOverLimit ? 'text-destructive' : 'text-muted-foreground'
          )}
        >
          {charCount}/{COMMENT_MAX_CHARS}
        </span>

        {isFocused && (
          <div className="flex gap-2">
            {(isReply || charCount > 0) && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleCancel}
                onMouseDown={(e) => e.preventDefault()}
                disabled={isSubmitting}
              >
                {t('common:cancel', 'Cancel')}
              </Button>
            )}
            <Button
              type="button"
              size="sm"
              onClick={handleSubmit}
              onMouseDown={(e) => e.preventDefault()}
              disabled={!canSubmit}
            >
              {isSubmitting
                ? t('common:submitting', 'Submitting...')
                : t('common:submit', 'Submit')}
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}
