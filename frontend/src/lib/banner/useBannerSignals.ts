import { useServerStatusStore } from './serverStatus'
import { useStorageUnavailable } from './useStorageUnavailable'
import type { BannerSignal } from './selectBanner'

export function useBannerSignals(): BannerSignal[] {
  const server = useServerStatusStore((s) => s.status)
  const storageUnavailable = useStorageUnavailable()

  const candidates: (BannerSignal | null)[] = [
    server === 'down' ? { kind: 'serverUnavailable' } : null,
    storageUnavailable ? { kind: 'storageUnavailable' } : null,
  ]
  return candidates.filter((signal) => signal !== null)
}
