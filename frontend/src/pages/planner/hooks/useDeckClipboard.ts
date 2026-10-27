import { useRef, useState } from 'react'

import { showError, showSuccess } from '@/lib/errorPresentation'
import { useIdentityListSpec } from '@/pages/identity'
import { useEGOListSpec } from '@/pages/ego'

import { loadIdentityMaxLevel } from './loadIdentityLevelCaps'
import type { DecodedDeck } from '../lib/deckCode'
import type { SinnerEquipment } from '../types/DeckTypes'

export interface DeckSnapshot {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
}

export interface UseDeckClipboardOptions {
  readDeck: () => DeckSnapshot
  mdVersion: number
}

export type ImportVerdict =
  | { kind: 'empty' }
  | { kind: 'invalid' }
  | { kind: 'valid'; deck: DecodedDeck }

export interface ImportDraft {
  text: string
  verdict: ImportVerdict
}

export interface DeckClipboard {
  handleExport: () => Promise<void>
  openImport: () => Promise<void>
  importDraft: ImportDraft | null
  setImportText: (text: string) => void
  closeImport: () => void
}

interface DecodeContext {
  deckCode: Awaited<ReturnType<typeof loadDeckCode>>
  maxLevel: number
  identitySpec: Record<string, unknown>
  egoSpec: Record<string, { maxThreadspin: 4 | 5 }>
}

function loadDeckCode() {
  return import('../lib/deckCode')
}

function readClipboardText(): Promise<string> {
  const clipboard = navigator.clipboard as Clipboard | undefined
  if (typeof clipboard?.readText !== 'function') return Promise.resolve('')
  return clipboard.readText().catch(() => '')
}

function judgeDeckCode(text: string, context: DecodeContext): ImportVerdict {
  const code = text.trim()
  if (code === '') return { kind: 'empty' }
  if (!context.deckCode.validateDeckCode(code).isValid) return { kind: 'invalid' }
  try {
    const deck = context.deckCode.decodeDeckCode(
      code,
      context.identitySpec,
      context.egoSpec,
      context.maxLevel,
    )
    return { kind: 'valid', deck }
  } catch {
    return { kind: 'invalid' }
  }
}

function writeDeckCode({ equipment, deploymentOrder }: DeckSnapshot): Promise<void> {
  const code = loadDeckCode().then((m) => m.encodeDeckCode(equipment, deploymentOrder))

  if (typeof ClipboardItem === 'undefined') {
    return code.then((text) => navigator.clipboard.writeText(text))
  }

  const blob = code.then((text) => new Blob([text], { type: 'text/plain' }))
  return navigator.clipboard.write([new ClipboardItem({ 'text/plain': blob })])
}

export function useDeckClipboard({ readDeck, mdVersion }: UseDeckClipboardOptions): DeckClipboard {
  const identitySpec = useIdentityListSpec()
  const egoSpec = useEGOListSpec()

  const [importDraft, setImportDraft] = useState<ImportDraft | null>(null)
  const deckCodeRef = useRef<DecodeContext['deckCode'] | null>(null)
  const maxLevelRef = useRef<number | null>(null)
  const openRequestRef = useRef(0)

  const handleExport = async () => {
    try {
      await writeDeckCode(readDeck())
      showSuccess('planner:deckBuilder.exportSuccess')
    } catch (error) {
      showError(error)
    }
  }

  const loadDecodeContext = async (): Promise<DecodeContext> => {
    const deckCode = deckCodeRef.current ?? (await loadDeckCode())
    const maxLevel = maxLevelRef.current ?? (await loadIdentityMaxLevel(mdVersion))
    deckCodeRef.current = deckCode
    maxLevelRef.current = maxLevel
    return { deckCode, maxLevel, identitySpec, egoSpec }
  }

  const openImport = () => {
    const request = openRequestRef.current + 1
    openRequestRef.current = request
    return Promise.all([readClipboardText(), loadDecodeContext()]).then(
      ([text, context]) => {
        if (openRequestRef.current !== request) return
        setImportDraft({ text, verdict: judgeDeckCode(text, context) })
      },
      (error: unknown) => {
        showError(error)
      },
    )
  }

  const setImportText = (text: string) => {
    const deckCode = deckCodeRef.current
    const maxLevel = maxLevelRef.current
    if (deckCode === null || maxLevel === null) return
    const verdict = judgeDeckCode(text, { deckCode, maxLevel, identitySpec, egoSpec })
    setImportDraft({ text, verdict })
  }

  const closeImport = () => {
    openRequestRef.current += 1
    setImportDraft(null)
  }

  return { handleExport, openImport, importDraft, setImportText, closeImport }
}
