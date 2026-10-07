import fs from 'node:fs'
import path from 'node:path'
import type { FullConfig } from '@playwright/test'

const BROWSER_PROJECT = 'chromium'
const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1'])

/** The `--project` filters on the command line, or null when every project runs. */
export function projectFilters(argv: readonly string[]): string[] | null {
  const filters: string[] = []
  let collecting = false
  for (const arg of argv) {
    if (arg.startsWith('-')) collecting = false
    if (arg.startsWith('--project=')) filters.push(arg.slice('--project='.length))
    else if (arg === '--project') collecting = true
    else if (collecting) filters.push(arg)
  }
  return filters.length > 0 ? filters : null
}

/** Mirrors Playwright's own project filter: case-insensitive, `*` as a wildcard. */
export function selectsProject(filters: readonly string[] | null, name: string): boolean {
  if (filters === null) return true
  return filters.some((filter) => {
    const pattern = filter
      .toLocaleLowerCase()
      .split('*')
      .map((part) => part.replace(/[.+?^${}()|[\]\\]/g, '\\$&'))
      .join('.*')
    return new RegExp(`^${pattern}$`).test(name.toLocaleLowerCase())
  })
}

export function isLocalBaseUrl(baseURL: string | undefined): boolean {
  return baseURL === undefined || LOCAL_HOSTS.has(new URL(baseURL).hostname)
}

/** Why the served bundle cannot be trusted, or null when it is at least as new as its source. */
export function staleDistReason(distNewestMs: number | null, srcNewestMs: number): string | null {
  if (distNewestMs === null) return 'frontend/dist is missing or empty'
  if (distNewestMs < srcNewestMs) return 'frontend/dist is older than frontend/src'
  return null
}

function newestFileMs(dir: string): number | null {
  if (!fs.existsSync(dir)) return null
  let newest: number | null = null
  for (const entry of fs.readdirSync(dir, { recursive: true, withFileTypes: true })) {
    if (!entry.isFile()) continue
    const mtime = fs.statSync(path.join(entry.parentPath, entry.name)).mtimeMs
    if (newest === null || mtime > newest) newest = mtime
  }
  return newest
}

export default function globalSetup(config: FullConfig): void {
  const browserProject = config.projects.find((project) => project.name === BROWSER_PROJECT)
  if (!browserProject || !selectsProject(projectFilters(process.argv), BROWSER_PROJECT)) return
  if (!isLocalBaseUrl(browserProject.use.baseURL)) return

  const frontend = path.resolve(path.dirname(config.configFile ?? config.rootDir), '../frontend')
  const reason = staleDistReason(
    newestFileMs(path.join(frontend, 'dist')),
    newestFileMs(path.join(frontend, 'src')) ?? 0,
  )
  if (reason) {
    throw new Error(
      `${reason}, so nginx cannot serve the current source. Run \`yarn --cwd frontend build\` first.`,
    )
  }
}
