import { useState, type ReactNode } from 'react'

import {
  ConfirmActionDialog,
  type ActionDialogBaseProps,
} from '@/components/feedback/ConfirmActionDialog'

/** Server-side cap on a moderation reason */
const REASON_MAX_LENGTH = 500

interface ModerationReasonDialogProps extends ActionDialogBaseProps {
  /** Label above the reason textarea */
  reasonLabel: ReactNode
  reasonPlaceholder: string
  /** DOM id of the textarea, tying it to its label */
  reasonInputId: string
  onConfirm: (reason: string) => void
  /** Extra controls rendered above the reason field */
  children?: ReactNode
}

/**
 * Moderation dialog with a mandatory free-text reason.
 *
 * Confirm stays disabled until the reason has non-whitespace content; the
 * reason resets on both confirm and cancel so the next open starts empty.
 */
export function ModerationReasonDialog({
  open,
  onOpenChange,
  title,
  description,
  reasonLabel,
  reasonPlaceholder,
  reasonInputId,
  cancelLabel,
  confirmLabel,
  destructive = false,
  onConfirm,
  isPending,
  children,
}: ModerationReasonDialogProps) {
  const [reason, setReason] = useState('')

  const handleConfirm = () => {
    if (!reason.trim()) return
    onConfirm(reason)
    setReason('')
  }

  const handleCancel = () => {
    onOpenChange(false)
    setReason('')
  }

  const reasonField = (
    <div className="space-y-2">
      <label htmlFor={reasonInputId} className="text-sm font-medium">
        {reasonLabel}
      </label>
      <textarea
        id={reasonInputId}
        value={reason}
        onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setReason(e.target.value)}
        placeholder={reasonPlaceholder}
        maxLength={REASON_MAX_LENGTH}
        rows={4}
        className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none overflow-y-auto"
      />
      <p className="text-xs text-muted-foreground text-right">
        {reason.length}/{REASON_MAX_LENGTH}
      </p>
    </div>
  )

  return (
    <ConfirmActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      description={description}
      cancelLabel={cancelLabel}
      confirmLabel={confirmLabel}
      destructive={destructive}
      onConfirm={handleConfirm}
      onCancel={handleCancel}
      isPending={isPending}
      confirmDisabled={!reason.trim()}
    >
      {children ? (
        <div className="space-y-4">
          {children}
          {reasonField}
        </div>
      ) : (
        reasonField
      )}
    </ConfirmActionDialog>
  )
}
