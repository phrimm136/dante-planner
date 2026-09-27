import { gzipSync } from 'node:zlib'
import { z } from 'zod'

const GZIP_LEVEL = 9

const SCRIPT_TAG = /<script\b([^>]*)>/g
const MODULE_TYPE = /\btype\s*=\s*["']module["']/
const SRC_ATTR = /\bsrc\s*=\s*["']([^"']+)["']/

export const bundleBudgetSchema = z.object({ entryGzipBytes: z.number().int().positive() }).strict()

export interface EntryBudgetResult {
  file: string
  gzipBytes: number
  budget: number
  ok: boolean
}

function entryScriptSrc(indexHtml: string): string {
  const sources = [...indexHtml.matchAll(SCRIPT_TAG)]
    .map(([, attrs]) => attrs)
    .filter((attrs) => MODULE_TYPE.test(attrs))
    .map((attrs) => attrs.match(SRC_ATTR)?.[1])
    .filter((src): src is string => src !== undefined)
  if (sources.length === 0) {
    throw new Error('index.html has no <script type="module" src=…> entry')
  }
  if (sources.length > 1) {
    throw new Error(
      `index.html has ${sources.length} module scripts, expected exactly one entry: ${sources.join(', ')}`,
    )
  }
  return sources[0]
}

export function checkEntryBudget(
  indexHtml: string,
  readFile: (src: string) => Uint8Array,
  budget: number,
): EntryBudgetResult {
  const file = entryScriptSrc(indexHtml)
  const gzipBytes = gzipSync(readFile(file), { level: GZIP_LEVEL }).length
  return { file, gzipBytes, budget, ok: gzipBytes <= budget }
}
