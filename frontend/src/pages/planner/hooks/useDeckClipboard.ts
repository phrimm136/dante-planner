import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { toast } from '@/lib/toast'
import { useIdentityListSpec } from '@/pages/identity'
import { useEGOListSpec } from '@/pages/ego'

import { encodeDeckCode, decodeDeckCode, validateDeckCode } from '../lib/deckCode'
import type { DecodedDeck } from '../lib/deckCode'
import type { SinnerEquipment } from '../types/DeckTypes'

/** The deck an export writes out, read at the moment the export runs. */
export interface DeckSnapshot {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
}

export interface UseDeckClipboardOptions {
  /** Reads the deck to encode. Called per export, never during render. */
  readDeck: () => DeckSnapshot
}

export interface DeckClipboard {
  /** Decodes the clipboard into `pendingImport`, or toasts why it could not. */
  handleImport: () => Promise<void>
  /** Writes the deck code to the clipboard. */
  handleExport: () => Promise<void>
  /** The decoded deck awaiting the reader's confirmation; `null` while none is. */
  pendingImport: DecodedDeck | null
  clearPending: () => void
}

/**
 * Clipboard import/export of a deck code, plus the decoded deck a caller must
 * confirm before applying. `pendingImport` is the whole dialog state: it is
 * non-null exactly while the confirmation is open.
 */
export function useDeckClipboard({ readDeck }: UseDeckClipboardOptions): DeckClipboard {
  const { t } = useTranslation(['planner', 'common'])
  const identitySpec = useIdentityListSpec()
  const egoSpec = useEGOListSpec()

  const [pendingImport, setPendingImport] = useState<DecodedDeck | null>(null)

  const handleExport = async () => {
    try {
      const { equipment, deploymentOrder } = readDeck()
      const code = encodeDeckCode(equipment, deploymentOrder)
      await navigator.clipboard.writeText(code)
      toast.success(t('deckBuilder.exportSuccess'))
    } catch {
      toast.error(t('deckBuilder.exportError'))
    }
  }

  const handleImport = async () => {
    try {
      const clipboardText = await navigator.clipboard.readText()
      const validation = validateDeckCode(clipboardText)

      if (!validation.isValid) {
        toast.error(t('deckBuilder.importError'))
        return
      }

      setPendingImport(decodeDeckCode(clipboardText, identitySpec, egoSpec))
    } catch {
      toast.error(t('deckBuilder.importError'))
    }
  }

  const clearPending = () => {
    setPendingImport(null)
  }

  return { handleImport, handleExport, pendingImport, clearPending }
}
