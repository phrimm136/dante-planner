import { describe, it, expect } from 'vitest'

import { bannerDismissKey, selectBanner, type BannerSignal } from '../selectBanner'

const server: BannerSignal = { kind: 'serverUnavailable' }
const storage: BannerSignal = { kind: 'storageUnavailable' }
const updateX: BannerSignal = { kind: 'updateAvailable', buildId: 'x' }
const updateY: BannerSignal = { kind: 'updateAvailable', buildId: 'y' }

const none: ReadonlySet<string> = new Set()

describe('selectBanner', () => {
  it.each([
    ['server over storage and update', [updateX, storage, server], server],
    ['storage over update', [updateX, storage], storage],
    ['update alone', [updateX], updateX],
  ] as const)('picks %s', (_name, signals, expected) => {
    expect(selectBanner(signals, none)).toBe(expected)
  })

  it('shows nothing when no signal is active', () => {
    expect(selectBanner([], none)).toBeNull()
  })

  it('skips a dismissed signal and shows the next', () => {
    expect(selectBanner([storage, updateX], new Set(['storageUnavailable']))).toBe(updateX)
  })

  it('never skips the server signal, which cannot be dismissed', () => {
    expect(bannerDismissKey(server)).toBeNull()
    expect(selectBanner([server], new Set(['serverUnavailable']))).toBe(server)
  })

  it('hides a dismissed update only for its own build', () => {
    const dismissed = new Set([bannerDismissKey(updateX) ?? ''])

    expect(selectBanner([updateX], dismissed)).toBeNull()
    expect(selectBanner([updateY], dismissed)).toBe(updateY)
  })
})
