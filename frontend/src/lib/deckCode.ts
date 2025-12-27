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

import pako from 'pako'
import { SINNERS } from './constants'
import type { SinnerEquipment } from '@/types/DeckTypes'
import type { EgoType } from '@/types/EGOTypes'

const BITS_PER_SINNER = 46
const TOTAL_BITS = 560

const EGO_RANK_ORDER: EgoType[] = ['ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH']
const EGO_BIT_LENGTHS = [7, 7, 7, 7, 6] // ZAYIN, TETH, HE, WAW, ALEPH

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
function reconstructIdentityId(sinnerIndex: number, entityIndex: number): string {
  const sinnerPart = (sinnerIndex + 1).toString().padStart(2, '0')
  const entityPart = entityIndex.toString().padStart(2, '0')
  return `1${sinnerPart}${entityPart}`
}

function reconstructEgoId(sinnerIndex: number, entityIndex: number): string {
  const sinnerPart = (sinnerIndex + 1).toString().padStart(2, '0')
  const entityPart = entityIndex.toString().padStart(2, '0')
  return `2${sinnerPart}${entityPart}`
}

/**
 * Convert a number to a binary string with specified bit length
 */
function toBinary(num: number, bits: number): string {
  return num.toString(2).padStart(bits, '0')
}

/**
 * Convert a binary string to a number
 */
function fromBinary(binary: string): number {
  return parseInt(binary, 2)
}

/**
 * Convert binary string to Uint8Array
 */
function binaryToBytes(binary: string): Uint8Array {
  // Pad to multiple of 8
  const padded = binary.padEnd(Math.ceil(binary.length / 8) * 8, '0')
  const bytes = new Uint8Array(padded.length / 8)
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = parseInt(padded.slice(i * 8, (i + 1) * 8), 2)
  }
  return bytes
}

/**
 * Convert Uint8Array to binary string
 */
function bytesToBinary(bytes: Uint8Array): string {
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += bytes[i].toString(2).padStart(8, '0')
  }
  return binary
}

/**
 * Encode equipment and deployment order into a deck code
 */
export function encodeDeckCode(
  equipment: Record<string, SinnerEquipment>,
  deploymentOrder: number[]
): string {
  let binary = ''

  for (let sinnerIndex = 0; sinnerIndex < 12; sinnerIndex++) {
    const sinnerName = SINNERS[sinnerIndex]
    const sinnerEquipment = equipment[sinnerName]

    // Identity ID (8 bits, 1-indexed)
    const identityIndex = sinnerEquipment
      ? getEntityIndex(sinnerEquipment.identity.id)
      : 0
    binary += toBinary(identityIndex, 8)

    // Deployment order (4 bits, 0=not deployed, 1-12=position)
    const deploymentPosition = deploymentOrder.indexOf(sinnerIndex) + 1
    binary += toBinary(deploymentPosition, 4)

    // EGO slots (ZAYIN, TETH, HE, WAW, ALEPH)
    for (let i = 0; i < EGO_RANK_ORDER.length; i++) {
      const rank = EGO_RANK_ORDER[i]
      const bits = EGO_BIT_LENGTHS[i]
      const ego = sinnerEquipment?.egos[rank]
      const egoIndex = ego ? getEntityIndex(ego.id) : 0
      binary += toBinary(egoIndex, bits)
    }
  }

  // Pad to 560 bits
  binary = binary.padEnd(TOTAL_BITS, '0')

  // Convert to bytes
  const bytes = binaryToBytes(binary)

  // First base64 encode
  const firstBase64 = btoa(String.fromCharCode(...bytes))

  // Gzip compress with Windows OS header for compatibility
  const compressed = pako.gzip(firstBase64, { header: { os: 10 } } as pako.DeflateFunctionOptions)

  // Second base64 encode
  const secondBase64 = btoa(String.fromCharCode(...compressed))

  return secondBase64
}

/**
 * Decode a deck code into equipment and deployment order
 */
export function decodeDeckCode(
  code: string,
  identitySpecMap: Record<string, unknown>,
  egoSpecMap: Record<string, unknown>
): DecodedDeck {
  const warnings: string[] = []

  // Second base64 decode
  const compressedStr = atob(code)
  const compressed = new Uint8Array(compressedStr.length)
  for (let i = 0; i < compressedStr.length; i++) {
    compressed[i] = compressedStr.charCodeAt(i)
  }

  // Gzip decompress
  const firstBase64 = pako.ungzip(compressed, { to: 'string' })

  // First base64 decode
  const bytesStr = atob(firstBase64)
  const bytes = new Uint8Array(bytesStr.length)
  for (let i = 0; i < bytesStr.length; i++) {
    bytes[i] = bytesStr.charCodeAt(i)
  }

  // Convert to binary
  const binary = bytesToBinary(bytes)

  // Parse equipment and deployment order
  const equipment: Record<string, SinnerEquipment> = {}
  const deploymentMap: { position: number; sinnerIndex: number }[] = []

  for (let sinnerIndex = 0; sinnerIndex < 12; sinnerIndex++) {
    const sinnerName = SINNERS[sinnerIndex]
    const offset = sinnerIndex * BITS_PER_SINNER

    // Identity ID (8 bits)
    const identityIndex = fromBinary(binary.slice(offset, offset + 8))

    // Deployment order (4 bits)
    const deploymentPosition = fromBinary(binary.slice(offset + 8, offset + 12))
    if (deploymentPosition > 0) {
      deploymentMap.push({ position: deploymentPosition, sinnerIndex })
    }

    // Reconstruct identity
    const identityId = reconstructIdentityId(sinnerIndex, identityIndex)

    // Validate identity
    if (identityIndex > 0 && !identitySpecMap[identityId]) {
      warnings.push(`Invalid identity ID ${identityId} for ${sinnerName}`)
    }

    // Parse EGO slots
    const egos: SinnerEquipment['egos'] = {}
    let egoOffset = offset + 12

    for (let i = 0; i < EGO_RANK_ORDER.length; i++) {
      const rank = EGO_RANK_ORDER[i]
      const bits = EGO_BIT_LENGTHS[i]
      const egoIndex = fromBinary(binary.slice(egoOffset, egoOffset + bits))
      egoOffset += bits

      if (egoIndex > 0) {
        const egoId = reconstructEgoId(sinnerIndex, egoIndex)

        // Validate EGO
        if (!egoSpecMap[egoId]) {
          warnings.push(`Invalid EGO ID ${egoId} for ${sinnerName}`)
        } else {
          egos[rank] = {
            id: egoId,
            threadspin: 4,
          }
        }
      }
    }

    // Only set equipment if identity is valid
    if (identityIndex > 0 && identitySpecMap[identityId]) {
      equipment[sinnerName] = {
        identity: {
          id: identityId,
          uptie: 4,
          level: 55,
        },
        egos,
      }
    } else if (identityIndex > 0) {
      // Invalid identity, use default
      const defaultIdentityId = reconstructIdentityId(sinnerIndex, 1)
      equipment[sinnerName] = {
        identity: {
          id: defaultIdentityId,
          uptie: 4,
          level: 55,
        },
        egos,
      }
    } else {
      // No identity specified, use default
      const defaultIdentityId = reconstructIdentityId(sinnerIndex, 1)
      const defaultEgoId = reconstructEgoId(sinnerIndex, 1)
      equipment[sinnerName] = {
        identity: {
          id: defaultIdentityId,
          uptie: 4,
          level: 55,
        },
        egos: {
          ZAYIN: {
            id: defaultEgoId,
            threadspin: 4,
          },
        },
      }
    }
  }

  // Sort deployment order by position
  deploymentMap.sort((a, b) => a.position - b.position)
  const deploymentOrder = deploymentMap.map((d) => d.sinnerIndex)

  return {
    equipment,
    deploymentOrder,
    warnings,
  }
}

/**
 * Validate a deck code string
 */
export function validateDeckCode(code: string): ValidationResult {
  try {
    // Second base64 decode
    const compressedStr = atob(code)
    const compressed = new Uint8Array(compressedStr.length)
    for (let i = 0; i < compressedStr.length; i++) {
      compressed[i] = compressedStr.charCodeAt(i)
    }

    // Gzip decompress
    const firstBase64 = pako.ungzip(compressed, { to: 'string' })

    // First base64 decode
    atob(firstBase64)

    return { isValid: true, warnings: [] }
  } catch {
    return { isValid: false, warnings: ['Invalid deck code format'] }
  }
}
