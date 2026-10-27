import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

import i18n from '@/lib/i18n'

import { DeckCodeTransfer } from '../DeckCodeTransfer'
import type { DeckCodeActions } from '../DeckCodeTransfer'
import type { DeckClipboard, DeckSnapshot, ImportDraft } from '../../../hooks/useDeckClipboard'
import type { DecodedDeck } from '../../../lib/deckCode'

const clipboard = vi.hoisted(() => ({
  handleExport: vi.fn<() => Promise<void>>(),
  openImport: vi.fn<() => Promise<void>>(),
  setImportText: vi.fn<(text: string) => void>(),
  closeImport: vi.fn<() => void>(),
  importDraft: null as ImportDraft | null,
}))

const { showSuccess } = vi.hoisted(() => ({ showSuccess: vi.fn() }))

vi.mock('../../../hooks/useDeckClipboard', () => ({
  useDeckClipboard: (): DeckClipboard => ({ ...clipboard }),
}))

vi.mock('@/lib/errorPresentation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/errorPresentation')>()),
  showSuccess,
}))

const WARNED_DECK: DecodedDeck = {
  equipment: {},
  deploymentOrder: [3, 4],
  warnings: [{ kind: 'identity', sinner: 'YiSang' }],
}

const VALID_DRAFT: ImportDraft = { text: 'code', verdict: { kind: 'valid', deck: WARNED_DECK } }

beforeAll(async () => {
  await i18n.changeLanguage('EN')
})

beforeEach(() => {
  vi.clearAllMocks()
  clipboard.handleExport.mockResolvedValue(undefined)
  clipboard.openImport.mockResolvedValue(undefined)
  clipboard.importDraft = null
})

function renderTransfer(writeDeck = vi.fn<(deck: DeckSnapshot) => void>()) {
  const children = vi.fn<(actions: DeckCodeActions) => React.ReactNode>(() => null)
  render(
    <DeckCodeTransfer
      readDeck={() => ({ equipment: {}, deploymentOrder: [] })}
      writeDeck={writeDeck}
      mdVersion={8}
    >
      {children}
    </DeckCodeTransfer>,
  )
  return { writeDeck, children }
}

describe('DeckCodeTransfer', () => {
  it('writes the applied deck without warnings, closes the dialog and toasts', () => {
    clipboard.importDraft = VALID_DRAFT
    const { writeDeck } = renderTransfer()

    fireEvent.click(screen.getByRole('button', { name: 'Apply' }))

    expect(writeDeck).toHaveBeenCalledExactlyOnceWith({ equipment: {}, deploymentOrder: [3, 4] })
    expect(writeDeck.mock.calls[0]?.[0]).not.toHaveProperty('warnings')
    expect(clipboard.closeImport).toHaveBeenCalledOnce()
    expect(showSuccess).toHaveBeenCalledExactlyOnceWith('planner:deckBuilder.importSuccess')
  })

  it('hands children an import and an export callback that return nothing', () => {
    const { children } = renderTransfer()

    const actions = children.mock.lastCall?.[0]
    expect(actions).toEqual({ onImport: expect.any(Function), onExport: expect.any(Function) })

    expect(actions?.onImport()).toBeUndefined()
    expect(clipboard.openImport).toHaveBeenCalledOnce()

    expect(actions?.onExport()).toBeUndefined()
    expect(clipboard.handleExport).toHaveBeenCalledOnce()
  })

  it('renders the import dialog once', () => {
    clipboard.importDraft = VALID_DRAFT
    renderTransfer()

    expect(screen.getAllByTestId('deck-import-text')).toHaveLength(1)
  })
})
