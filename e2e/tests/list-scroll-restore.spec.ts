import { test, expect } from '@playwright/test'

// Deep enough that the row sits far below the first reveal batch: a grid that renders
// only the batches it has revealed cannot be tall enough to restore to it.
const DEEP_CARD_INDEX = 119

// Outlasts the reveal window's walk from the restored row back to the top of the list.
const SAMPLE_FRAMES = 240

test('the identity list comes back to the row it left', async ({ page }) => {
  await page.goto('/identity', { waitUntil: 'domcontentloaded' })

  const cells = page.locator('div.grid > div')
  await expect.poll(() => cells.count(), { timeout: 20_000 }).toBeGreaterThan(DEEP_CARD_INDEX)

  const deepCell = cells.nth(DEEP_CARD_INDEX)
  await deepCell.scrollIntoViewIfNeeded()

  const savedScrollY = await page.evaluate(() => window.scrollY)
  expect(savedScrollY).toBeGreaterThan(0)

  const rowHeight = await deepCell.evaluate((el) => el.getBoundingClientRect().height)
  expect(rowHeight).toBeGreaterThan(0)

  const deepLink = deepCell.locator('a')
  await expect(deepLink).toHaveCount(1, { timeout: 20_000 })
  const deepHref = await deepLink.getAttribute('href')

  await deepLink.click()
  await page.waitForURL('**/identity/*')
  // The router snapshots the offset on the way out and replays it on the way back; a
  // return fired inside the detail route's own first frames races that bookkeeping.
  await expect(page.getByRole('heading').first()).toBeVisible({ timeout: 20_000 })

  await page.goBack()
  await page.waitForURL('**/identity')

  const reveal = await page.evaluate(
    async ({ deepIndex, frames }) => {
      const cellAt = (index: number) =>
        document.querySelectorAll('div.grid > div').item(index) as HTMLElement | null

      let deepFrame = -1
      let firstFrame = -1

      for (let frame = 0; frame < frames && (deepFrame < 0 || firstFrame < 0); frame += 1) {
        await new Promise((resolve) => requestAnimationFrame(resolve))
        if (deepFrame < 0 && cellAt(deepIndex)?.querySelector('img')) deepFrame = frame
        if (firstFrame < 0 && cellAt(0)?.querySelector('img')) firstFrame = frame
      }

      return {
        scrollY: window.scrollY,
        deepFrame,
        firstFrame,
        deepHref: cellAt(deepIndex)?.querySelector('a')?.getAttribute('href') ?? null,
      }
    },
    { deepIndex: DEEP_CARD_INDEX, frames: SAMPLE_FRAMES },
  )

  const offsets = `restored ${String(reveal.scrollY)} against saved ${String(savedScrollY)}`
  expect(Math.abs(reveal.scrollY - savedScrollY), offsets).toBeLessThanOrEqual(rowHeight)

  expect(reveal.deepHref).toBe(deepHref)
  expect(reveal.deepFrame).toBeGreaterThanOrEqual(0)
  if (reveal.firstFrame >= 0) {
    expect(reveal.deepFrame).toBeLessThanOrEqual(reveal.firstFrame)
  }
})
