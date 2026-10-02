import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import { CHUNK_RELOAD_STORAGE_KEY, CHUNK_RELOAD_WINDOW_MS } from '@/lib/constants'
import { installChunkReload, shouldReloadForChunkError } from '../chunkReload'
import { classifyRouteError } from '../routeErrorCode'

describe('shouldReloadForChunkError', () => {
  it.each([
    { started: 50_000, last: null, expected: true },
    { started: 50_000, last: 50_000, expected: false },
    { started: 50_000, last: 50_000 - CHUNK_RELOAD_WINDOW_MS + 1, expected: false },
    { started: 50_000, last: 50_000 - CHUNK_RELOAD_WINDOW_MS, expected: true },
    { started: 50_000, last: 0, expected: true },
    { started: 50_000 + 15_000, last: 50_000, expected: true },
    { started: 50_000 + 1_000, last: 50_000, expected: false },
    { started: 50_000, last: 50_000 + 5_000, expected: false },
  ])('documentStartedAt=$started last=$last → $expected', ({ started, last, expected }) => {
    expect(shouldReloadForChunkError(started, last)).toBe(expected)
  })
})

describe('installChunkReload', () => {
  const reload = vi.fn()
  const listenerErrors: unknown[] = []

  function dispatchPreloadError(payload: unknown): Event {
    const event = new Event('vite:preloadError', { cancelable: true })
    Object.assign(event, { payload })
    window.dispatchEvent(event)
    return event
  }

  function startDocumentAt(timeOrigin: number): void {
    vi.spyOn(performance, 'timeOrigin', 'get').mockReturnValue(timeOrigin)
  }

  function recordListenerError(event: ErrorEvent): void {
    listenerErrors.push(event.error)
    event.preventDefault()
  }

  beforeAll(() => {
    installChunkReload()
  })

  beforeEach(() => {
    sessionStorage.clear()
    reload.mockReset()
    listenerErrors.length = 0
    window.addEventListener('error', recordListenerError)
    vi.spyOn(window.location, 'reload').mockImplementation(reload)
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(1_000_000)
    startDocumentAt(990_000)
  })

  afterEach(() => {
    window.removeEventListener('error', recordListenerError)
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('reloads on the first chunk failure and records the failure time', () => {
    dispatchPreloadError(new TypeError('x'))

    expect(reload).toHaveBeenCalledTimes(1)
    expect(sessionStorage.getItem(CHUNK_RELOAD_STORAGE_KEY)).toBe('1000000')
  })

  it('does not reload twice for two failures in the same document', () => {
    dispatchPreloadError(new TypeError('x'))
    vi.setSystemTime(1_000_000 + 60_000)
    dispatchPreloadError(new TypeError('x'))

    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('reloads a document that started after the window following the last reload', () => {
    sessionStorage.setItem(CHUNK_RELOAD_STORAGE_KEY, '1000000')
    startDocumentAt(1_000_000 + 15_000)
    vi.setSystemTime(1_000_000 + 20_000)
    dispatchPreloadError(new TypeError('x'))

    expect(reload).toHaveBeenCalledTimes(1)
    expect(sessionStorage.getItem(CHUNK_RELOAD_STORAGE_KEY)).toBe('1020000')
  })

  it('does not reload the document its own reload produced, however late the failure', () => {
    sessionStorage.setItem(CHUNK_RELOAD_STORAGE_KEY, '1000000')
    startDocumentAt(1_000_000 + 1_000)
    vi.setSystemTime(1_000_000 + 60_000)
    dispatchPreloadError(new TypeError('x'))

    expect(reload).not.toHaveBeenCalled()
    expect(sessionStorage.getItem(CHUNK_RELOAD_STORAGE_KEY)).toBe('1000000')
  })

  it.each(['', 'not-a-number'])('treats a stored value of %j as no previous reload', (stored) => {
    sessionStorage.setItem(CHUNK_RELOAD_STORAGE_KEY, stored)
    dispatchPreloadError(new TypeError('x'))

    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('does not reload or throw when sessionStorage cannot be read', () => {
    vi.stubGlobal('sessionStorage', {
      getItem: () => {
        throw new DOMException('blocked', 'SecurityError')
      },
      setItem: () => {},
    })
    const payload = new TypeError('x')

    expect(() => dispatchPreloadError(payload)).not.toThrow()
    expect(listenerErrors).toEqual([])
    expect(reload).not.toHaveBeenCalled()
    expect(classifyRouteError(payload)).toBe('CHUNK_LOAD')
  })

  it('does not reload or throw when sessionStorage cannot be written', () => {
    vi.stubGlobal('sessionStorage', {
      getItem: () => null,
      setItem: () => {
        throw new DOMException('blocked', 'QuotaExceededError')
      },
    })
    const payload = new TypeError('x')

    expect(() => dispatchPreloadError(payload)).not.toThrow()
    expect(listenerErrors).toEqual([])
    expect(reload).not.toHaveBeenCalled()
    expect(classifyRouteError(payload)).toBe('CHUNK_LOAD')
  })

  it('never prevents the event default', () => {
    const event = dispatchPreloadError(new TypeError('x'))

    expect(event.defaultPrevented).toBe(false)
  })

  it('stamps the payload as a chunk load failure', () => {
    const payload = new TypeError('x')
    dispatchPreloadError(payload)

    expect(classifyRouteError(payload)).toBe('CHUNK_LOAD')
  })

  it('still decides on reload when the payload is not an object', () => {
    expect(() => dispatchPreloadError('str')).not.toThrow()
    expect(reload).toHaveBeenCalledTimes(1)
  })
})
