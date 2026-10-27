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

const SHORT_PAYLOAD_CODE = 'H4sIAAAAAAAC/wsM9XIBAIZ5pUMEAAAA'

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
    useDeckClipboard({ readDeck: () => ({ equipment: {}, deploymentOrder: [] }), maxLevel: 60 }),
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

  it('opens an empty draft without a toast when the clipboard read is refused', async () => {
    readText.mockRejectedValue(new DOMException('denied', 'NotAllowedError'))
    pako.release()
    const { showError, showErrorMessage } = await import('@/lib/errorPresentation')
    const { result } = await renderClipboard()

    await act(() => result.current.openImport())

    expect(result.current.importDraft).toEqual({ text: '', verdict: { kind: 'empty' } })
    expect(showError).not.toHaveBeenCalled()
    expect(showErrorMessage).not.toHaveBeenCalled()
  })

  it('opens an empty draft without an error when the clipboard API is absent', async () => {
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: undefined })
    pako.release()
    const { showError } = await import('@/lib/errorPresentation')
    const { result } = await renderClipboard()

    await act(() => result.current.openImport())

    expect(result.current.importDraft).toEqual({ text: '', verdict: { kind: 'empty' } })
    expect(showError).not.toHaveBeenCalled()
  })

  it('prefills the draft with a valid code from the clipboard', async () => {
    readText.mockResolvedValue(KNOWN_CODE)
    pako.release()
    const { result } = await renderClipboard()

    await act(() => result.current.openImport())

    const draft = result.current.importDraft
    expect(draft?.text).toBe(KNOWN_CODE)
    expect(draft?.verdict.kind).toBe('valid')
    if (draft?.verdict.kind !== 'valid') return
    expect(draft.verdict.deck.deploymentOrder).toEqual([3, 4, 0, 9, 2, 1, 10, 6, 7])
  })

  it('moves the verdict as the text changes, trimming whitespace', async () => {
    readText.mockResolvedValue('')
    pako.release()
    const { result } = await renderClipboard()
    await act(() => result.current.openImport())

    const kinds: string[] = []
    for (const text of ['H4sIAAAAAAAAA3NIzs8tKMovLskvSgEAq', '', '  ' + KNOWN_CODE + '\n']) {
      act(() => {
        result.current.setImportText(text)
      })
      kinds.push(result.current.importDraft?.verdict.kind ?? 'closed')
    }

    expect(kinds).toEqual(['invalid', 'empty', 'valid'])
  })

  it('judges a well-formed gzip whose payload is too short to be a deck as invalid', async () => {
    readText.mockResolvedValue(SHORT_PAYLOAD_CODE)
    pako.release()
    const { result } = await renderClipboard()

    await act(() => result.current.openImport())

    expect(result.current.importDraft?.verdict).toEqual({ kind: 'invalid' })
  })

  it('drops a prefill that resolves after the dialog was closed', async () => {
    let resolveRead: (text: string) => void = () => {}
    readText.mockReturnValue(
      new Promise<string>((resolve) => {
        resolveRead = resolve
      }),
    )
    pako.release()
    const { result } = await renderClipboard()

    let opened: Promise<void> = Promise.resolve()
    act(() => {
      opened = result.current.openImport()
    })
    act(() => {
      result.current.closeImport()
    })
    resolveRead(KNOWN_CODE)
    await act(() => opened)

    expect(result.current.importDraft).toBeNull()
  })
})
