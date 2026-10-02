import { test, expect } from '@playwright/test'

const UPDATE_BANNER_TEXT = 'A new version of the site is available.'
const DEPLOYED_BUILD_ID = 'e2e-deployed-build'
const NARROW_VIEWPORT = { width: 360, height: 740 }

test('a newer deployed build shows the update banner, and its button reloads the page', async ({
  page,
}) => {
  await page.setViewportSize(NARROW_VIEWPORT)
  await page.route('**/version.json', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ buildId: DEPLOYED_BUILD_ID }),
    }),
  )
  await page.route('**/api/**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: 'null' }),
  )
  const loads = { count: 0 }
  page.on('load', () => {
    loads.count += 1
  })

  await page.goto('/', { waitUntil: 'load' })

  const banner = page.getByRole('status').filter({ hasText: UPDATE_BANNER_TEXT })
  await expect(banner).toBeVisible({ timeout: 20_000 })
  await expect(banner.getByRole('button', { name: 'Dismiss' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
    NARROW_VIEWPORT.width,
  )
  expect(loads.count).toBe(1)

  await banner.getByRole('button', { name: 'Reload' }).click()

  await expect.poll(() => loads.count).toBe(2)
})
