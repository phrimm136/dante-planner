import { CHUNK_RELOAD_STORAGE_KEY, CHUNK_RELOAD_WINDOW_MS } from '@/lib/constants'
import { markChunkLoadError } from '@/lib/routeErrorCode'

export function shouldReloadForChunkError(
  documentStartedAt: number,
  lastReloadAt: number | null,
): boolean {
  return lastReloadAt === null || documentStartedAt - lastReloadAt >= CHUNK_RELOAD_WINDOW_MS
}

function parseTimestamp(value: string | null): number | null {
  if (value === null) return null
  const parsed = Number(value)
  return value.trim() === '' || !Number.isFinite(parsed) ? null : parsed
}

function handlePreloadError(event: WindowEventMap['vite:preloadError']): void {
  markChunkLoadError(event.payload)

  let stored: string | null
  try {
    stored = sessionStorage.getItem(CHUNK_RELOAD_STORAGE_KEY)
  } catch {
    return
  }

  if (!shouldReloadForChunkError(performance.timeOrigin, parseTimestamp(stored))) return

  try {
    sessionStorage.setItem(CHUNK_RELOAD_STORAGE_KEY, String(Date.now()))
  } catch {
    return
  }
  window.location.reload()
}

export function installChunkReload(): void {
  window.addEventListener('vite:preloadError', handlePreloadError)
}
