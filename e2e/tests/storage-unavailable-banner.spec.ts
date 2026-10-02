import { test, expect } from '@playwright/test'

const STORAGE_BANNER_TEXT =
  'This browser cannot open local storage, so guest plans are not saved here. Close other tabs of this site and reload, or check that site data is allowed.'

test('the planner list renders under the storage banner when IndexedDB cannot open', async ({
  page,
}) => {
  await page.addInitScript(() => {
    IDBFactory.prototype.open = () => {
      throw new DOMException('blocked', 'SecurityError')
    }
  })
  await page.route('**/api/**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: 'null' }),
  )

  await page.goto('/planner/md')

  await expect(page.getByRole('status').filter({ hasText: STORAGE_BANNER_TEXT })).toBeVisible({
    timeout: 20_000,
  })
  await expect(page.getByRole('heading', { name: 'No plans yet' })).toBeVisible()
  await expect(page.getByText('Something Went Wrong')).toHaveCount(0)
  await expect(page.getByText(/CHUNK_LOAD|UNKNOWN/)).toHaveCount(0)
})
