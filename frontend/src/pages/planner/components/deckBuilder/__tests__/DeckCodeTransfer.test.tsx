import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

import i18n from '@/lib/i18n'

import { DeckCodeTransfer } from '../DeckCodeTransfer'
import { SeededPlannerStore } from '../../../stores/__tests__/plannerStoreHarness'
import { decodeDeckCode } from '../../../lib/deckCode'
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

const hook = vi.hoisted(() => ({ real: false }))

const { showSuccess } = vi.hoisted(() => ({ showSuccess: vi.fn() }))

vi.mock('../../../hooks/useDeckClipboard', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../hooks/useDeckClipboard')>()
  return {
    ...actual,
    useDeckClipboard: (options: Parameters<typeof actual.useDeckClipboard>[0]): DeckClipboard =>
      hook.real ? actual.useDeckClipboard(options) : { ...clipboard },
  }
})

vi.mock('../../../lib/deckCode', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/deckCode')>()
  return { ...actual, decodeDeckCode: vi.fn(actual.decodeDeckCode) }
})

vi.mock('@/pages/identity', () => ({ useIdentityListSpec: () => ({}) }))
vi.mock('@/pages/ego', () => ({ useEGOListSpec: () => ({}) }))

const KNOWN_CODE =
  'H4sIAAAAAAAACi2MIQ6AMBAEP4WtmF5LOIGoQKARW1JRScLrSYE1kxmx3OiahO+WchS1+FYWLFIrDXeb4wkFZ6yjgdTs1fznhtSFHd/J+mdCeABI0/iuYAAAAA=='

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
  hook.real = false
})

function renderTransfer(writeDeck = vi.fn<(deck: DeckSnapshot) => void>(), identityMaxLevel = 60) {
  const children = vi.fn<(actions: DeckCodeActions) => React.ReactNode>(() => null)
  render(
    <SeededPlannerStore identityMaxLevel={identityMaxLevel}>
      <DeckCodeTransfer
        readDeck={() => ({ equipment: {}, deploymentOrder: [] })}
        writeDeck={writeDeck}
      >
        {children}
      </DeckCodeTransfer>
    </SeededPlannerStore>,
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

  it('decodes an applied code with the store cap and never loads one itself', async () => {
    hook.real = true
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { readText: () => Promise.resolve(KNOWN_CODE) },
    })
    const { writeDeck, children } = renderTransfer(undefined, 45)

    children.mock.lastCall?.[0].onImport()
    fireEvent.click(await screen.findByRole('button', { name: 'Apply' }))

    await waitFor(() => {
      expect(writeDeck).toHaveBeenCalledOnce()
    })
    expect(vi.mocked(decodeDeckCode).mock.calls.map((call) => call[3])).toContain(45)
    expect(vi.mocked(decodeDeckCode).mock.calls.every((call) => call[3] === 45)).toBe(true)
  })

  it('renders the import dialog once', () => {
    clipboard.importDraft = VALID_DRAFT
    renderTransfer()

    expect(screen.getAllByTestId('deck-import-text')).toHaveLength(1)
  })
})
