import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'

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
import { SECTION_STYLES } from '@/lib/constants'

export interface AccountDeleteDialogProps {
  open: boolean
  onConfirm: () => void
  onCancel: () => void
  isPending: boolean
}

export function AccountDeleteDialog({
  open,
  onConfirm,
  onCancel,
  isPending,
}: AccountDeleteDialogProps) {
  const { t } = useTranslation()
  const [confirmationInput, setConfirmationInput] = useState('')

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
          <DialogTitle>{t('settings.deleteAccount.title')}</DialogTitle>
          <DialogDescription>{t('settings.deleteAccount.confirmMessage')}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          <div className="space-y-2">
            <p className="text-sm font-medium">
              <Trans
                i18nKey="settings.deleteAccount.typeToConfirm"
                components={{ 1: <span className="font-bold text-destructive" /> }}
              />
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
          <p className={SECTION_STYLES.TEXT.captionSmall}>
            {t('settings.deleteAccount.cannotUndo')}
          </p>
        </div>

        <DialogFooter className="flex-col gap-2 sm:flex-row">
          <Button variant="outline" onClick={onCancel} disabled={isPending}>
            {t('cancel')}
          </Button>
          <Button
            variant="destructive"
            onClick={onConfirm}
            disabled={!isDeleteEnabled || isPending}
          >
            {isPending ? t('deleting') : t('settings.deleteAccount.title')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
