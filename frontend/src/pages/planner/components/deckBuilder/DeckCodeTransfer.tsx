import type { ReactNode } from 'react'

import { showSuccess } from '@/lib/errorPresentation'

import { useDeckClipboard } from '../../hooks/useDeckClipboard'
import type { DeckSnapshot } from '../../hooks/useDeckClipboard'
import type { DecodedDeck } from '../../lib/deckCode'
import { DeckImportDialog } from './DeckImportDialog'

export interface DeckCodeActions {
  onImport: () => void
  onExport: () => void
}

interface DeckCodeTransferProps {
  readDeck: () => DeckSnapshot
  writeDeck: (deck: DeckSnapshot) => void
  mdVersion: number
  children: (actions: DeckCodeActions) => ReactNode
}

export function DeckCodeTransfer({
  readDeck,
  writeDeck,
  mdVersion,
  children,
}: DeckCodeTransferProps) {
  const { handleExport, openImport, importDraft, setImportText, closeImport } = useDeckClipboard({
    readDeck,
    mdVersion,
  })

  const onImport = () => {
    void openImport()
  }

  const onExport = () => {
    void handleExport()
  }

  const onApply = (deck: DecodedDeck) => {
    writeDeck({ equipment: deck.equipment, deploymentOrder: deck.deploymentOrder })
    closeImport()
    showSuccess('planner:deckBuilder.importSuccess')
  }

  return (
    <>
      {children({ onImport, onExport })}
      <DeckImportDialog
        draft={importDraft}
        onTextChange={setImportText}
        onApply={onApply}
        onClose={closeImport}
      />
    </>
  )
}
