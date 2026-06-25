/**
 * UUID v4 generation backed by CSPRNG only.
 *
 * Resolution order:
 *   1. crypto.randomUUID()                — preferred; available in secure contexts
 *   2. crypto.getRandomValues()           — fallback; ubiquitous, available outside secure contexts
 *   3. throw                              — refuse to produce a weak ID
 *
 * Math.random() is never used. Weak IDs caused cross-user collisions on planner upsert
 * (server-side ID-collision warns) before this module existed.
 *
 * @see RFC 4122 §4.4 for the v4 bit layout this fallback constructs by hand.
 */

/**
 * Build a v4 UUID string from 16 random bytes per RFC 4122 §4.4.
 * Byte 6 high nibble = 0100 (version 4); byte 8 high 2 bits = 10 (variant).
 */
function formatV4(bytes: Uint8Array): string {
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80

  const hex: string[] = []
  for (let i = 0; i < 16; i++) {
    hex.push(bytes[i].toString(16).padStart(2, '0'))
  }
  return (
    hex.slice(0, 4).join('') + '-' +
    hex.slice(4, 6).join('') + '-' +
    hex.slice(6, 8).join('') + '-' +
    hex.slice(8, 10).join('') + '-' +
    hex.slice(10, 16).join('')
  )
}

/**
 * Generate a cryptographically random UUID v4.
 *
 * @throws if the environment provides neither crypto.randomUUID nor crypto.getRandomValues
 */
export function generateUUID(): string {
  if (typeof crypto !== 'undefined') {
    if (typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID()
    }
    if (typeof crypto.getRandomValues === 'function') {
      return formatV4(crypto.getRandomValues(new Uint8Array(16)))
    }
  }
  throw new Error('UUID generation requires the Web Crypto API (crypto.randomUUID or crypto.getRandomValues).')
}
