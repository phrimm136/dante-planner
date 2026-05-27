import { useTranslation } from 'react-i18next'

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

/**
 * Props for LogoutEverywhereDialog
 */
export interface LogoutEverywhereDialogProps {
  /** Whether the dialog is open */
  open: boolean
  /** Callback when user confirms logging out everywhere */
  onConfirm: () => void
  /** Callback when user cancels */
  onCancel: () => void
  /** Whether the logout request is in progress */
  isPending: boolean
}

/**
 * Dialog for confirming "log out everywhere".
 *
 * Explains that the user will be signed out of every device, including the
 * current one. Cannot be dismissed with ESC or clicking outside while pending.
 *
 * @example
 * ```tsx
 * <LogoutEverywhereDialog
 *   open={dialogOpen}
 *   onConfirm={handleLogoutEverywhere}
 *   onCancel={() => setDialogOpen(false)}
 *   isPending={logoutEverywhere.isPending}
 * />
 * ```
 */
export function LogoutEverywhereDialog({
  open,
  onConfirm,
  onCancel,
  isPending,
}: LogoutEverywhereDialogProps) {
  const { t } = useTranslation()

  const preventDismissal = (e: Event) => {
    e.preventDefault()
  }

  return (
    <Dialog open={open}>
      <DialogContent
        showCloseButton={false}
        onEscapeKeyDown={preventDismissal}
        onInteractOutside={preventDismissal}
      >
        <DialogHeader>
          <DialogTitle>{t('settings.logoutEverywhere.confirmTitle')}</DialogTitle>
          <DialogDescription>
            {t('settings.logoutEverywhere.confirmDescription')}
          </DialogDescription>
        </DialogHeader>

        <DialogFooter className="flex-col gap-2 sm:flex-row">
          <Button variant="outline" onClick={onCancel} disabled={isPending}>
            {t('settings.logoutEverywhere.cancelButton')}
          </Button>
          <Button variant="destructive" onClick={onConfirm} disabled={isPending}>
            {t('settings.logoutEverywhere.confirmButton')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
