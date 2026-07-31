import { test, expect, type ConsoleMessage } from '@playwright/test'

// index.html ships a #seo-skeleton inside #root for crawlers, and React replaces the whole subtree
// on mount.
const PUBLIC_ROUTES = [
  '/',
  '/planner',
  '/planner/md',
  '/planner/md/gesellschaft',
  '/planner/md/new',
  '/planner/deck',
  '/planner/extraction',
  '/identity',
  '/ego',
  '/ego-gift',
  '/theme-pack',
  '/ab-event',
  '/keyword',
  '/privacy',
  '/terms',
]

for (const route of PUBLIC_ROUTES) {
  test(`${route} mounts without a client error`, async ({ page }) => {
    const consoleErrors: string[] = []
    const pageErrors: string[] = []
    page.on('console', (m: ConsoleMessage) => {
      if (m.type() === 'error') consoleErrors.push(m.text())
    })
    page.on('pageerror', (e) => pageErrors.push(e.message))

    const response = await page.goto(route, { waitUntil: 'domcontentloaded' })
    expect(response?.status()).toBe(200)

    // The heaviest routes exceed the default 5s expect timeout when the whole file runs in
    // parallel. A mount budget would be a different test.
    await expect(page.locator('#seo-skeleton')).toHaveCount(0, { timeout: 20_000 })
    expect(pageErrors).toEqual([])
    expect(consoleErrors).toEqual([])
  })
}

test('an unknown route renders the not-found page rather than failing to mount', async ({
  page,
}) => {
  await page.goto('/this-route-does-not-exist', { waitUntil: 'domcontentloaded' })
  await expect(page.locator('#seo-skeleton')).toHaveCount(0)
})
