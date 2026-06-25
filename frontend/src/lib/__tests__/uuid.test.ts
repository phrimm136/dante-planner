import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

const V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

/**
 * Import fresh inside each test so module-level crypto bindings observe the current globals.
 */
async function importFresh() {
  vi.resetModules()
  return import('../uuid')
}

describe('generateUUID', () => {
  let originalCrypto: typeof globalThis.crypto | undefined

  beforeEach(() => {
    originalCrypto = globalThis.crypto
  })

  afterEach(() => {
    if (originalCrypto !== undefined) {
      Object.defineProperty(globalThis, 'crypto', {
        value: originalCrypto,
        configurable: true,
      })
    }
  })

  it('returns crypto.randomUUID output when available', async () => {
    Object.defineProperty(globalThis, 'crypto', {
      value: {
        randomUUID: () => '11111111-2222-4333-8444-555555555555',
        getRandomValues: (_: Uint8Array) => _,
      },
      configurable: true,
    })

    const { generateUUID } = await importFresh()
    expect(generateUUID()).toBe('11111111-2222-4333-8444-555555555555')
  })

  it('falls back to getRandomValues with correct v4 version and variant bits', async () => {
    // Bytes deliberately set to all-zeros so the only non-zero bits in the output
    // come from the version/variant masks the implementation applies.
    Object.defineProperty(globalThis, 'crypto', {
      value: {
        randomUUID: undefined,
        getRandomValues: (buf: Uint8Array) => {
          for (let i = 0; i < buf.length; i++) buf[i] = 0
          return buf
        },
      },
      configurable: true,
    })

    const { generateUUID } = await importFresh()
    const uuid = generateUUID()

    expect(uuid).toMatch(V4_PATTERN)
    // Byte 6 high nibble = 4 → 13th hex char (index 14 incl. dashes) = '4'
    expect(uuid[14]).toBe('4')
    // Byte 8 high 2 bits = 10 → 17th hex char must be 8/9/a/b
    expect(['8', '9', 'a', 'b']).toContain(uuid[19])
  })

  it('throws when neither randomUUID nor getRandomValues is available', async () => {
    Object.defineProperty(globalThis, 'crypto', {
      value: { randomUUID: undefined, getRandomValues: undefined },
      configurable: true,
    })

    const { generateUUID } = await importFresh()
    expect(() => generateUUID()).toThrow(/Web Crypto API/)
  })

  it('throws when crypto global is missing entirely', async () => {
    Object.defineProperty(globalThis, 'crypto', {
      value: undefined,
      configurable: true,
    })

    const { generateUUID } = await importFresh()
    expect(() => generateUUID()).toThrow(/Web Crypto API/)
  })

  it('produces distinct outputs across calls under the fallback path', async () => {
    let counter = 0
    Object.defineProperty(globalThis, 'crypto', {
      value: {
        randomUUID: undefined,
        getRandomValues: (buf: Uint8Array) => {
          for (let i = 0; i < buf.length; i++) buf[i] = (counter + i) & 0xff
          counter++
          return buf
        },
      },
      configurable: true,
    })

    const { generateUUID } = await importFresh()
    const a = generateUUID()
    const b = generateUUID()
    expect(a).not.toBe(b)
  })
})
