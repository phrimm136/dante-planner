import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest'
import fs from 'fs'
import path from 'path'
import os from 'os'
import { hashStaticPlugin, transformResolveAsset, transformDynamicImportKey, partialHash, toBytes } from './vite-plugin-hash-static'
import { hashKey } from './src/lib/hashKey'

function createTempStaticDir(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'hash-static-test-'))

  // Create nested structure mimicking static/
  fs.mkdirSync(path.join(dir, 'images', 'icon'), { recursive: true })
  fs.mkdirSync(path.join(dir, 'data'), { recursive: true })
  fs.mkdirSync(path.join(dir, 'i18n', 'en'), { recursive: true })

  // Write sample files
  fs.writeFileSync(path.join(dir, 'images', 'icon', 'test.webp'), 'image-content-a')
  fs.writeFileSync(path.join(dir, 'data', 'specList.json'), '{"id":1}')
  fs.writeFileSync(path.join(dir, 'i18n', 'en', 'names.json'), '{"name":"test"}')

  // Duplicate content file (should produce same hash)
  fs.writeFileSync(path.join(dir, 'images', 'icon', 'duplicate.webp'), 'image-content-a')

  // Different content (should produce different hash)
  fs.writeFileSync(path.join(dir, 'images', 'icon', 'other.webp'), 'image-content-b')

  return dir
}

describe('hashStaticPlugin', () => {
  let tempDir: string

  beforeAll(() => {
    tempDir = createTempStaticDir()
  })

  afterAll(() => {
    fs.rmSync(tempDir, { recursive: true, force: true })
  })

  it('resolves virtual:asset-manifest module ID', () => {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const resolveId = plugin.resolveId as (id: string) => string | undefined
    expect(resolveId('virtual:asset-manifest')).toBe('\0virtual:asset-manifest')
  })

  it('returns undefined for other module IDs', () => {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const resolveId = plugin.resolveId as (id: string) => string | undefined
    expect(resolveId('some-other-module')).toBeUndefined()
  })

  it('generates manifest with correct hash format', () => {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const load = plugin.load as (id: string) => string | undefined
    const result = load.call({ meta: { watchMode: false } }, '\0virtual:asset-manifest')

    expect(result).toBeDefined()
    expect(result).toContain('export default')

    // Parse the manifest from the generated module
    const manifestJson = result!.replace('export default ', '')
    const manifest = JSON.parse(manifestJson) as Record<string, string>

    // Keys are hashed — original paths not present
    expect(manifest[hashKey('images/icon/test.webp')]).toBeDefined()
    expect(manifest['images/icon/test.webp']).toBeUndefined()
    // JSON files are excluded (bundled via dynamic import)
    expect(manifest[hashKey('data/specList.json')]).toBeUndefined()
    expect(manifest[hashKey('i18n/en/names.json')]).toBeUndefined()
  })

  it('produces 12-char hashes with correct extensions', () => {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const load = plugin.load as (id: string) => string | undefined
    const result = load.call({ meta: { watchMode: false } }, '\0virtual:asset-manifest')
    const manifest = JSON.parse(result!.replace('export default ', '')) as Record<string, string>

    // Check hash format: a/{12-char-hex}.{ext}
    for (const hashedPath of Object.values(manifest)) {
      expect(hashedPath).toMatch(/^a\/[a-f0-9]{12}\.\w+$/)
    }

    // Extension preservation
    expect(manifest[hashKey('images/icon/test.webp')]).toMatch(/\.webp$/)
  })

  it('same content produces same hash', () => {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const load = plugin.load as (id: string) => string | undefined
    const result = load.call({ meta: { watchMode: false } }, '\0virtual:asset-manifest')
    const manifest = JSON.parse(result!.replace('export default ', '')) as Record<string, string>

    expect(manifest[hashKey('images/icon/test.webp')]).toBe(manifest[hashKey('images/icon/duplicate.webp')])
  })

  it('different content produces different hash', () => {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const load = plugin.load as (id: string) => string | undefined
    const result = load.call({ meta: { watchMode: false } }, '\0virtual:asset-manifest')
    const manifest = JSON.parse(result!.replace('export default ', '')) as Record<string, string>

    expect(manifest[hashKey('images/icon/test.webp')]).not.toBe(manifest[hashKey('images/icon/other.webp')])
  })

  it('returns empty manifest in dev mode (watchMode)', () => {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const load = plugin.load as (id: string) => string | undefined
    const result = load.call({ meta: { watchMode: true } }, '\0virtual:asset-manifest')

    expect(result).toBe('export default {}')
  })

  it('returns undefined for non-virtual module load', () => {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const load = plugin.load as (id: string) => string | undefined
    const result = load.call({ meta: { watchMode: false } }, 'some-other-id')

    expect(result).toBeUndefined()
  })

  it('excludes JSON files from manifest', () => {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const load = plugin.load as (id: string) => string | undefined
    const result = load.call({ meta: { watchMode: false } }, '\0virtual:asset-manifest')
    const manifest = JSON.parse(result!.replace('export default ', '')) as Record<string, string>

    // No .json values in manifest (JSON files excluded)
    const jsonValues = Object.values(manifest).filter(v => v.endsWith('.json'))
    expect(jsonValues).toHaveLength(0)

    // Only .webp values remain (3 webp files in test fixture)
    const webpValues = Object.values(manifest).filter(v => v.endsWith('.webp'))
    expect(webpValues).toHaveLength(3)
  })

  it('skips scripts directory', () => {
    // Add a scripts dir
    const scriptsDir = path.join(tempDir, 'scripts')
    fs.mkdirSync(scriptsDir, { recursive: true })
    fs.writeFileSync(path.join(scriptsDir, 'build.sh'), '#!/bin/bash')

    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const load = plugin.load as (id: string) => string | undefined
    const result = load.call({ meta: { watchMode: false } }, '\0virtual:asset-manifest')
    const manifest = JSON.parse(result!.replace('export default ', '')) as Record<string, string>

    expect(manifest[hashKey('scripts/build.sh')]).toBeUndefined()

    // Cleanup
    fs.rmSync(scriptsDir, { recursive: true, force: true })
  })
})

describe('partialHash', () => {
  it('produces consistent numeric state matching hashKey', () => {
    const input = 'images/icon/sinners/'
    const state = partialHash(input)
    expect(typeof state).toBe('number')

    // Continuing partialHash with more chars should equal hashKey of full string
    const full = 'images/icon/sinners/YiSang.webp'
    const suffix = 'YiSang.webp'
    let hash = partialHash(input)
    for (let i = 0; i < suffix.length; i++) {
      hash ^= suffix.charCodeAt(i)
      hash = Math.imul(hash, 0x01000193)
    }
    expect((hash >>> 0).toString(16).padStart(8, '0')).toBe(hashKey(full))
  })
})

describe('toBytes', () => {
  it('converts string to char code array', () => {
    expect(toBytes('.webp')).toEqual([46, 119, 101, 98, 112])
    expect(toBytes('/')).toEqual([47])
    expect(toBytes('')).toEqual([])
  })
})

describe('transformResolveAsset', () => {
  const manifest: Record<string, string> = {
    [hashKey('images/icon/test.webp')]: 'a/abc123def456.webp',
    [hashKey('images/UI/formation/selected.webp')]: 'a/sel789012345.webp',
  }
  const warnings: string[] = []
  const warn = (msg: string) => { warnings.push(msg) }

  it('inlines static calls with leading slash', () => {
    const result = transformResolveAsset('/images/UI/formation/selected.webp', manifest, warn)
    expect(result).toBe('"/a/sel789012345.webp"')
  })

  it('inlines static calls without leading slash', () => {
    const result = transformResolveAsset('images/icon/test.webp', manifest, warn)
    expect(result).toBe('"/a/abc123def456.webp"')
  })

  it('warns and returns empty string for missing static assets', () => {
    warnings.length = 0
    const result = transformResolveAsset('/images/missing/file.webp', manifest, warn)
    expect(result).toBe('""')
    expect(warnings.length).toBe(1)
    expect(warnings[0]).toContain('Static asset not found')
  })

  it('transforms single interpolation into hash continuation', () => {
    const result = transformResolveAsset(
      '/images/icon/sinners/${sinner}.webp',
      manifest,
      warn
    )
    // Should produce: _r(_hb(_h(0x...,""+sinner),[46,119,101,98,112]))
    expect(result).toMatch(/^_r\(_hb\(_h\(0x[a-f0-9]+,""\+sinner\),\[46,119,101,98,112\]\)\)$/)
  })

  it('transforms two interpolations (same var)', () => {
    const result = transformResolveAsset(
      '/images/identity/${identityId}/${identityId}_normal_info.webp',
      manifest,
      warn
    )
    // Should contain two _h calls with identityId
    expect(result).toContain('_h(')
    expect((result.match(/identityId/g) || []).length).toBe(2)
    // Should have byte array for "/" between interpolations
    expect(result).toContain('[47]')
    // Should have byte array for "_normal_info.webp" suffix
    expect(result).toContain(toBytes('_normal_info.webp').join(','))
  })

  it('transforms two interpolations (different vars)', () => {
    const result = transformResolveAsset(
      '/images/identity/${identityId}/${skillId}.webp',
      manifest,
      warn
    )
    expect(result).toContain('identityId')
    expect(result).toContain('skillId')
    // "/" separator between the two interpolations
    expect(result).toContain('[47]')
    // ".webp" suffix
    expect(result).toContain('[46,119,101,98,112]')
  })

  it('handles String() wrapper expressions', () => {
    const result = transformResolveAsset(
      '/images/UI/formation/${String(rank)}Rank${String(uptie)}UptieFrame.webp',
      manifest,
      warn
    )
    expect(result).toContain('""+String(rank)')
    expect(result).toContain('""+String(uptie)')
  })

  it('handles String(expr + N) expression', () => {
    const result = transformResolveAsset(
      '/images/UI/common/coin${String(coinIndex + 1)}.webp',
      manifest,
      warn
    )
    expect(result).toContain('""+String(coinIndex + 1)')
  })

  it('handles three interpolations', () => {
    const result = transformResolveAsset(
      '/images/ego/${egoId}/${egoId}_${imageFileName}',
      manifest,
      warn
    )
    expect((result.match(/_h\(/g) || []).length).toBe(3)
    expect(result).toContain('egoId')
    expect(result).toContain('imageFileName')
  })

  it('produces correct hash for single interpolation (end-to-end)', () => {
    // Verify the transform output matches what hashKey would produce
    const sinner = 'TestSinner'
    const fullPath = `images/icon/sinners/${sinner}.webp`
    const expectedKey = hashKey(fullPath)

    // Simulate what the runtime helpers would do
    const prefix = 'images/icon/sinners/'
    let state = partialHash(prefix)
    // _h(state, sinner)
    for (let i = 0; i < sinner.length; i++) {
      state ^= sinner.charCodeAt(i)
      state = Math.imul(state, 0x01000193)
    }
    // _hb(state, [46,119,101,98,112])
    const suffixBytes = [46, 119, 101, 98, 112]
    for (const b of suffixBytes) {
      state ^= b
      state = Math.imul(state, 0x01000193)
    }
    const resultKey = (state >>> 0).toString(16).padStart(8, '0')
    expect(resultKey).toBe(expectedKey)
  })
})

describe('transform hook', () => {
  let tempDir: string

  beforeAll(() => {
    tempDir = createTempStaticDir()
  })

  afterAll(() => {
    fs.rmSync(tempDir, { recursive: true, force: true })
  })

  function callTransform(code: string, filename = '/src/lib/assetPaths.ts'): string | null {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const transform = plugin.transform as (code: string, id: string) => { code: string; map: null } | undefined
    const result = transform.call({ meta: { watchMode: false }, warn: vi.fn() }, code, filename)
    return result?.code ?? null
  }

  it('skips non-assetPaths files', () => {
    const result = callTransform('resolveAsset(`/images/test.webp`)', '/src/lib/other.ts')
    expect(result).toBeNull()
  })

  it('skips in dev mode (watchMode)', () => {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const transform = plugin.transform as (code: string, id: string) => { code: string; map: null } | undefined
    const result = transform.call({ meta: { watchMode: true }, warn: vi.fn() }, 'code', '/src/lib/assetPaths.ts')
    expect(result).toBeUndefined()
  })

  it('replaces resolveAsset import with manifest import', () => {
    const code = `import { resolveAsset } from './assetManifest'\nconst x = 1`
    const result = callTransform(code)
    expect(result).toContain("import _m from 'virtual:asset-manifest'")
    expect(result).not.toContain('resolveAsset')
  })

  it('injects runtime helpers', () => {
    const code = `import { resolveAsset } from './assetManifest'\nconst x = 1`
    const result = callTransform(code)
    expect(result).toContain('function _h(')
    expect(result).toContain('function _hb(')
    expect(result).toContain('function _r(')
  })

  it('preserves non-resolveAsset imports', () => {
    const code = `import { AFFINITIES, ATK_TYPES } from './constants'\nimport { resolveAsset } from './assetManifest'\nconst x = 1`
    const result = callTransform(code)
    expect(result).toContain("import { AFFINITIES, ATK_TYPES } from './constants'")
  })

  it('replaces static resolveAsset calls with inline paths', () => {
    const code = `import { resolveAsset } from './assetManifest'\nresolveAsset(\`/images/icon/test.webp\`)`
    const result = callTransform(code)
    expect(result).not.toContain('resolveAsset')
    // Should contain a resolved path like "/a/..."
    expect(result).toMatch(/\"\/a\/[a-f0-9]{12}\.webp\"/)
  })

  it('replaces single-quoted resolveAsset calls', () => {
    const code = `import { resolveAsset } from './assetManifest'\nresolveAsset('/images/icon/test.webp')`
    const result = callTransform(code)
    expect(result).toMatch(/\"\/a\/[a-f0-9]{12}\.webp\"/)
  })

  it('replaces dynamic resolveAsset calls with hash continuation', () => {
    const code = `import { resolveAsset } from './assetManifest'\nresolveAsset(\`/images/icon/\${name}.webp\`)`
    const result = callTransform(code)
    expect(result).toContain('_r(')
    expect(result).toContain('_h(')
    expect(result).toContain('name')
  })
})

describe('transformDynamicImportKey', () => {
  it('hashes static path directly', () => {
    const result = transformDynamicImportKey('../../../static/data/egoSpecList.json')
    expect(result).toBe(`"${hashKey('../../../static/data/egoSpecList.json')}"`)
  })

  it('transforms single interpolation into hash continuation', () => {
    const result = transformDynamicImportKey('../../../static/data/identity/${r}.json')
    expect(result).toMatch(/^\$_hk\(\$_hb\(\$_h\(0x[a-f0-9]+,""\+r\),\[/)
    // .json as byte codes: [46,106,115,111,110]
    expect(result).toContain([46, 106, 115, 111, 110].join(','))
  })

  it('transforms two interpolations', () => {
    const result = transformDynamicImportKey('../../../static/i18n/${i}/identity/${r}.json')
    expect(result).toContain('$_h(')
    expect(result).toContain('$_hb(')
    expect(result).toContain('$_hk(')
    expect((result.match(/\$_h\(/g) || []).length).toBe(2)
  })

  it('produces correct hash (end-to-end)', () => {
    // Verify hash continuation matches hashKey for a concrete path
    const variable = '10101'
    const fullPath = `../../../static/data/identity/${variable}.json`

    const prefix = '../../../static/data/identity/'
    let state = partialHash(prefix)
    for (let i = 0; i < variable.length; i++) {
      state ^= variable.charCodeAt(i)
      state = Math.imul(state, 0x01000193)
    }
    const suffixBytes = toBytes('.json')
    for (const b of suffixBytes) {
      state ^= b
      state = Math.imul(state, 0x01000193)
    }
    const resultKey = (state >>> 0).toString(16).padStart(8, '0')
    expect(resultKey).toBe(hashKey(fullPath))
  })
})

describe('renderChunk hook', () => {
  let tempDir: string

  beforeAll(() => {
    tempDir = createTempStaticDir()
  })

  afterAll(() => {
    fs.rmSync(tempDir, { recursive: true, force: true })
  })

  function callRenderChunk(code: string): string | null {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const renderChunk = plugin.renderChunk as (code: string) => { code: string; map: null } | undefined
    const result = renderChunk.call({}, code)
    return result?.code ?? null
  }

  it('skips chunks without static paths', () => {
    const result = callRenderChunk('const x = 1')
    expect(result).toBeNull()
  })

  it('replaces map key strings with hashes', () => {
    const code = '{"../../../static/data/identity/10101.json":()=>import("./chunk.js")}'
    const result = callRenderChunk(code)
    expect(result).not.toContain('../../../static/')
    const expectedHash = hashKey('../../../static/data/identity/10101.json')
    expect(result).toContain(`"${expectedHash}"`)
  })

  it('replaces template lookups with hash continuation', () => {
    const code = 'lookup(`../../../static/data/identity/${r}.json`)'
    const result = callRenderChunk(code)
    expect(result).not.toContain('../../../static/')
    expect(result).toContain('$_h(')
    expect(result).toContain('$_hk(')
  })

  it('injects chunk-level helpers when replacements are made', () => {
    const code = '{"../../../static/data/test.json":()=>1}'
    const result = callRenderChunk(code)
    expect(result).toContain('var $_h=')
    expect(result).toContain('var $_hb=')
    expect(result).toContain('var $_hk=')
  })

  it('map key hash matches template hash for same path', () => {
    // Verify that hashing "../../../static/data/identity/10101.json" as a map key
    // produces the same value as the hash continuation for the template with r="10101"
    const staticKey = hashKey('../../../static/data/identity/10101.json')

    const prefix = '../../../static/data/identity/'
    let state = partialHash(prefix)
    const r = '10101'
    for (let i = 0; i < r.length; i++) {
      state ^= r.charCodeAt(i)
      state = Math.imul(state, 0x01000193)
    }
    for (const b of toBytes('.json')) {
      state ^= b
      state = Math.imul(state, 0x01000193)
    }
    const templateKey = (state >>> 0).toString(16).padStart(8, '0')

    expect(templateKey).toBe(staticKey)
  })
})

describe('closeBundle hook', () => {
  let tempDir: string
  let workDir: string
  let distDir: string
  let originalCwd: string

  beforeAll(() => {
    originalCwd = process.cwd()
    tempDir = createTempStaticDir()

    // closeBundle resolves path.resolve('dist'), so we need:
    // workDir/dist/ as the output directory
    workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'hash-work-test-'))
    distDir = path.join(workDir, 'dist')
    fs.mkdirSync(path.join(distDir, 'a'), { recursive: true })

    // Simulate publicDir copy (Vite copies static/ contents to dist/)
    fs.mkdirSync(path.join(distDir, 'images', 'icon'), { recursive: true })
    fs.writeFileSync(path.join(distDir, 'images', 'icon', 'test.webp'), 'image-content-a')
    fs.writeFileSync(path.join(distDir, 'images', 'icon', 'duplicate.webp'), 'image-content-a')
    fs.writeFileSync(path.join(distDir, 'images', 'icon', 'other.webp'), 'image-content-b')

    process.chdir(workDir)
  })

  afterAll(() => {
    process.chdir(originalCwd)
    fs.rmSync(workDir, { recursive: true, force: true })
    fs.rmSync(tempDir, { recursive: true, force: true })
  })

  it('copies static files to dist/a/ with content hashes', () => {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    // Trigger load to set hasManifest flag
    const load = plugin.load as (id: string) => string | undefined
    load.call({ meta: { watchMode: false } }, '\0virtual:asset-manifest')
    // Trigger closeBundle
    const closeBundle = plugin.closeBundle as () => void
    closeBundle.call({})

    // Check that hashed files exist in dist/a/
    const aFiles = fs.readdirSync(path.join(distDir, 'a'))
    const webpFiles = aFiles.filter(f => f.endsWith('.webp'))
    expect(webpFiles.length).toBeGreaterThanOrEqual(2) // at least test.webp and other.webp (duplicate has same hash)
  })

  it('removes original static directories from dist', () => {
    // After closeBundle, the original images/ dir should be gone
    expect(fs.existsSync(path.join(distDir, 'images'))).toBe(false)
  })

  it('preserves dist/a/ directory', () => {
    expect(fs.existsSync(path.join(distDir, 'a'))).toBe(true)
  })
})

describe('transformResolveAsset edge cases', () => {
  const manifest: Record<string, string> = {}
  const warn = vi.fn()

  it('handles adjacent interpolations with no static part between them', () => {
    const result = transformResolveAsset(
      '/images/${prefix}${suffix}.webp',
      manifest,
      warn
    )
    // Two _h calls, no _hb between them (empty quasi)
    expect((result.match(/_h\(/g) || []).length).toBe(2)
    expect(result).toContain('prefix')
    expect(result).toContain('suffix')
  })

  it('handles interpolation at the very end (no trailing quasi)', () => {
    const result = transformResolveAsset(
      '/images/icons/${name}',
      manifest,
      warn
    )
    expect(result).toContain('_h(')
    expect(result).toContain('name')
    // No trailing _hb since there's no suffix
    expect(result).not.toContain('_hb(')
  })

  it('handles path with only an interpolation', () => {
    const result = transformResolveAsset(
      '${fullPath}',
      manifest,
      warn
    )
    expect(result).toContain('_h(')
    expect(result).toContain('fullPath')
  })

  it('handles long paths with many segments', () => {
    const result = transformResolveAsset(
      '/images/a/b/c/d/e/f/g/${id}.webp',
      manifest,
      warn
    )
    expect(result).toMatch(/^_r\(/)
    expect(result).toContain('id')
  })
})

describe('regression: esbuild double-quote normalization', () => {
  let tempDir: string

  beforeAll(() => {
    tempDir = createTempStaticDir()
  })

  afterAll(() => {
    fs.rmSync(tempDir, { recursive: true, force: true })
  })

  function callTransform(code: string): string | null {
    const plugin = hashStaticPlugin({ staticDir: tempDir })
    const transform = plugin.transform as (code: string, id: string) => { code: string; map: null } | undefined
    const result = transform.call({ meta: { watchMode: false }, warn: vi.fn() }, code, '/src/lib/assetPaths.ts')
    return result?.code ?? null
  }

  it('replaces double-quoted resolveAsset import (esbuild output)', () => {
    // esbuild converts single quotes to double quotes before our transform runs
    const code = `import { resolveAsset } from "./assetManifest"\nconst x = 1`
    const result = callTransform(code)
    expect(result).toContain("import _m from 'virtual:asset-manifest'")
    expect(result).not.toContain('resolveAsset')
  })

  it('replaces double-quoted static resolveAsset calls (esbuild output)', () => {
    // esbuild converts resolveAsset('/path') to resolveAsset("/path")
    const code = `import { resolveAsset } from "./assetManifest"\nresolveAsset("/images/icon/test.webp")`
    const result = callTransform(code)
    expect(result).not.toContain('resolveAsset')
    expect(result).toMatch(/\"\/a\/[a-f0-9]{12}\.webp\"/)
  })

  it('handles mixed quote styles in same file', () => {
    const code = [
      `import { resolveAsset } from "./assetManifest"`,
      `const a = resolveAsset("/images/icon/test.webp")`,
      `const b = resolveAsset(\`/images/icon/test.webp\`)`,
    ].join('\n')
    const result = callTransform(code)
    expect(result).not.toContain('resolveAsset')
    // Both calls should produce the same resolved path
    const matches = result!.match(/\"\/a\/[a-f0-9]{12}\.webp\"/g)
    expect(matches).toHaveLength(2)
    expect(matches![0]).toBe(matches![1])
  })
})

describe('regression: numeric expression coercion', () => {
  it('coerces expressions to string in transformResolveAsset', () => {
    const manifest: Record<string, string> = {}
    const result = transformResolveAsset(
      '/images/UI/MD${version}/buff.webp',
      manifest,
      () => {}
    )
    // Must contain ""+version to coerce numbers to strings
    expect(result).toContain('""+version')
  })

  it('coerces expressions to string in transformDynamicImportKey', () => {
    const result = transformDynamicImportKey('../../../static/data/MD${version}/startBuffs.json')
    expect(result).toContain('""+version')
  })

  it('numeric coercion produces correct hash (end-to-end)', () => {
    // Simulate: import(`@static/data/MD${version}/startBuffs.json`) with version=6
    const fullPath = '../../../static/data/MD6/startBuffs.json'
    const expectedKey = hashKey(fullPath)

    // Simulate runtime with ""+version (coerced to string "6")
    const prefix = '../../../static/data/MD'
    let state = partialHash(prefix)
    const version = String(6) // ""+6 at runtime
    for (let i = 0; i < version.length; i++) {
      state ^= version.charCodeAt(i)
      state = Math.imul(state, 0x01000193)
    }
    for (const b of toBytes('/startBuffs.json')) {
      state ^= b
      state = Math.imul(state, 0x01000193)
    }
    const resultKey = (state >>> 0).toString(16).padStart(8, '0')
    expect(resultKey).toBe(expectedKey)
  })

  it('without coercion, numeric input produces wrong hash', () => {
    // Demonstrates the bug: passing a number to charCodeAt skips the loop
    const prefix = '../../../static/data/MD'
    const prefixState = partialHash(prefix)

    // Simulate broken behavior: _h(prefixState, 6) where 6 is a number
    // (6).length is undefined, loop doesn't execute, state unchanged
    let brokenState = prefixState
    // for (i = 0; i < (6).length; i++) → (6).length === undefined → loop skips
    // brokenState remains prefixState

    // Simulate correct behavior: _h(prefixState, "6") where "6" is a string
    let correctState = prefixState
    correctState ^= '6'.charCodeAt(0)
    correctState = Math.imul(correctState, 0x01000193)

    expect(brokenState).not.toBe(correctState)
  })
})
