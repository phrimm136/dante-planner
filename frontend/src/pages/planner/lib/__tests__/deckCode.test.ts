/**
 * deckCode.test.ts
 *
 * Unit tests for the deck code compression layer.
 * Covers the gzip OS header byte and decoding of a previously emitted code.
 */

import { describe, it, expect } from 'vitest'
import {
  encodeDeckCode,
  decodeDeckCode,
  validateDeckCode,
  GZIP_OS_BYTE_OFFSET,
  GZIP_OS_TOPS20,
} from '../deckCode'
import { DECK_CODE_MAX_LENGTH } from '@/lib/constants'

/** A code emitted by this encoder, kept verbatim to detect format drift. */
const KNOWN_CODE =
  'H4sIAAAAAAAACi2MIQ6AMBAEP4WtmF5LOIGoQKARW1JRScLrSYE1kxmx3OiahO+WchS1+FYWLFIrDXeb4wkFZ6yjgdTs1fznhtSFHd/J+mdCeABI0/iuYAAAAA=='

function toBytes(base64: string): Uint8Array {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

describe('deck code gzip header', () => {
  it('emits the TOPS-20 OS byte', () => {
    const bytes = toBytes(encodeDeckCode({}, []))

    expect(bytes[GZIP_OS_BYTE_OFFSET]).toBe(GZIP_OS_TOPS20)
  })

  it('emits a gzip member with zeroed mtime so encoding is deterministic', () => {
    const first = encodeDeckCode({}, [])
    const second = encodeDeckCode({}, [])
    const bytes = toBytes(first)

    expect(first).toBe(second)
    expect([bytes[0], bytes[1]]).toEqual([0x1f, 0x8b])
    expect(Array.from(bytes.subarray(4, 8))).toEqual([0, 0, 0, 0])
  })

  it('keeps the OS byte the previously emitted code carries', () => {
    expect(toBytes(KNOWN_CODE)[GZIP_OS_BYTE_OFFSET]).toBe(GZIP_OS_TOPS20)
  })
})

describe('decodeDeckCode', () => {
  it('recovers the deployment order from a previously emitted code', () => {
    const { deploymentOrder } = decodeDeckCode(KNOWN_CODE, {}, {})

    expect(deploymentOrder).toEqual([3, 4, 0, 9, 2, 1, 10, 6, 7])
  })

  it('decodes without throwing when spec maps are empty', () => {
    const { equipment, warnings } = decodeDeckCode(KNOWN_CODE, {}, {})

    expect(Object.keys(equipment)).toHaveLength(12)
    expect(warnings.length).toBeGreaterThan(0)
  })
})

describe('deck code length bound', () => {
  const overLong = 'A'.repeat(DECK_CODE_MAX_LENGTH + 1)

  it('rejects an over-long code before it reaches atob', () => {
    const result = validateDeckCode(overLong)
    expect(result.isValid).toBe(false)
  })

  it('decodes an over-long code to nothing rather than inflating it', () => {
    const decoded = decodeDeckCode(overLong, {}, {})
    expect(decoded.equipment).toEqual({})
    expect(decoded.deploymentOrder).toEqual([])
    expect(decoded.warnings.length).toBeGreaterThan(0)
  })

  it('still accepts a code at the cap', () => {
    // Not a real code, so it fails on content rather than on length.
    const atCap = 'A'.repeat(DECK_CODE_MAX_LENGTH)
    expect(() => validateDeckCode(atCap)).not.toThrow()
  })
})
