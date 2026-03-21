import fs from 'fs'
import path from 'path'
import { createHash } from 'crypto'
import type { Plugin } from 'vite'
import { hashKey } from './src/lib/hashKey'

const VIRTUAL_MODULE_ID = 'virtual:asset-manifest'
const RESOLVED_VIRTUAL_MODULE_ID = '\0' + VIRTUAL_MODULE_ID

interface HashStaticOptions {
  staticDir: string
}

/** Returns the numeric FNV-1a state (not hex string) for partial hash continuation. */
function partialHash(input: string): number {
  let hash = 0x811c9dc5
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i)
    hash = Math.imul(hash, 0x01000193)
  }
  return hash
}

/** Convert a string to its char code byte array for build-time embedding. */
function toBytes(s: string): number[] {
  return Array.from(s).map(c => c.charCodeAt(0))
}

/**
 * Transform a resolveAsset template/string into either:
 * - An inline resolved path (static calls)
 * - A hash continuation chain (dynamic calls with interpolations)
 */
function transformResolveAsset(
  template: string,
  manifest: Record<string, string>,
  warn: (msg: string) => void
): string {
  // Split template on ${...} interpolations
  const interpRegex = /\$\{([^}]+)\}/g
  const expressions: string[] = []
  let m: RegExpExecArray | null
  while ((m = interpRegex.exec(template)) !== null) {
    expressions.push(m[1])
  }

  // Quasis: the static parts between interpolations
  const quasis = template.split(/\$\{[^}]+\}/)

  if (expressions.length === 0) {
    // Static call: compute full hash, look up manifest, inline result
    const key = template.startsWith('/') ? template.slice(1) : template
    const hashedKey = hashKey(key)
    const resolved = manifest[hashedKey]
    if (resolved) {
      return `"/${resolved}"`
    }
    warn(`Static asset not found in manifest: ${hashedKey} (path: ${key})`)
    return `""`
  }

  // Dynamic call: FNV-1a hash continuation
  // Strip leading / from first quasi (resolveAsset does this at runtime)
  const firstQuasi = quasis[0].startsWith('/') ? quasis[0].slice(1) : quasis[0]

  // Pre-compute hash state for the first quasi (prefix)
  const prefixState = partialHash(firstQuasi)

  // Build nested call from inside out:
  // _r(_hb(_h(_hb(_h(PREFIX, e1), q1_bytes), e2), q2_bytes))
  let expr = `0x${(prefixState >>> 0).toString(16)}`

  for (let i = 0; i < expressions.length; i++) {
    // Hash the expression with _h; coerce to string to match template literal behavior
    expr = `_h(${expr},""+${expressions[i]})`
    // Hash the next quasi (static part) with _hb if it's non-empty
    const nextQuasi = quasis[i + 1]
    if (nextQuasi.length > 0) {
      const bytes = toBytes(nextQuasi)
      expr = `_hb(${expr},[${bytes.join(',')}])`
    }
  }

  return `_r(${expr})`
}

/**
 * Transform a dynamic import path template into a hash continuation expression.
 * Unlike transformResolveAsset, this produces a hex string key (not a manifest lookup).
 * Used by renderChunk to hash glob map lookup templates.
 */
function transformDynamicImportKey(template: string): string {
  const interpRegex = /\$\{([^}]+)\}/g
  const expressions: string[] = []
  let m: RegExpExecArray | null
  while ((m = interpRegex.exec(template)) !== null) {
    expressions.push(m[1])
  }
  const quasis = template.split(/\$\{[^}]+\}/)

  if (expressions.length === 0) {
    // Static path — inline the hash string directly
    return `"${hashKey(template)}"`
  }

  // Build hash continuation chain using chunk-level helpers ($_h, $_hb, $_hk)
  const prefixState = partialHash(quasis[0])
  let expr = `0x${(prefixState >>> 0).toString(16)}`

  for (let i = 0; i < expressions.length; i++) {
    // Coerce to string with ""+expr to match template literal behavior
    expr = `$_h(${expr},""+${expressions[i]})`
    const nextQuasi = quasis[i + 1]
    if (nextQuasi.length > 0) {
      const bytes = toBytes(nextQuasi)
      expr = `$_hb(${expr},[${bytes.join(',')}])`
    }
  }

  return `$_hk(${expr})`
}

/** Runtime helper source: continue FNV-1a hash with a string value. */
const HELPER_H = `function _h(s,v){for(let i=0;i<v.length;i++){s^=v.charCodeAt(i);s=Math.imul(s,0x01000193)}return s}`

/** Runtime helper source: continue FNV-1a hash with a byte array. */
const HELPER_HB = `function _hb(s,b){for(let i=0;i<b.length;i++){s^=b[i];s=Math.imul(s,0x01000193)}return s}`

/** Runtime helper source: look up manifest by final hash state. */
const HELPER_R = `function _r(h){const k=(h>>>0).toString(16).padStart(8,'0');return k in _m?'/'+_m[k]:'/missing-asset'}`

/** Chunk-level helpers for dynamic import key hashing ($ prefix avoids minifier name conflicts). */
const CHUNK_HELPER_H = `var $_h=function(s,v){for(var i=0;i<v.length;i++){s^=v.charCodeAt(i);s=Math.imul(s,0x01000193)}return s};`
const CHUNK_HELPER_HB = `var $_hb=function(s,b){for(var i=0;i<b.length;i++){s^=b[i];s=Math.imul(s,0x01000193)}return s};`
const CHUNK_HELPER_HK = `var $_hk=function(h){return(h>>>0).toString(16).padStart(8,"0")};`

function scanFiles(dir: string, base = ''): string[] {
  const entries = fs.readdirSync(dir, { withFileTypes: true })
  const files: string[] = []
  for (const entry of entries) {
    const rel = base ? `${base}/${entry.name}` : entry.name
    if (entry.isDirectory()) {
      // Skip scripts directory (excluded by another plugin)
      if (entry.name === 'scripts') continue
      files.push(...scanFiles(path.join(dir, entry.name), rel))
    } else {
      files.push(rel)
    }
  }
  return files
}

function buildManifest(staticDir: string): Record<string, string> {
  const manifest: Record<string, string> = {}
  const files = scanFiles(staticDir)

  for (const file of files) {
    // Skip JSON files — they are bundled into JS chunks via dynamic import()
    if (path.extname(file) === '.json') continue

    const filePath = path.join(staticDir, file)
    const content = fs.readFileSync(filePath)
    const hash = createHash('md5').update(content).digest('hex').slice(0, 12)
    const ext = path.extname(file)
    manifest[hashKey(file)] = `a/${hash}${ext}`
  }

  return manifest
}

// Exported for testing
export { transformResolveAsset, transformDynamicImportKey, partialHash, toBytes }

export function hashStaticPlugin(options: HashStaticOptions): Plugin {
  let hasManifest = false

  return {
    name: 'hash-static-assets',

    resolveId(id) {
      if (id === VIRTUAL_MODULE_ID) {
        return RESOLVED_VIRTUAL_MODULE_ID
      }
    },

    load(id) {
      if (id === RESOLVED_VIRTUAL_MODULE_ID) {
        if (this.meta.watchMode) {
          return 'export default {}'
        }
        const manifest = buildManifest(options.staticDir)
        hasManifest = Object.keys(manifest).length > 0
        return `export default ${JSON.stringify(manifest)}`
      }
    },

    transform(code, id) {
      if (this.meta.watchMode) return
      if (!id.endsWith('assetPaths.ts')) return

      const manifest = buildManifest(options.staticDir)

      // Regex to match resolveAsset(`...`), resolveAsset('...'), and resolveAsset("...")
      // Note: esbuild pre-transform converts single quotes to double quotes
      const templateRegex = /resolveAsset\(`([^`]*)`\)/g
      const singleQuoteRegex = /resolveAsset\('([^']*)'\)/g
      const doubleQuoteRegex = /resolveAsset\("([^"]*)"\)/g

      let result = code

      // Process template literal calls
      result = result.replace(templateRegex, (_match, template: string) => {
        return transformResolveAsset(template, manifest, (msg) => this.warn(msg))
      })

      // Process single-quoted string calls
      result = result.replace(singleQuoteRegex, (_match, str: string) => {
        return transformResolveAsset(str, manifest, (msg) => this.warn(msg))
      })

      // Process double-quoted string calls (esbuild converts single to double quotes)
      result = result.replace(doubleQuoteRegex, (_match, str: string) => {
        return transformResolveAsset(str, manifest, (msg) => this.warn(msg))
      })

      // Replace import of resolveAsset with direct manifest import
      // Match both single and double quotes (esbuild normalizes to double)
      result = result.replace(
        /import\s*\{\s*resolveAsset\s*\}\s*from\s*['"]\.\/assetManifest['"]/,
        `import _m from 'virtual:asset-manifest'`
      )

      // Inject runtime helpers after the last import line
      // Find all import statement positions, then insert after the last one
      const importLineRegex = /^import\s.+$/gm
      let lastMatch: RegExpExecArray | null = null
      let im: RegExpExecArray | null
      while ((im = importLineRegex.exec(result)) !== null) {
        lastMatch = im
      }
      if (lastMatch) {
        const insertPos = lastMatch.index + lastMatch[0].length
        result = result.slice(0, insertPos) +
          `\n\n${HELPER_H}\n${HELPER_HB}\n${HELPER_R}\n` +
          result.slice(insertPos)
      }

      return { code: result, map: null }
    },

    renderChunk(code) {
      if (!code.includes('../../../static/')) return

      let result = code

      // 1. Replace map key strings: "../../../static/..." → "abc12345"
      result = result.replace(
        new RegExp(`"\\.\\.\/\\.\\.\/\\.\\.\/static\/[^"]+\\.json"`, 'g'),
        (match) => {
          const p = match.slice(1, -1)
          return `"${hashKey(p)}"`
        }
      )

      // 2. Replace template literal lookups: `../../../static/.../${var}.json` → $_hk(...)
      result = result.replace(
        /`\.\.\/\.\.\/\.\.\/static\/[^`]+`/g,
        (match) => {
          const template = match.slice(1, -1)
          return transformDynamicImportKey(template)
        }
      )

      // 3. Inject chunk-level hash helpers if replacements were made
      if (result !== code) {
        result = CHUNK_HELPER_H + CHUNK_HELPER_HB + CHUNK_HELPER_HK + result
      }

      return { code: result, map: null }
    },

    closeBundle() {
      if (!hasManifest) return

      const distDir = path.resolve('dist')
      const outputDir = path.join(distDir, 'a')
      fs.mkdirSync(outputDir, { recursive: true })

      // Re-scan and copy: no original paths stored in manifest
      const files = scanFiles(options.staticDir)
      for (const file of files) {
        if (path.extname(file) === '.json') continue

        const src = path.join(distDir, file)
        if (!fs.existsSync(src)) continue

        const content = fs.readFileSync(path.join(options.staticDir, file))
        const hash = createHash('md5').update(content).digest('hex').slice(0, 12)
        const ext = path.extname(file)
        const dest = path.join(distDir, `a/${hash}${ext}`)
        fs.copyFileSync(src, dest)
      }

      // Delete all top-level directories that came from staticDir
      const staticTopDirs = fs.readdirSync(options.staticDir, { withFileTypes: true })
        .filter(e => e.isDirectory() && e.name !== 'scripts')
        .map(e => e.name)

      for (const dir of staticTopDirs) {
        const dirPath = path.join(distDir, dir)
        if (fs.existsSync(dirPath) && fs.statSync(dirPath).isDirectory()) {
          fs.rmSync(dirPath, { recursive: true, force: true })
        }
      }
    },
  }
}
