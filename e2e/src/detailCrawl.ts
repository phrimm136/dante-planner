import fs from 'node:fs'
import path from 'node:path'
import type { Page } from '@playwright/test'
import identitySpecList from '@static/data/identitySpecList.json'
import egoSpecList from '@static/data/egoSpecList.json'
import egoGiftSpecList from '@static/data/egoGiftSpecList.json'
import themePackList from '@static/data/themePackList.json'
import enemySpecList from '@static/data/enemySpecList.json'
import assistantSpecList from '@static/data/assistantSpecList.json'
import sinnerEventSpecList from '@static/data/sinnerEventSpecList.json'
import enCommon from '@static/i18n/EN/common.json'
import krCommon from '@static/i18n/KR/common.json'
import {
  AB_PART_TYPES,
  AFFINITIES,
  ATK_TYPES,
  COIN_COLORS,
  DEF_TYPES,
  EGO_GIFT_TIER_TAGS,
  EGO_SKILL_TYPES,
  EGO_TYPES,
  SKILL_ATTRIBUTE_TYPES,
  SKILL_DEF_TYPE_ATTACK,
} from '@/shared/gameData/constants'

export const CRAWL_LANGUAGES = ['KR', 'EN'] as const
export type CrawlLanguage = (typeof CRAWL_LANGUAGES)[number]

export const ROUTE_FAMILIES = [
  { family: 'identity', prefix: '/identity/', ids: Object.keys(identitySpecList) },
  { family: 'ego', prefix: '/ego/', ids: Object.keys(egoSpecList) },
  { family: 'egoGift', prefix: '/ego-gift/', ids: Object.keys(egoGiftSpecList) },
  { family: 'themePack', prefix: '/theme-pack/', ids: Object.keys(themePackList) },
  { family: 'enemy', prefix: '/battle/enemy/', ids: Object.keys(enemySpecList) },
  { family: 'assistant', prefix: '/battle/assistant/', ids: Object.keys(assistantSpecList) },
  { family: 'sinnerEvent', prefix: '/battle/sinnerEvent/', ids: Object.keys(sinnerEventSpecList) },
] as const

export interface CrawlRoute {
  family: string
  path: string
  lang: CrawlLanguage
}

export function crawlRoutes(): CrawlRoute[] {
  return ROUTE_FAMILIES.flatMap(({ family, prefix, ids }) =>
    CRAWL_LANGUAGES.flatMap((lang) => ids.map((id) => ({ family, path: prefix + id, lang }))),
  )
}

export const VIOLATION_CATEGORIES = [
  'load',
  'brokenImage',
  'rawToken',
  'rawEnum',
  'emptyName',
] as const
export type ViolationCategory = (typeof VIOLATION_CATEGORIES)[number]

export interface Violation {
  category: ViolationCategory
  path: string
  lang: CrawlLanguage
  detail: string
}

export const RAW_TOKEN_PATTERNS = [/\[[A-Za-z][A-Za-z0-9_]*\]/g, /<\/?[a-z]+[^>]*>/g]

export const RAW_ENUM_WORDS: readonly string[] = [
  ...new Set([
    ...AFFINITIES,
    ...SKILL_ATTRIBUTE_TYPES,
    ...ATK_TYPES,
    ...DEF_TYPES,
    SKILL_DEF_TYPE_ATTACK,
    ...AB_PART_TYPES,
    ...EGO_TYPES,
    ...Object.values(EGO_SKILL_TYPES),
    ...COIN_COLORS,
    ...EGO_GIFT_TIER_TAGS,
  ]),
]

const RAW_ENUM_PATTERN = new RegExp(
  `(?<![A-Za-z0-9_])(${[...RAW_ENUM_WORDS].sort((a, b) => b.length - a.length).join('|')})(?![A-Za-z0-9_])`,
  'g',
)

export const NOT_FOUND_TITLES = [enCommon.errors.notFound.title, krCommon.errors.notFound.title]

function excerpt(text: string, index: number, length: number): string {
  return text
    .slice(Math.max(0, index - 20), index + length + 20)
    .replace(/\s+/g, ' ')
    .trim()
}

export function textViolations(text: string): { category: ViolationCategory; detail: string }[] {
  const found: { category: ViolationCategory; detail: string }[] = []
  for (const pattern of RAW_TOKEN_PATTERNS) {
    for (const match of text.matchAll(pattern)) {
      found.push({
        category: 'rawToken',
        detail: `${match[0]} in "${excerpt(text, match.index, match[0].length)}"`,
      })
    }
  }
  for (const match of text.matchAll(RAW_ENUM_PATTERN)) {
    found.push({
      category: 'rawEnum',
      detail: `${match[0]} in "${excerpt(text, match.index, match[0].length)}"`,
    })
  }
  return found
}

export type PageState = 'ready' | 'error' | 'notFound' | 'pending'

export async function pageState(page: Page): Promise<PageState> {
  return page.evaluate((notFoundTitles) => {
    const main = document.querySelector('main')
    if (document.querySelector('.border-destructive h2')) return 'error'
    if (!main || document.getElementById('seo-skeleton')) return 'pending'
    const heading = main.querySelector('h1')?.textContent?.trim() ?? ''
    if (notFoundTitles.includes(heading)) return 'notFound'
    if (main.querySelector('[data-slot=page-skeleton], [data-slot=skeleton]')) return 'pending'
    return main.innerText.trim().length > 0 ? 'ready' : 'pending'
  }, NOT_FOUND_TITLES)
}

export async function settleImages(page: Page, timeoutMs: number): Promise<void> {
  await page.evaluate(() => {
    for (const img of Array.from(document.querySelectorAll<HTMLImageElement>('main img[loading=lazy]'))) {
      img.loading = 'eager'
    }
  })
  await page
    .waitForFunction(
      () =>
        Array.from(document.querySelectorAll<HTMLImageElement>('main img')).every((i) => i.complete),
      null,
      { timeout: timeoutMs },
    )
    .catch(() => undefined)
}

export interface DomFindings {
  brokenImages: string[]
  pendingImages: string[]
  emptyNames: string[]
  text: string
}

export async function domFindings(page: Page): Promise<DomFindings> {
  return page.evaluate(() => {
    const main = document.querySelector('main')
    if (!main) return { brokenImages: [], pendingImages: [], emptyNames: [], text: '' }
    const imgs = Array.from(main.querySelectorAll('img'))
    const rendered = (el: Element) => el.getClientRects().length > 0
    const names = Array.from(main.querySelectorAll('[data-slot=name-text], h1, h2, h3')).filter(
      rendered,
    )
    return {
      brokenImages: imgs
        .filter((i) => i.complete && i.naturalWidth === 0)
        .map((i) => i.getAttribute('src') ?? '(no src)'),
      pendingImages: imgs.filter((i) => !i.complete).map((i) => i.getAttribute('src') ?? ''),
      emptyNames: names
        .filter((el) => (el.textContent ?? '').trim() === '' && !el.querySelector('img, svg'))
        .map((el) => el.getAttribute('data-slot') ?? el.tagName.toLowerCase()),
      text: main.innerText,
    }
  })
}

export function reportDir(): string {
  return (
    process.env.CRAWL_REPORT_DIR ??
    path.resolve(__dirname, '../../.claude/reports/battle-catalog/crawl')
  )
}

export interface ChunkPart {
  chunk: string
  finishedAt: string
  routes: number
  violations: Violation[]
  pageErrors: { path: string; lang: CrawlLanguage; message: string }[]
}

function withLock(dir: string, body: () => void): void {
  const lock = path.join(dir, '.lock')
  const deadline = Date.now() + 60_000
  for (;;) {
    try {
      fs.mkdirSync(lock)
      break
    } catch {
      if (Date.now() > deadline) throw new Error(`crawl report lock held: ${lock}`)
      Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 50)
    }
  }
  try {
    body()
  } finally {
    fs.rmdirSync(lock)
  }
}

function groupKey(v: Violation): string {
  return `${v.category}\t${v.detail.replace(/"[^"]*"/, '').replace(/\d+/g, '#').trim()}`
}

function summarize(parts: ChunkPart[], totalRoutes: number) {
  const violations = parts.flatMap((p) => p.violations)
  const groups = new Map<string, Violation[]>()
  for (const v of violations) {
    const key = groupKey(v)
    groups.set(key, [...(groups.get(key) ?? []), v])
  }
  const counts = Object.fromEntries(
    VIOLATION_CATEGORIES.map((c) => [c, violations.filter((v) => v.category === c).length]),
  )
  const grouped = [...groups.entries()]
    .map(([key, list]) => ({
      category: list[0].category,
      pattern: key.split('\t')[1],
      count: list.length,
      routes: new Set(list.map((v) => `${v.lang} ${v.path}`)).size,
      examples: list.slice(0, 3),
    }))
    .sort((a, b) => a.category.localeCompare(b.category) || b.count - a.count)
  return {
    generatedAt: new Date().toISOString(),
    expectedRoutes: totalRoutes,
    crawledRoutes: parts.reduce((n, p) => n + p.routes, 0),
    chunks: parts.length,
    counts,
    pageErrors: parts.flatMap((p) => p.pageErrors),
    groups: grouped,
  }
}

function markdown(summary: ReturnType<typeof summarize>): string {
  const lines = [
    '# Detail crawl',
    '',
    `Generated ${summary.generatedAt}. Routes crawled ${String(summary.crawledRoutes)} of ${String(summary.expectedRoutes)} (${String(summary.chunks)} chunks).`,
    '',
    '| Category | Violations |',
    '|---|---|',
    ...Object.entries(summary.counts).map(([c, n]) => `| ${c} | ${String(n)} |`),
    '',
    `Uncaught page errors (informational): ${String(summary.pageErrors.length)}`,
    '',
  ]
  for (const g of summary.groups) {
    lines.push(
      `## ${g.category}: ${g.pattern}`,
      '',
      `${String(g.count)} violations on ${String(g.routes)} routes.`,
      '',
    )
    for (const e of g.examples) lines.push(`- ${e.lang} ${e.path}: ${e.detail}`)
    lines.push('')
  }
  return lines.join('\n')
}

export function recordChunk(part: ChunkPart, totalRoutes: number): void {
  const dir = reportDir()
  const partsDir = path.join(dir, 'parts')
  fs.mkdirSync(partsDir, { recursive: true })
  withLock(dir, () => {
    fs.writeFileSync(path.join(partsDir, `${part.chunk}.json`), JSON.stringify(part, null, 2))
    const parts = fs
      .readdirSync(partsDir)
      .filter((f) => f.startsWith('chunk-'))
      .map((f) => JSON.parse(fs.readFileSync(path.join(partsDir, f), 'utf8')) as ChunkPart)
    const summary = summarize(parts, totalRoutes)
    fs.writeFileSync(path.join(dir, 'crawl-summary.json'), JSON.stringify(summary, null, 2))
    fs.writeFileSync(path.join(dir, 'crawl-summary.md'), markdown(summary))
  })
}

export function recordCheck(name: string, result: unknown): void {
  const partsDir = path.join(reportDir(), 'parts')
  fs.mkdirSync(partsDir, { recursive: true })
  fs.writeFileSync(path.join(partsDir, `check-${name}.json`), JSON.stringify(result, null, 2))
}
