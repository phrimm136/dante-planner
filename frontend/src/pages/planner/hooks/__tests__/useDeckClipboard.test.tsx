import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'

const pako = vi.hoisted(() => {
  const state = {
    loaded: false,
    release: () => {},
    gate: Promise.resolve(),
    reset() {
      state.loaded = false
      state.gate = new Promise<void>((resolve) => {
        state.release = resolve
      })
    },
  }
  return state
})

vi.mock('@/pages/identity', () => ({ useIdentityListSpec: () => ({}) }))
vi.mock('@/pages/ego', () => ({ useEGOListSpec: () => ({}) }))
vi.mock('@/lib/errorPresentation', () => ({
  showError: vi.fn<() => void>(),
  showErrorMessage: vi.fn<() => void>(),
  showSuccess: vi.fn<() => void>(),
}))

const KNOWN_CODE =
  'H4sIAAAAAAAACi2MIQ6AMBAEP4WtmF5LOIGoQKARW1JRScLrSYE1kxmx3OiahO+WchS1+FYWLFIrDXeb4wkFZ6yjgdTs1fznhtSFHd/J+mdCeABI0/iuYAAAAA=='

const EMPTY_DECK_CODE = 'H4sIAAAAAAAACnN0pCWwtQUALI+kE2AAAAA='

class RecordingClipboardItem {
  readonly items: Record<string, Promise<Blob>>
  constructor(items: Record<string, Promise<Blob>>) {
    this.items = items
  }
}

const write = vi.fn<(items: RecordingClipboardItem[]) => Promise<void>>()
const writeText = vi.fn<(text: string) => Promise<void>>()
const readText = vi.fn<() => Promise<string>>()

beforeEach(() => {
  vi.resetModules()
  pako.reset()
  vi.doMock('pako', async (importOriginal) => {
    pako.loaded = true
    await pako.gate
    return importOriginal<typeof import('pako')>()
  })
  write.mockReset().mockResolvedValue(undefined)
  writeText.mockReset().mockResolvedValue(undefined)
  readText.mockReset()
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: { write, writeText, readText },
  })
  vi.stubGlobal('ClipboardItem', RecordingClipboardItem)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

async function renderClipboard() {
  const { useDeckClipboard } = await import('../useDeckClipboard')
  return renderHook(() =>
    useDeckClipboard({ readDeck: () => ({ equipment: {}, deploymentOrder: [] }) }),
  )
}

describe('useDeckClipboard', () => {
  it('does not load pako until a clipboard action runs', async () => {
    await renderClipboard()

    expect(pako.loaded).toBe(false)
  })

  it('writes a ClipboardItem synchronously and resolves it to the deck code', async () => {
    const { result } = await renderClipboard()

    let exported: Promise<void> = Promise.resolve()
    act(() => {
      exported = result.current.handleExport()
    })

    expect(write).toHaveBeenCalledTimes(1)
    const [item] = write.mock.calls[0]?.[0] ?? []
    expect(item).toBeInstanceOf(RecordingClipboardItem)
    expect(writeText).not.toHaveBeenCalled()

    pako.release()
    await act(() => exported)

    const blob = await item?.items['text/plain']
    expect(await blob?.text()).toBe(EMPTY_DECK_CODE)
  })

  it('falls back to writeText with the deck code when ClipboardItem is unavailable', async () => {
    vi.stubGlobal('ClipboardItem', undefined)
    pako.release()
    const { result } = await renderClipboard()

    await act(() => result.current.handleExport())

    expect(write).not.toHaveBeenCalled()
    expect(writeText).toHaveBeenCalledWith(EMPTY_DECK_CODE)
  })

  it('stages a decoded deck from the clipboard on import', async () => {
    readText.mockResolvedValue(KNOWN_CODE)
    pako.release()
    const { result } = await renderClipboard()

    await act(() => result.current.handleImport())

    expect(result.current.pendingImport?.deploymentOrder).toEqual([3, 4, 0, 9, 2, 1, 10, 6, 7])
  })
})
