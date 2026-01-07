import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { LinkDialogProps } from '@/types/NoteEditorTypes'

/**
 * LinkDialog - Dialog for inserting/editing links in the editor
 *
 * Features:
 * - URL input with basic validation
 * - Optional display text (pre-populated from selected text)
 * - Confirm/Cancel actions
 */
export function LinkDialog({
  open,
  onClose,
  onConfirm,
  initialText = '',
}: LinkDialogProps) {
  const { t } = useTranslation(['planner', 'common'])
  const [url, setUrl] = useState('')
  const [displayText, setDisplayText] = useState('')

  // Reset form when dialog opens
  useEffect(() => {
    if (open) {
      setUrl('')
      setDisplayText(initialText)
    }
  }, [open, initialText])

  const handleConfirm = () => {
    if (!url.trim()) return

    // Pass raw URL to parent - sanitization and protocol handling done in NoteEditor
    onConfirm(url.trim(), displayText.trim() || undefined)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && url.trim()) {
      e.preventDefault()
      handleConfirm()
    }
  }

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>
            {t('pages.plannerMD.noteEditor.linkDialog.title')}
          </DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-4 py-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="link-url">
              {t('pages.plannerMD.noteEditor.linkDialog.url')}
            </Label>
            <Input
              id="link-url"
              type="url"
              value={url}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => { setUrl(e.target.value); }}
              onKeyDown={handleKeyDown}
              placeholder="https://example.com"
              autoFocus
            />
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="link-text">
              {t('pages.plannerMD.noteEditor.linkDialog.displayText')}
            </Label>
            <Input
              id="link-text"
              type="text"
              value={displayText}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => { setDisplayText(e.target.value); }}
              onKeyDown={handleKeyDown}
              placeholder={t('pages.plannerMD.noteEditor.linkDialog.displayTextPlaceholder')}
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleConfirm} disabled={!url.trim()}>
            {t('common.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export default LinkDialog
