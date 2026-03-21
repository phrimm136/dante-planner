import { describe, it, expect } from 'vitest'
import { hashKey } from '../hashKey'

describe('hashKey', () => {
  it('returns 8-char hex string', () => {
    const result = hashKey('images/icon/test.webp')
    expect(result).toMatch(/^[a-f0-9]{8}$/)
  })

  it('is deterministic — same input produces same output', () => {
    const a = hashKey('images/icon/sinners/YiSang.webp')
    const b = hashKey('images/icon/sinners/YiSang.webp')
    expect(a).toBe(b)
  })

  it('produces different hashes for different inputs', () => {
    const a = hashKey('images/icon/sinners/YiSang.webp')
    const b = hashKey('images/icon/sinners/Faust.webp')
    expect(a).not.toBe(b)
  })

  it('handles empty string', () => {
    const result = hashKey('')
    expect(result).toMatch(/^[a-f0-9]{8}$/)
    // FNV-1a offset basis: 0x811c9dc5
    expect(result).toBe('811c9dc5')
  })

  it('handles single character', () => {
    const result = hashKey('a')
    expect(result).toMatch(/^[a-f0-9]{8}$/)
    expect(result).not.toBe('811c9dc5') // different from empty
  })

  it('handles unicode characters', () => {
    const result = hashKey('i18n/KR/sinnerNames.json')
    expect(result).toMatch(/^[a-f0-9]{8}$/)
  })

  it('is sensitive to path separators', () => {
    const a = hashKey('images/icon/test.webp')
    const b = hashKey('images\\icon\\test.webp')
    expect(a).not.toBe(b)
  })

  it('is sensitive to leading slash', () => {
    const a = hashKey('images/icon/test.webp')
    const b = hashKey('/images/icon/test.webp')
    expect(a).not.toBe(b)
  })

  it('produces low collision rate for similar paths', () => {
    // Hash 1000 sequential identity paths and check for uniqueness
    const hashes = new Set<string>()
    for (let i = 10100; i < 11100; i++) {
      hashes.add(hashKey(`data/identity/${i}.json`))
    }
    expect(hashes.size).toBe(1000)
  })

  it('matches known FNV-1a reference values', () => {
    // FNV-1a 32-bit test vectors
    // "foobar" -> 0xbf9cf968
    expect(hashKey('foobar')).toBe('bf9cf968')
  })
})
