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

import type { ImportDraft, ImportVerdict } from '../../hooks/useDeckClipboard'
import type { DecodedDeck, DeckWarning } from '../../lib/deckCode'

interface DeckImportDialogProps {
  draft: ImportDraft | null
  onTextChange: (text: string) => void
  onApply: (deck: DecodedDeck) => void
  onClose: () => void
}

function ImportStatus({ verdict }: { verdict: ImportVerdict }) {
  const { t } = useTranslation(['planner', 'sinnerNames'])

  const describeWarning = (warning: DeckWarning) => {
    const sinner = t(`sinnerNames:${warning.sinner}`)
    if (warning.kind === 'identity') {
      return t('deckBuilder.importWarningIdentity', { sinner })
    }
    return t('deckBuilder.importWarningEgo', { sinner, grade: warning.grade })
  }

  if (verdict.kind === 'empty') return null

  if (verdict.kind === 'invalid') {
    return <p className="text-sm text-destructive">{t('deckBuilder.importError')}</p>
  }

  const { warnings } = verdict.deck
  if (warnings.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">{t('deckBuilder.importConfirmDescription')}</p>
    )
  }

  return (
    <div className={WARNING_CALLOUT_STYLES.panel}>
      <p className={WARNING_CALLOUT_STYLES.heading}>{t('deckBuilder.importWarnings')}</p>
      <ul className={WARNING_CALLOUT_STYLES.list}>
        {warnings.map((warning, index) => (
          <li key={index}>{describeWarning(warning)}</li>
        ))}
      </ul>
    </div>
  )
}

export function DeckImportDialog({ draft, onTextChange, onApply, onClose }: DeckImportDialogProps) {
  const { t } = useTranslation(['planner', 'common'])
  const verdict = draft?.verdict

  return (
    <Dialog
      open={draft !== null}
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
    >
      <DialogContent showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>{t('deckBuilder.importConfirmTitle')}</DialogTitle>
          <DialogDescription>{t('deckBuilder.importDescription')}</DialogDescription>
        </DialogHeader>

        <textarea
          data-testid="deck-import-text"
          value={draft?.text ?? ''}
          onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => onTextChange(e.target.value)}
          placeholder={t('deckBuilder.importPlaceholder')}
          rows={4}
          className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm break-all ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none overflow-y-auto"
        />

        {verdict && <ImportStatus verdict={verdict} />}

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t('common:close')}
          </Button>
          <Button
            disabled={verdict?.kind !== 'valid'}
            onClick={() => {
              if (verdict?.kind === 'valid') onApply(verdict.deck)
            }}
          >
            {t('deckBuilder.apply')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
