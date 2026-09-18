import { useState, type ReactNode } from 'react'

import {
  ConfirmActionDialog,
  type ActionDialogBaseProps,
} from '@/components/feedback/ConfirmActionDialog'

const REASON_MAX_LENGTH = 500

interface ModerationReasonDialogProps extends ActionDialogBaseProps {
  reasonLabel: ReactNode
  reasonPlaceholder: string
  reasonInputId: string
  onConfirm: (reason: string) => void
  children?: ReactNode
}

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
      {...(isPending !== undefined && { isPending })}
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
