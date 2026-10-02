import { useServerStatusStore } from './serverStatus'
import type { BannerSignal } from './selectBanner'

export function useBannerSignals(): BannerSignal[] {
  const server = useServerStatusStore((s) => s.status)

  const candidates: (BannerSignal | null)[] = [
    server === 'down' ? { kind: 'serverUnavailable' } : null,
  ]
  return candidates.filter((signal) => signal !== null)
}
