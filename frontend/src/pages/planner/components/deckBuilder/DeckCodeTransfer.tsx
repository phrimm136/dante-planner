import type { ReactNode } from 'react'

import { showSuccess } from '@/lib/errorPresentation'

import { useDeckClipboard } from '../../hooks/useDeckClipboard'
import { useStoreSeason } from '../../stores/usePlannerStore'
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
  children: (actions: DeckCodeActions) => ReactNode
}

export function DeckCodeTransfer({ readDeck, writeDeck, children }: DeckCodeTransferProps) {
  const { identityMaxLevel } = useStoreSeason()
  const { handleExport, openImport, importDraft, setImportText, closeImport } = useDeckClipboard({
    readDeck,
    maxLevel: identityMaxLevel,
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
