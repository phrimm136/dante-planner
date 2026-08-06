import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { WARNING_CALLOUT_STYLES } from '@/lib/constants'

import type { DecodedDeck } from '../../lib/deckCode'

interface DeckImportConfirmDialogProps {
  /** The decoded deck awaiting confirmation; `null` keeps the dialog closed. */
  pendingImport: DecodedDeck | null
  onConfirm: () => void
  onCancel: () => void
}

/**
 * Confirmation for a deck code read off the clipboard, listing the warnings the
 * decode produced before the caller applies it.
 */
export function DeckImportConfirmDialog({
  pendingImport,
  onConfirm,
  onCancel,
}: DeckImportConfirmDialogProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <Dialog
      open={pendingImport !== null}
      onOpenChange={(open) => {
        if (!open) onCancel()
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('deckBuilder.importConfirmTitle')}</DialogTitle>
          <DialogDescription>{t('deckBuilder.importConfirmDescription')}</DialogDescription>
        </DialogHeader>

        {pendingImport && pendingImport.warnings.length > 0 && (
          <div className={WARNING_CALLOUT_STYLES.panel}>
            <p className={WARNING_CALLOUT_STYLES.heading}>{t('deckBuilder.importWarnings')}</p>
            <ul className={WARNING_CALLOUT_STYLES.list}>
              {pendingImport.warnings.map((warning, index) => (
                <li key={index}>{warning}</li>
              ))}
            </ul>
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>
            {t('deckBuilder.cancel')}
          </Button>
          <Button onClick={onConfirm}>{t('deckBuilder.apply')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
