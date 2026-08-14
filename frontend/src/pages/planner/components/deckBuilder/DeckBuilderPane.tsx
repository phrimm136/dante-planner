import { type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { SelectorPaneShell } from '../SelectorPaneShell'

interface DeckBuilderPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  children: ReactNode
}

/**
 * Dialog chrome for the deck builder. Knows nothing about the deck it hosts;
 * the caller supplies whichever builder its surface owns.
 */
export function DeckBuilderPane({ open, onOpenChange, children }: DeckBuilderPaneProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <SelectorPaneShell open={open} onOpenChange={onOpenChange} title={t('deckBuilder.paneTitle')}>
      {children}
    </SelectorPaneShell>
  )
}
