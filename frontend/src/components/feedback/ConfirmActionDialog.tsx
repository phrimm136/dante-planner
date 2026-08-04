import type { ReactNode } from 'react'

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { SECTION_STYLES } from '@/lib/constants'

interface ConfirmActionDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: ReactNode
  description: ReactNode
  confirmLabel: ReactNode
  cancelLabel: ReactNode
  /** Confirm-button label while `isPending` is true; falls back to `confirmLabel` */
  pendingLabel?: ReactNode
  /** Renders the confirm button in the destructive variant */
  destructive?: boolean
  onConfirm: () => void
  /** Cancel-button handler; defaults to closing the dialog */
  onCancel?: () => void
  isPending?: boolean
  /** Disables the confirm button without disabling cancel */
  confirmDisabled?: boolean
  /** Rendered beside the title inside a flex row */
  icon?: ReactNode
  /** Extra content between the header and the footer */
  children?: ReactNode
  className?: string
  descriptionClassName?: string
  footerClassName?: string
}

/**
 * Confirmation dialog: title + description, cancel/confirm footer.
 *
 * @example
 * <ConfirmActionDialog
 *   open={open}
 *   onOpenChange={setOpen}
 *   title={t('deleteConfirm.title')}
 *   description={t('deleteConfirm.description')}
 *   cancelLabel={t('common:cancel')}
 *   confirmLabel={t('deleteConfirm.delete')}
 *   pendingLabel={t('deleteConfirm.deleting')}
 *   destructive
 *   onConfirm={handleDelete}
 *   isPending={isDeleting}
 * />
 */
export function ConfirmActionDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  cancelLabel,
  pendingLabel,
  destructive = false,
  onConfirm,
  onCancel,
  isPending = false,
  confirmDisabled = false,
  icon,
  children,
  className,
  descriptionClassName,
  footerClassName,
}: ConfirmActionDialogProps) {
  const handleCancel = () => {
    if (onCancel) {
      onCancel()
      return
    }
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className={className}>
        <DialogHeader>
          {icon ? (
            <div className={SECTION_STYLES.LAYOUT.row}>
              {icon}
              <DialogTitle>{title}</DialogTitle>
            </div>
          ) : (
            <DialogTitle>{title}</DialogTitle>
          )}
          <DialogDescription className={descriptionClassName}>{description}</DialogDescription>
        </DialogHeader>

        {children}

        <DialogFooter className={footerClassName}>
          <Button variant="outline" onClick={handleCancel} disabled={isPending}>
            {cancelLabel}
          </Button>
          <Button
            variant={destructive ? 'destructive' : 'default'}
            onClick={onConfirm}
            disabled={isPending || confirmDisabled}
          >
            {isPending && pendingLabel !== undefined ? pendingLabel : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
