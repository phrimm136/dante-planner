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

export interface LogoutEverywhereDialogProps {
  open: boolean
  onConfirm: () => void
  onCancel: () => void
  isPending: boolean
}

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
          <DialogDescription>{t('settings.logoutEverywhere.confirmDescription')}</DialogDescription>
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
