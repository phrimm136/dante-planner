import { test, expect, type Page } from '@playwright/test'

const CHUNK_PATH = /^\/a\/[^/]+\.js$/
const STALE_BODY = '<!doctype html><title>stale</title>'

type StalePolicy = 'once' | 'always'

async function serveStaleChunks(page: Page, policy: StalePolicy) {
  const seen = new Set<string>()
  const failed = new Set<string>()
  let armed = false

  await page.route(
    (url) => CHUNK_PATH.test(url.pathname),
    async (route) => {
      const url = route.request().url()
      if (!armed) {
        seen.add(url)
        return route.continue()
      }
      const failAgain = failed.has(url) && policy === 'always'
      const failFirst =
        !failed.has(url) && !seen.has(url) && (policy === 'always' || failed.size === 0)
      if (failAgain || failFirst) {
        failed.add(url)
        return route.fulfill({ status: 200, contentType: 'text/html', body: STALE_BODY })
      }
      return route.continue()
    },
  )

  return {
    arm: () => {
      armed = true
    },
    failed,
  }
}

function trackChunkRequests(page: Page) {
  const inFlight = new Set<string>()
  const isChunk = (url: string) => CHUNK_PATH.test(new URL(url).pathname)
  page.on('request', (request) => {
    if (isChunk(request.url())) inFlight.add(request.url())
  })
  const settle = (request: { url: () => string }) => {
    inFlight.delete(request.url())
  }
  page.on('requestfinished', settle)
  page.on('requestfailed', settle)
  return {
    settled: () => expect.poll(() => inFlight.size, { timeout: 20_000 }).toBe(0),
  }
}

function countLoads(page: Page) {
  const loads = { count: 0 }
  page.on('load', () => {
    loads.count += 1
  })
  return loads
}

async function openHomeThenIdentity(page: Page, policy: StalePolicy) {
  const stale = await serveStaleChunks(page, policy)
  const loads = countLoads(page)
  const chunks = trackChunkRequests(page)

  await page.goto('/', { waitUntil: 'load' })
  await expect(page.locator('#seo-skeleton')).toHaveCount(0, { timeout: 20_000 })
  const identityLink = page.locator('a[href="/identity"]:visible').first()
  await expect(identityLink).toBeVisible()
  await chunks.settled()

  stale.arm()
  await identityLink.click()
  return { stale, loads, chunks }
}

test('a stale lazy chunk reloads the tab once and the route renders', async ({ page }) => {
  const { stale, loads, chunks } = await openHomeThenIdentity(page, 'once')

  await expect(page).toHaveURL(/\/identity$/)
  await expect(page.locator('a[href^="/identity/"]').first()).toBeVisible({ timeout: 20_000 })
  await chunks.settled()

  expect(stale.failed.size).toBe(1)
  await expect.poll(() => loads.count).toBe(2)
  expect(await page.evaluate(() => sessionStorage.getItem('chunkReloadAt'))).toMatch(/^\d+$/)
  await expect(page.getByText('CHUNK_LOAD')).toHaveCount(0)
})

test('a chunk that keeps failing reloads once, then shows CHUNK_LOAD', async ({ page }) => {
  const { stale, loads, chunks } = await openHomeThenIdentity(page, 'always')

  await expect(page.getByText('CHUNK_LOAD')).toBeVisible({ timeout: 20_000 })
  await chunks.settled()

  expect(stale.failed.size).toBeGreaterThan(0)
  await expect.poll(() => loads.count).toBe(2)
  await expect(page.getByText('CHUNK_LOAD')).toBeVisible()
})
