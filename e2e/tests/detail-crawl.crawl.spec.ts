import { test, expect, type BrowserContext, type Page, type Response } from '@playwright/test'
import {
  crawlRoutes,
  domFindings,
  pageState,
  recordCheck,
  recordChunk,
  settleImages,
  textViolations,
  type ChunkPart,
  type CrawlRoute,
  type PageState,
  type Violation,
} from '../src/detailCrawl'

const CHUNK_SIZE = 20
const READY_TIMEOUT_MS = 60_000
const IMAGE_TIMEOUT_MS = 15_000
const ROUTE_BUDGET_MS = READY_TIMEOUT_MS + IMAGE_TIMEOUT_MS + 15_000
const ASPECT_TOLERANCE = 0.02
const STEP_TIMEOUT_MS = 30_000

const ROUTES = crawlRoutes()
const CHUNKS = Array.from({ length: Math.ceil(ROUTES.length / CHUNK_SIZE) }, (_, i) =>
  ROUTES.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE),
)

class HungStep extends Error {}

function step<T>(name: string, work: Promise<T>): Promise<T> {
  let timer: NodeJS.Timeout | undefined
  const hung = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new HungStep(`hung at ${name}`)), STEP_TIMEOUT_MS)
  })
  return Promise.race([work, hung]).finally(() => clearTimeout(timer))
}

async function waitForState(page: Page): Promise<PageState> {
  const deadline = Date.now() + READY_TIMEOUT_MS
  for (;;) {
    const state = await step('readiness', pageState(page))
    if (state !== 'pending' || Date.now() > deadline) return state
    await page.waitForTimeout(100)
  }
}

async function crawlRoute(page: Page, route: CrawlRoute, part: ChunkPart): Promise<boolean> {
  const record = (category: Violation['category'], detail: string) =>
    part.violations.push({ category, path: route.path, lang: route.lang, detail })
  const failedAssets = new Set<string>()
  const onResponse = (response: Response) => {
    const type = response.request().resourceType()
    if (response.status() >= 400 && (type === 'image' || type === 'stylesheet')) {
      failedAssets.add(`${String(response.status())} ${new URL(response.url()).pathname}`)
    }
  }
  const onPageError = (error: Error) =>
    part.pageErrors.push({ path: route.path, lang: route.lang, message: error.message })
  page.on('response', onResponse)
  page.on('pageerror', onPageError)
  try {
    await step('goto', page.goto(route.path, { waitUntil: 'domcontentloaded' }))
    const state = await waitForState(page)
    if (state !== 'ready') {
      record('load', state === 'pending' ? `not ready after ${String(READY_TIMEOUT_MS)} ms` : state)
    }
    if (state === 'error' || state === 'notFound') return true
    await step('images', settleImages(page, IMAGE_TIMEOUT_MS))
    const dom = await step('findings', domFindings(page))
    for (const asset of failedAssets) record('brokenImage', asset)
    for (const src of dom.brokenImages) {
      if (![...failedAssets].some((a) => a.endsWith(` ${new URL(src, page.url()).pathname}`))) {
        record('brokenImage', `naturalWidth 0 ${src}`)
      }
    }
    for (const src of dom.pendingImages) record('brokenImage', `still loading ${src}`)
    for (const slot of dom.emptyNames) record('emptyName', slot)
    for (const v of textViolations(dom.text)) record(v.category, v.detail)
    return true
  } catch (error) {
    record('load', `crawl error: ${error instanceof Error ? error.message.split('\n')[0] : String(error)}`)
    return false
  } finally {
    page.off('response', onResponse)
    page.off('pageerror', onPageError)
  }
}

async function setLanguage(context: BrowserContext, lang: string): Promise<void> {
  await context.addInitScript((l) => localStorage.setItem('i18nextLng', l), lang)
}

test.describe.configure({ mode: 'parallel' })

test('detectors flag synthetic violators', async ({ page }) => {
  expect(textViolations('[Combustion] then <color=#fff>x</color> and CRIMSON, HEAD_PLATE').map((v) => v.category)).toEqual([
    'rawToken',
    'rawToken',
    'rawToken',
    'rawEnum',
    'rawEnum',
  ])
  expect(textViolations('[적중시] Crimsonness HEADS HEAD_PLATEX 3 < 4')).toEqual([])

  await page.setContent(
    '<main><h1></h1><span data-slot="name-text"> </span><h2><img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"></h2>' +
      '<img src="data:image/png;base64,AAAA"><p>ok</p></main>',
  )
  await page.waitForFunction(() => Array.from(document.images).every((i) => i.complete))
  const dom = await domFindings(page)
  expect(dom.emptyNames).toEqual(['h1', 'name-text'])
  expect(dom.brokenImages).toEqual(['data:image/png;base64,AAAA'])
})

for (const [index, chunk] of CHUNKS.entries()) {
  const first = chunk[0]
  const last = chunk[chunk.length - 1]
  test(`chunk ${String(index)}: ${first.lang} ${first.path} .. ${last.lang} ${last.path}`, async ({
    context,
  }) => {
    test.setTimeout(chunk.length * ROUTE_BUDGET_MS)
    await setLanguage(context, first.lang)
    let page = await context.newPage()
    const part: ChunkPart = {
      chunk: `chunk-${String(index).padStart(4, '0')}`,
      finishedAt: '',
      routes: 0,
      violations: [],
      pageErrors: [],
    }
    let lang = first.lang
    for (const route of chunk) {
      if (route.lang !== lang) {
        lang = route.lang
        await setLanguage(context, lang)
      }
      if (!(await crawlRoute(page, route, part))) {
        void page.close().catch(() => undefined)
        page = await context.newPage()
      }
      part.routes += 1
    }
    part.finishedAt = new Date().toISOString()
    recordChunk(part, ROUTES.length)
    expect(part.violations, `${String(part.violations.length)} violations`).toEqual([])
  })
}

test('a coin renders at its natural aspect', async ({ page }) => {
  await page.goto('/identity/10101', { waitUntil: 'domcontentloaded' })
  const coin = page.locator('main [data-slot=skill-coins] img').first()
  await expect(coin).toBeVisible({ timeout: READY_TIMEOUT_MS })
  await expect.poll(() => coin.evaluate((i: HTMLImageElement) => i.naturalWidth)).toBeGreaterThan(0)
  const measured = await coin.evaluate((i: HTMLImageElement) => {
    const box = i.getBoundingClientRect()
    return { rendered: box.width / box.height, natural: i.naturalWidth / i.naturalHeight, src: i.src }
  })
  const deviation = Math.abs(measured.rendered / measured.natural - 1)
  recordCheck('coin-aspect', { ...measured, deviation })
  expect(deviation).toBeLessThanOrEqual(ASPECT_TOLERANCE)
})

test('a decorated skill animates its caution stripe', async ({ page }) => {
  await page.goto('/battle/enemy/1079', { waitUntil: 'domcontentloaded' })
  const stripe = page
    .locator('main [data-importance-box]:has([data-slot=skill-coins]) .importance-stripe')
    .first()
  await expect(stripe).toBeAttached({ timeout: READY_TIMEOUT_MS })
  const animationName = await stripe.evaluate((e) => getComputedStyle(e).animationName)
  recordCheck('caution-stripe', { route: '/battle/enemy/1079', animationName })
  expect(animationName).not.toBe('none')
})
