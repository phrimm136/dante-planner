export type BannerSignal =
  | { kind: 'serverUnavailable' }
  | { kind: 'storageUnavailable' }
  | { kind: 'updateAvailable'; buildId: string }

const PRIORITY: Record<BannerSignal['kind'], number> = {
  serverUnavailable: 0,
  storageUnavailable: 1,
  updateAvailable: 2,
}

export function bannerDismissKey(signal: BannerSignal): string | null {
  switch (signal.kind) {
    case 'serverUnavailable':
      return null
    case 'storageUnavailable':
      return signal.kind
    case 'updateAvailable':
      return `${signal.kind}:${signal.buildId}`
  }
}

function isDismissed(signal: BannerSignal, dismissed: ReadonlySet<string>): boolean {
  const key = bannerDismissKey(signal)
  return key !== null && dismissed.has(key)
}

export function selectBanner(
  signals: readonly BannerSignal[],
  dismissed: ReadonlySet<string>,
): BannerSignal | null {
  return signals.reduce<BannerSignal | null>((best, signal) => {
    if (isDismissed(signal, dismissed)) return best
    if (best === null || PRIORITY[signal.kind] < PRIORITY[best.kind]) return signal
    return best
  }, null)
}
