import { useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

/**
 * Props for AccountDeleteDialog
 */
export interface AccountDeleteDialogProps {
  /** Whether the dialog is open */
  open: boolean
  /** Callback when user confirms deletion */
  onConfirm: () => void
  /** Callback when user cancels */
  onCancel: () => void
  /** Whether deletion is in progress */
  isPending: boolean
}

/**
 * Dialog for confirming account deletion
 *
 * Requires user to type "DELETE" exactly to enable the delete button.
 * Cannot be dismissed with ESC key or clicking outside.
 * Shows warning about consequences and grace period information.
 *
 * @example
 * \`\`\`tsx
 * <AccountDeleteDialog
 *   open={dialogOpen}
 *   onConfirm={handleDelete}
 *   onCancel={() => setDialogOpen(false)}
 *   isPending={deleteAccount.isPending}
 * />
 * \`\`\`
 */
export function AccountDeleteDialog({
  open,
  onConfirm,
  onCancel,
  isPending,
}: AccountDeleteDialogProps) {
  const [confirmationInput, setConfirmationInput] = useState('')

  // Prevent dismissal via ESC key or clicking outside
  const preventDismissal = (e: Event) => {
    e.preventDefault()
  }

  const isDeleteEnabled = confirmationInput === 'DELETE'

  return (
    <Dialog open={open}>
      <DialogContent
        showCloseButton={false}
        onEscapeKeyDown={preventDismissal}
        onInteractOutside={preventDismissal}
      >
        <DialogHeader>
          <DialogTitle>Delete Account</DialogTitle>
          <DialogDescription>
            This action will permanently delete your account, all saved planners, and all
            comments after a 30-day grace period. You can cancel this deletion by logging
            back in within 30 days.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          <div className="space-y-2">
            <p className="text-sm font-medium">
              Type <span className="font-bold text-destructive">DELETE</span> to confirm
            </p>
            <Input
              type="text"
              value={confirmationInput}
              onChange={(e) => setConfirmationInput(e.target.value)}
              placeholder="DELETE"
              disabled={isPending}
              autoComplete="off"
            />
          </div>
          <p className="text-xs text-muted-foreground">
            This action cannot be undone after the grace period expires.
          </p>
        </div>

        <DialogFooter className="flex-col gap-2 sm:flex-row">
          <Button
            variant="outline"
            onClick={onCancel}
            disabled={isPending}
          >
            Cancel
          </Button>
          <Button
            variant="destructive"
            onClick={onConfirm}
            disabled={!isDeleteEnabled || isPending}
          >
            {isPending ? 'Deleting...' : 'Delete Account'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
