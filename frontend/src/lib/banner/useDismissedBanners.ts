import { useState } from 'react'
import { z } from 'zod'

import { BANNER_DISMISS_STORAGE_KEY } from '@/lib/constants'

import { bannerDismissKey, type BannerSignal } from './selectBanner'

const DismissedKeysSchema = z.array(z.string())

function readDismissed(): Set<string> {
  let raw: string | null
  try {
    raw = sessionStorage.getItem(BANNER_DISMISS_STORAGE_KEY)
  } catch {
    return new Set()
  }
  if (raw === null) return new Set()

  let json: unknown
  try {
    json = JSON.parse(raw)
  } catch {
    return new Set()
  }
  const parsed = DismissedKeysSchema.safeParse(json)
  return parsed.success ? new Set(parsed.data) : new Set()
}

function writeDismissed(keys: ReadonlySet<string>): void {
  try {
    sessionStorage.setItem(BANNER_DISMISS_STORAGE_KEY, JSON.stringify([...keys]))
  } catch {
    return
  }
}

export function useDismissedBanners(): [ReadonlySet<string>, (signal: BannerSignal) => void] {
  const [dismissed, setDismissed] = useState<ReadonlySet<string>>(readDismissed)

  const dismiss = (signal: BannerSignal) => {
    const key = bannerDismissKey(signal)
    if (key === null) return
    const next = new Set([...dismissed, key])
    writeDismissed(next)
    setDismissed(next)
  }

  return [dismissed, dismiss]
}
