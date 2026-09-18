import { useState } from 'react'

import { showError, showErrorMessage, showSuccess } from '@/lib/errorPresentation'
import { useIdentityListSpec } from '@/pages/identity'
import { useEGOListSpec } from '@/pages/ego'

import { encodeDeckCode, decodeDeckCode, validateDeckCode } from '../lib/deckCode'
import type { DecodedDeck } from '../lib/deckCode'
import type { SinnerEquipment } from '../types/DeckTypes'

export interface DeckSnapshot {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
}

export interface UseDeckClipboardOptions {
  readDeck: () => DeckSnapshot
}

export interface DeckClipboard {
  handleImport: () => Promise<void>
  handleExport: () => Promise<void>
  pendingImport: DecodedDeck | null
  clearPending: () => void
}

export function useDeckClipboard({ readDeck }: UseDeckClipboardOptions): DeckClipboard {
  const identitySpec = useIdentityListSpec()
  const egoSpec = useEGOListSpec()

  const [pendingImport, setPendingImport] = useState<DecodedDeck | null>(null)

  const handleExport = async () => {
    try {
      const { equipment, deploymentOrder } = readDeck()
      const code = encodeDeckCode(equipment, deploymentOrder)
      await navigator.clipboard.writeText(code)
      showSuccess('planner:deckBuilder.exportSuccess')
    } catch (error) {
      showError(error)
    }
  }

  const handleImport = async () => {
    try {
      const clipboardText = await navigator.clipboard.readText()
      const validation = validateDeckCode(clipboardText)

      if (!validation.isValid) {
        showErrorMessage('planner:deckBuilder.importError')
        return
      }

      setPendingImport(decodeDeckCode(clipboardText, identitySpec, egoSpec))
    } catch (error) {
      showError(error)
    }
  }

  const clearPending = () => {
    setPendingImport(null)
  }

  return { handleImport, handleExport, pendingImport, clearPending }
}
