import { useState, useRef } from 'react'
import { useEditor, EditorContent, EditorContext, type Editor } from '@tiptap/react'
import { ErrorBoundary as ReactErrorBoundary } from 'react-error-boundary'
import { useTranslation } from 'react-i18next'
import StarterKit from '@tiptap/starter-kit'

import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { COMMENT_MAX_CHARS } from '@/lib/constants'

import './CommentEditor.css'

interface CommentEditorProps {
  placeholder?: string
  disabled?: boolean
  onSubmit: (content: string) => void
  onCancel?: () => void
  initialContent?: string
  isReply?: boolean
  isSubmitting?: boolean
}

function CommentEditorErrorFallback() {
  const { t } = useTranslation(['planner', 'common'])
  return <div className="p-3 text-sm text-muted-foreground">{t('common:loadError')}</div>
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
  // The server's limit applies to the HTML that gets submitted, not to its plain text.
  const [charCount, setCharCount] = useState(0)
  const [isEmpty, setIsEmpty] = useState(true)

  const syncCounters = (editor: Editor) => {
    setCharCount(editor.getHTML().length)
    setIsEmpty(editor.getText().trim().length === 0)
  }

  const extensions = [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
    }),
  ]

  const editor = useEditor({
    extensions,
    content: initialContent,
    editable: !disabled,
    editorProps: {
      attributes: {
        class:
          'comment-editor-content prose prose-sm max-w-none focus:outline-none min-h-[80px] p-3',
      },
    },
    onUpdate: ({ editor }) => {
      syncCounters(editor)
    },
    onCreate: ({ editor }) => {
      syncCounters(editor)
    },
  })

  const isOverLimit = charCount > COMMENT_MAX_CHARS
  const canSubmit = !isEmpty && !isOverLimit && !isSubmitting

  const handleFocus = () => {
    if (!disabled) {
      setIsFocused(true)
    }
  }

  const handleBlur = (e: React.FocusEvent) => {
    const relatedTarget = e.relatedTarget as HTMLElement | null

    if (containerRef.current && !containerRef.current.contains(relatedTarget)) {
      if (isEmpty) {
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
        disabled && 'opacity-50 cursor-not-allowed',
      )}
      onFocus={handleFocus}
      onBlur={handleBlur}
    >
      <ReactErrorBoundary FallbackComponent={CommentEditorErrorFallback}>
        <EditorContext.Provider value={{ editor }}>
          <div className="relative">
            <EditorContent editor={editor} />

            {!isFocused && editor.isEmpty && (
              <div className="absolute top-0 left-0 p-3 text-muted-foreground pointer-events-none">
                {placeholder || t('pages.plannerMD.comments.placeholder', 'Write a comment...')}
              </div>
            )}
          </div>
        </EditorContext.Provider>
      </ReactErrorBoundary>

      <div className="flex items-center justify-end gap-3 px-3 py-2 border-t border-input">
        <span className={cn('text-xs', isOverLimit ? 'text-destructive' : 'text-muted-foreground')}>
          {charCount}/{COMMENT_MAX_CHARS}
        </span>

        {isFocused && (
          <div className="flex gap-2">
            {(isReply || !isEmpty) && (
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
