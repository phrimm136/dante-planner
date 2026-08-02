/**
 * Bulk-sync static/data and static/i18n JSON files to local Miniflare R2.
 * Uses getPlatformProxy() for direct R2 API access — no process spawning.
 *
 * Usage: yarn dev:r2-sync
 */

import { getPlatformProxy } from 'wrangler'
import fs from 'node:fs'
import path from 'node:path'

const STATIC_DIR = path.resolve(import.meta.dirname, '../../static')
const DIRS_TO_SYNC = ['data', 'i18n', 'images']

function collectFiles(dir: string): string[] {
  const files: string[] = []
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) files.push(...collectFiles(full))
    else files.push(full)
  }
  return files
}

async function main() {
  if (!fs.existsSync(STATIC_DIR)) {
    console.error('static/ not found. Run: git submodule update --init')
    process.exit(1)
  }

  const proxy = await getPlatformProxy<{ GAME_DATA: { put: (key: string, value: Uint8Array) => Promise<unknown> } }>({
    configPath: path.resolve(import.meta.dirname, '../wrangler.jsonc'),
  })

  const files: string[] = []
  for (const dir of DIRS_TO_SYNC) {
    const fullDir = path.join(STATIC_DIR, dir)
    if (fs.existsSync(fullDir)) {
      files.push(...collectFiles(fullDir))
    }
  }

  console.log(`Syncing ${files.length} files to local R2...`)

  let count = 0
  for (const file of files) {
    const key = path.relative(STATIC_DIR, file)
    const content = fs.readFileSync(file)
    await proxy.env.GAME_DATA.put(key, new Uint8Array(content))
    count++
    if (count % 100 === 0) console.log(`  ${count}/${files.length}`)
  }

  await proxy.dispose()
  console.log(`Done. ${count} files synced to local R2.`)
}

main()
