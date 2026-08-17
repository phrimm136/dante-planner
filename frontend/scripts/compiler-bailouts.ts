/**
 * React Compiler bailout gate.
 *
 * Compiles every shipped source file with babel-plugin-react-compiler and
 * fails when a file bails out that the allowlist does not already name.
 * Remove entries from the allowlist as files are fixed; the gate blocks
 * everything once the allowlist is empty.
 */

import { createRequire } from 'node:module'
import { readFile, readdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = path.resolve(SCRIPT_DIR, '..')
const SRC_ROOT = path.join(FRONTEND_ROOT, 'src')
const ALLOWLIST_PATH = path.join(SCRIPT_DIR, 'compiler-bailouts.allowlist.json')

const SOURCE_EXTENSIONS = ['.ts', '.tsx']
const TEST_FILE_PATTERN = /\.test\.tsx?$/
const TEST_DIR_NAME = '__tests__'

/**
 * babel-plugin-react-compiler does not support Babel 8, and under it reports
 * an order of magnitude more bailouts than it does under Babel 7. The build
 * feeds the compiler through @rolldown/plugin-babel, which carries its own
 * nested Babel 7; resolving from there keeps this gate on the same Babel the
 * build uses rather than the hoisted Babel 8 devDependency.
 */
const BABEL_MAJOR = '7'

const frontendRequire = createRequire(path.join(FRONTEND_ROOT, 'package.json'))
const rolldownBabelRequire = createRequire(frontendRequire.resolve('@rolldown/plugin-babel'))

const babel = rolldownBabelRequire('@babel/core') as typeof import('@babel/core')
const reactCompiler = frontendRequire('babel-plugin-react-compiler')

if (!babel.version.startsWith(`${BABEL_MAJOR}.`)) {
  console.error(
    `Resolved @babel/core ${babel.version} via @rolldown/plugin-babel, expected ${BABEL_MAJOR}.x.`,
  )
  process.exit(1)
}

interface CompilerEvent {
  kind: string
  fnLoc?: { start?: { line?: number } } | null
  detail?: unknown
  reason?: string
}

const BAILOUT_EVENT_KINDS = new Set(['CompileError', 'CompileSkip', 'PipelineError'])

async function collectSourceFiles(dir: string): Promise<string[]> {
  const entries = await readdir(dir, { withFileTypes: true })
  const files: string[] = []

  for (const entry of entries) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === TEST_DIR_NAME) continue
      files.push(...(await collectSourceFiles(full)))
      continue
    }
    if (!SOURCE_EXTENSIONS.includes(path.extname(entry.name))) continue
    if (TEST_FILE_PATTERN.test(entry.name)) continue
    files.push(full)
  }

  return files
}

function describeEvent(event: CompilerEvent): string {
  const line = event.fnLoc?.start?.line
  const detail = event.detail
  const message =
    typeof detail === 'string'
      ? detail
      : ((detail as { reason?: string; description?: string } | undefined)?.reason ??
        (detail as { description?: string } | undefined)?.description ??
        event.reason ??
        '')
  return `${event.kind}${line ? ` (line ${line})` : ''}${message ? `: ${message}` : ''}`
}

async function findBailouts(file: string): Promise<string[]> {
  const source = await readFile(file, 'utf8')
  const bailouts: string[] = []

  const logger = {
    logEvent(_filename: string | null, event: CompilerEvent) {
      if (BAILOUT_EVENT_KINDS.has(event.kind)) bailouts.push(describeEvent(event))
    },
  }

  try {
    await babel.transformAsync(source, {
      filename: file,
      babelrc: false,
      configFile: false,
      sourceType: 'module',
      code: false,
      parserOpts: { plugins: ['typescript', 'jsx'] },
      plugins: [[reactCompiler, { logger }]],
    })
  } catch (error) {
    bailouts.push(`TransformError: ${(error as Error).message.split('\n')[0]}`)
  }

  return bailouts
}

async function main() {
  const allowlist: string[] = JSON.parse(await readFile(ALLOWLIST_PATH, 'utf8'))
  const allowed = new Set(allowlist)

  const files = await collectSourceFiles(SRC_ROOT)
  const bailedOut = new Map<string, string[]>()

  for (const file of files) {
    const bailouts = await findBailouts(file)
    if (bailouts.length > 0) bailedOut.set(path.relative(FRONTEND_ROOT, file), bailouts)
  }

  const unexpected = [...bailedOut.keys()].filter((f) => !allowed.has(f)).sort()
  const stale = allowlist.filter((f) => !bailedOut.has(f)).sort()

  console.log(`Scanned ${files.length} files; ${bailedOut.size} bail out of the React Compiler.`)

  if (stale.length > 0) {
    console.log(`\n${stale.length} allowlist entries no longer bail out — remove them:`)
    for (const file of stale) console.log(`  ${file}`)
  }

  if (unexpected.length > 0) {
    console.error(`\n${unexpected.length} files bail out and are not allowlisted:`)
    for (const file of unexpected) {
      console.error(`  ${file}`)
      for (const bailout of bailedOut.get(file) ?? []) console.error(`      ${bailout}`)
    }
    console.error(`\nFix the file, or add it to ${path.relative(FRONTEND_ROOT, ALLOWLIST_PATH)}.`)
    process.exit(1)
  }

  console.log('\nNo new React Compiler bailouts.')
}

await main()
