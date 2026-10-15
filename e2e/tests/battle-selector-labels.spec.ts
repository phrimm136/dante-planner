import { test, expect } from '@playwright/test'

const WIDTHS = [390, 1400]
const REQUIRED_LABELS = ['스토리', '거울 던전']

for (const width of WIDTHS) {
  test(`KR battle selector labels stay one line high at ${width} px`, async ({ page, context }) => {
    await context.addInitScript(() => localStorage.setItem('i18nextLng', 'KR'))
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/battle/enemy/8016', { waitUntil: 'domcontentloaded' })

    for (const name of REQUIRED_LABELS) {
      await expect(page.getByRole('group', { name, exact: true })).toBeVisible({ timeout: 20_000 })
    }
    const labels = page.locator('div[role="group"] > span:first-child')

    const boxes = await labels.evaluateAll((spans) =>
      spans.map((span) => ({
        text: span.textContent,
        height: span.getBoundingClientRect().height,
        lineHeight: parseFloat(getComputedStyle(span).lineHeight),
      })),
    )
    const detail = JSON.stringify(boxes)
    expect(boxes.map((box) => box.text), detail).toEqual(expect.arrayContaining(REQUIRED_LABELS))
    for (const box of boxes) expect(box.height, detail).toBeLessThanOrEqual(box.lineHeight + 1)

    const overflow = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      viewport: window.innerWidth,
    }))
    expect(overflow.scrollWidth, JSON.stringify(overflow)).toBeLessThanOrEqual(overflow.viewport)
  })
}
