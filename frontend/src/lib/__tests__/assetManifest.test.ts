import { describe, it, expect, vi } from 'vitest'

vi.mock('virtual:asset-manifest', async () => {
  const { hashKey: hk } = await import('../hashKey')
  return {
    default: {
      [hk('images/icon/sinners/YiSang.webp')]: 'a/abc123def456.webp',
    },
  }
})

import { resolveAsset } from '../assetManifest'

describe('resolveAsset', () => {
  it('returns hashed path when manifest has entry', () => {
    expect(resolveAsset('/images/icon/sinners/YiSang.webp')).toBe('/a/abc123def456.webp')
  })

  it('falls back to original path when entry missing', () => {
    expect(resolveAsset('/images/unknown/file.webp')).toBe('/images/unknown/file.webp')
  })

  it('strips leading slash for lookup', () => {
    expect(resolveAsset('/images/icon/sinners/YiSang.webp')).toBe('/a/abc123def456.webp')
  })

  it('handles paths without leading slash', () => {
    expect(resolveAsset('images/icon/sinners/YiSang.webp')).toBe('/a/abc123def456.webp')
  })
})
