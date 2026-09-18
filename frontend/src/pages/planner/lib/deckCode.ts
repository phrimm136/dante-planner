/**
 * Deck Code Import/Export Utility
 *
 * Encodes and decodes deck configurations into shareable strings.
 *
 * Format: 560-bit binary string (46 bits per sinner × 12 sinners)
 * Per-sinner structure (46 bits):
 *   - Bits 1-8: Identity ID (1-indexed, 8 bits)
 *   - Bits 9-12: Deployment order (4 bits, 0=not deployed, 1-12=position)
 *   - Bits 13-19: ZAYIN EGO ID (7 bits)
 *   - Bits 20-26: TETH EGO ID (7 bits)
 *   - Bits 27-33: HE EGO ID (7 bits)
 *   - Bits 34-40: WAW EGO ID (7 bits)
 *   - Bits 41-46: ALEPH EGO ID (6 bits)
 *
 * Encoding chain: Binary → Base64 → Gzip → Base64
 */

import { gzip, ungzip } from 'pako'
import { SINNERS, MAX_LEVEL, IdentityIdSchema, EGOIdSchema } from '@/shared/gameData'
import { DECK_CODE_MAX_LENGTH } from '@/lib/constants'
import type { SinnerEquipment } from '../types/DeckTypes'
import type { EgoType, IdentityId, EGOId } from '@/shared/gameData'

const BITS_PER_SINNER = 46
const TOTAL_BITS = 560

const EGO_SLOTS: { rank: EgoType; bits: number }[] = [
  { rank: 'ZAYIN', bits: 7 },
  { rank: 'TETH', bits: 7 },
  { rank: 'HE', bits: 7 },
  { rank: 'WAW', bits: 7 },
  { rank: 'ALEPH', bits: 6 },
]

/** Offset of the OS field in a gzip member header (RFC 1952 section 2.3) */
export const GZIP_OS_BYTE_OFFSET = 9

/**
 * OS field value 10, TOPS-20 (RFC 1952 section 2.3.1). Load-bearing: it fixes
 * the leading base64 character of every emitted code, so any other value
 * changes the shape of what this app writes.
 */
export const GZIP_OS_TOPS20 = 10

export interface DecodedDeck {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  warnings: string[]
}

export interface ValidationResult {
  isValid: boolean
  warnings: string[]
}

/**
 * Extract entity index from full ID (last 2 digits)
 */
function getEntityIndex(fullId: string): number {
  return parseInt(fullId.slice(-2), 10)
}

/**
 * Reconstruct full ID from sinner index (0-11) and entity index
 */
function reconstructIdentityId(sinnerIndex: number, entityIndex: number): IdentityId {
  const sinnerPart = (sinnerIndex + 1).toString().padStart(2, '0')
  const entityPart = entityIndex.toString().padStart(2, '0')
  return IdentityIdSchema.parse(`1${sinnerPart}${entityPart}`)
}

function reconstructEgoId(sinnerIndex: number, entityIndex: number): EGOId {
  const sinnerPart = (sinnerIndex + 1).toString().padStart(2, '0')
  const entityPart = entityIndex.toString().padStart(2, '0')
  return EGOIdSchema.parse(`2${sinnerPart}${entityPart}`)
}

function toBinary(num: number, bits: number): string {
  return num.toString(2).padStart(bits, '0')
}

function fromBinary(binary: string): number {
  return parseInt(binary, 2)
}

function binaryToBytes(binary: string): Uint8Array {
  const padded = binary.padEnd(Math.ceil(binary.length / 8) * 8, '0')
  const bytes = new Uint8Array(padded.length / 8)
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = parseInt(padded.slice(i * 8, (i + 1) * 8), 2)
  }
  return bytes
}

function bytesToBinary(bytes: Uint8Array): string {
  let binary = ''
  for (const byte of bytes) {
    binary += byte.toString(2).padStart(8, '0')
  }
  return binary
}

export function encodeDeckCode(
  equipment: Record<string, SinnerEquipment>,
  deploymentOrder: number[],
): string {
  let binary = ''

  for (let sinnerIndex = 0; sinnerIndex < 12; sinnerIndex++) {
    const sinnerCode = String(sinnerIndex + 1)
    const sinnerEquipment = equipment[sinnerCode]

    const identityIndex = sinnerEquipment ? getEntityIndex(sinnerEquipment.identity.id) : 0
    binary += toBinary(identityIndex, 8)

    const deploymentPosition = deploymentOrder.indexOf(sinnerIndex) + 1
    binary += toBinary(deploymentPosition, 4)

    for (const { rank, bits } of EGO_SLOTS) {
      const ego = sinnerEquipment?.egos[rank]
      const egoIndex = ego ? getEntityIndex(ego.id) : 0
      binary += toBinary(egoIndex, bits)
    }
  }

  binary = binary.padEnd(TOTAL_BITS, '0')

  const bytes = binaryToBytes(binary)

  const firstBase64 = btoa(String.fromCharCode(...bytes))

  // pako 3 ignores a `header` option on the one-shot gzip(), so the OS byte is
  // written directly into the emitted header instead.
  const compressed = gzip(firstBase64)
  compressed[GZIP_OS_BYTE_OFFSET] = GZIP_OS_TOPS20

  const secondBase64 = btoa(String.fromCharCode(...compressed))

  return secondBase64
}

export function decodeDeckCode(
  code: string,
  identitySpecMap: Record<string, unknown>,
  egoSpecMap: Record<string, { maxThreadspin: 4 | 5 }>,
): DecodedDeck {
  const warnings: string[] = []

  // A pasted code reaches atob and then the inflater unbounded; a real code is
  // around 150 characters, so anything past the cap decodes to nothing.
  if (code.length > DECK_CODE_MAX_LENGTH) {
    return {
      equipment: {},
      deploymentOrder: [],
      warnings: ['Deck code exceeds the maximum length'],
    }
  }

  const compressedStr = atob(code)
  const compressed = new Uint8Array(compressedStr.length)
  for (let i = 0; i < compressedStr.length; i++) {
    compressed[i] = compressedStr.charCodeAt(i)
  }

  const firstBase64 = ungzip(compressed, { toText: true })

  const bytesStr = atob(firstBase64)
  const bytes = new Uint8Array(bytesStr.length)
  for (let i = 0; i < bytesStr.length; i++) {
    bytes[i] = bytesStr.charCodeAt(i)
  }

  const binary = bytesToBinary(bytes)

  const equipment: Record<string, SinnerEquipment> = {}
  const deploymentMap: { position: number; sinnerIndex: number }[] = []

  for (let sinnerIndex = 0; sinnerIndex < 12; sinnerIndex++) {
    const sinnerCode = String(sinnerIndex + 1)
    const sinnerName = SINNERS[sinnerIndex]
    const offset = sinnerIndex * BITS_PER_SINNER

    const identityIndex = fromBinary(binary.slice(offset, offset + 8))

    const deploymentPosition = fromBinary(binary.slice(offset + 8, offset + 12))
    if (deploymentPosition > 0) {
      deploymentMap.push({ position: deploymentPosition, sinnerIndex })
    }

    const identityId = reconstructIdentityId(sinnerIndex, identityIndex)

    if (identityIndex > 0 && !identitySpecMap[identityId]) {
      warnings.push(`Invalid identity ID ${identityId} for ${sinnerName}`)
    }

    const egos: SinnerEquipment['egos'] = {}
    let egoOffset = offset + 12

    for (const { rank, bits } of EGO_SLOTS) {
      const egoIndex = fromBinary(binary.slice(egoOffset, egoOffset + bits))
      egoOffset += bits

      if (egoIndex > 0) {
        const egoId = reconstructEgoId(sinnerIndex, egoIndex)

        if (!egoSpecMap[egoId]) {
          warnings.push(`Invalid EGO ID ${egoId} for ${sinnerName}`)
        } else {
          egos[rank] = {
            id: egoId,
            threadspin: egoSpecMap[egoId].maxThreadspin,
          }
        }
      }
    }

    if (identityIndex > 0 && identitySpecMap[identityId]) {
      equipment[sinnerCode] = {
        identity: {
          id: identityId,
          uptie: 4,
          level: MAX_LEVEL,
        },
        egos,
      }
    } else if (identityIndex > 0) {
      const defaultIdentityId = reconstructIdentityId(sinnerIndex, 1)
      equipment[sinnerCode] = {
        identity: {
          id: defaultIdentityId,
          uptie: 4,
          level: MAX_LEVEL,
        },
        egos,
      }
    } else {
      const defaultIdentityId = reconstructIdentityId(sinnerIndex, 1)
      const defaultEgoId = reconstructEgoId(sinnerIndex, 1)
      equipment[sinnerCode] = {
        identity: {
          id: defaultIdentityId,
          uptie: 4,
          level: MAX_LEVEL,
        },
        egos: {
          ZAYIN: {
            id: defaultEgoId,
            threadspin: egoSpecMap[defaultEgoId]?.maxThreadspin ?? 4,
          },
        },
      }
    }
  }

  deploymentMap.sort((a, b) => a.position - b.position)
  const deploymentOrder = deploymentMap.map((d) => d.sinnerIndex)

  return {
    equipment,
    deploymentOrder,
    warnings,
  }
}

export function validateDeckCode(code: string): ValidationResult {
  if (code.length > DECK_CODE_MAX_LENGTH) {
    return { isValid: false, warnings: ['Invalid deck code format'] }
  }

  try {
    const compressedStr = atob(code)
    const compressed = new Uint8Array(compressedStr.length)
    for (let i = 0; i < compressedStr.length; i++) {
      compressed[i] = compressedStr.charCodeAt(i)
    }

    const firstBase64 = ungzip(compressed, { toText: true })

    atob(firstBase64)

    return { isValid: true, warnings: [] }
  } catch {
    return { isValid: false, warnings: ['Invalid deck code format'] }
  }
}
