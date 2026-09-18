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

export interface ActionDialogBaseProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: ReactNode
  description: ReactNode
  confirmLabel: ReactNode
  cancelLabel: ReactNode
  destructive?: boolean
  isPending?: boolean
}

export type ActionDialogControl = Pick<ActionDialogBaseProps, 'open' | 'onOpenChange' | 'isPending'>

interface ConfirmActionDialogProps extends ActionDialogBaseProps {
  pendingLabel?: ReactNode
  onConfirm: () => void
  onCancel?: () => void
  confirmDisabled?: boolean
  icon?: ReactNode
  children?: ReactNode
  className?: string
  descriptionClassName?: string
  footerClassName?: string
}

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
