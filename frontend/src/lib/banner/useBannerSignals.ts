import { useServerStatusStore } from './serverStatus'
import { useStorageUnavailable } from './useStorageUnavailable'
import { useUpdateAvailable } from './useUpdateAvailable'
import type { BannerSignal } from './selectBanner'

export function useBannerSignals(): BannerSignal[] {
  const server = useServerStatusStore((s) => s.status)
  const storageUnavailable = useStorageUnavailable()
  const newBuildId = useUpdateAvailable()

  const candidates: (BannerSignal | null)[] = [
    server === 'down' ? { kind: 'serverUnavailable' } : null,
    storageUnavailable ? { kind: 'storageUnavailable' } : null,
    newBuildId !== null ? { kind: 'updateAvailable', buildId: newBuildId } : null,
  ]
  return candidates.filter((signal) => signal !== null)
}
