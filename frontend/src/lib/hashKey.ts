/**
 * FNV-1a hash for manifest key obfuscation.
 * Used at build time (Node) and runtime (browser) to hide original asset paths.
 */
export function hashKey(input: string): string {
  let hash = 0x811c9dc5
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i)
    hash = Math.imul(hash, 0x01000193)
  }
  // Convert to unsigned 32-bit, then to 8-char hex
  return (hash >>> 0).toString(16).padStart(8, '0')
}
