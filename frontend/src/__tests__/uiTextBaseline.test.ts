/// <reference types="node" />
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { describe, it, expect } from 'vitest'

const FRONTEND_ROOT = resolve(dirname(new URL(import.meta.url).pathname), '../..')
const BASELINE_PATH = join(FRONTEND_ROOT, 'lint/ui-text-baseline.json')

function orphanKeysError(
  keys: readonly string[],
  exists: (relativePath: string) => boolean,
): string | null {
  const missing = keys.filter((key) => !exists(key))
  return missing.length === 0 ? null : `baseline keys name missing files: ${missing.join(', ')}`
}

const existsUnderFrontend = (relativePath: string) => existsSync(join(FRONTEND_ROOT, relativePath))

describe('ui-text baseline orphan guard', () => {
  it('names a key whose file is missing', () => {
    expect(
      orphanKeysError(['src/lib/router.tsx', 'src/pages/gone/Gone.tsx'], existsUnderFrontend),
    ).toBe('baseline keys name missing files: src/pages/gone/Gone.tsx')
  })

  it('finds every real baseline key on disk', () => {
    const baseline = JSON.parse(readFileSync(BASELINE_PATH, 'utf8')) as Record<string, unknown>

    expect(orphanKeysError(Object.keys(baseline), existsUnderFrontend)).toBeNull()
  })
})
