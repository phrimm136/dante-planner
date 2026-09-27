import { useState } from 'react'

import { showError, showErrorMessage, showSuccess } from '@/lib/errorPresentation'
import { useIdentityListSpec } from '@/pages/identity'
import { useEGOListSpec } from '@/pages/ego'

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

function loadDeckCode() {
  return import('../lib/deckCode')
}

function writeDeckCode({ equipment, deploymentOrder }: DeckSnapshot): Promise<void> {
  const code = loadDeckCode().then((m) => m.encodeDeckCode(equipment, deploymentOrder))

  if (typeof ClipboardItem === 'undefined') {
    return code.then((text) => navigator.clipboard.writeText(text))
  }

  const blob = code.then((text) => new Blob([text], { type: 'text/plain' }))
  return navigator.clipboard.write([new ClipboardItem({ 'text/plain': blob })])
}

export function useDeckClipboard({ readDeck }: UseDeckClipboardOptions): DeckClipboard {
  const identitySpec = useIdentityListSpec()
  const egoSpec = useEGOListSpec()

  const [pendingImport, setPendingImport] = useState<DecodedDeck | null>(null)

  const handleExport = async () => {
    try {
      await writeDeckCode(readDeck())
      showSuccess('planner:deckBuilder.exportSuccess')
    } catch (error) {
      showError(error)
    }
  }

  const handleImport = async () => {
    try {
      const clipboardText = await navigator.clipboard.readText()
      const { decodeDeckCode, validateDeckCode } = await loadDeckCode()
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
