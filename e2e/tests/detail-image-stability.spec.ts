import { test, expect } from '@playwright/test'

// The webp delay guarantees the swapped-in image is still in flight across at least one
// layout frame, which is the condition the assertion is about; a same-frame load can
// mask the defect on localhost.
const IMAGE_DELAY_MS = 150

test('the EGO detail image keeps its box while the corrosion CG loads', async ({ page }) => {
  await page.route('**/*.webp', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, IMAGE_DELAY_MS))
    await route.continue()
  })

  await page.goto('/ego/20103', { waitUntil: 'domcontentloaded' })

  const corrosionTab = page.getByRole('button', { name: 'Corrosion' })
  await expect(corrosionTab).toBeVisible({ timeout: 20_000 })

  const section = page.locator('div.relative.bg-muted').first()
  await expect
    .poll(async () => (await section.boundingBox())?.height ?? 0, { timeout: 20_000 })
    .toBeGreaterThan(100)

  const heights = await section.evaluate((el) => {
    const trace: number[] = [el.getBoundingClientRect().height]
    new ResizeObserver((entries) => {
      for (const entry of entries) trace.push(entry.contentRect.height)
    }).observe(el)
    ;(window as unknown as { __heights: number[] }).__heights = trace
    return trace
  })
  expect(heights[0]).toBeGreaterThan(100)

  await corrosionTab.click()
  await page.waitForTimeout(IMAGE_DELAY_MS * 4)

  const trace = await page.evaluate(
    () => (window as unknown as { __heights: number[] }).__heights,
  )
  const minHeight = Math.min(...trace)
  expect(minHeight, `section height trace across the swap: ${trace.join(', ')}`).toBeGreaterThan(
    100,
  )
})

test('the lightbox shows the whole image inside its clip box', async ({ page }) => {
  await page.goto('/ego/20103', { waitUntil: 'domcontentloaded' })

  const expandButton = page.getByRole('button', { name: 'Expand image' })
  await expect(expandButton).toBeVisible({ timeout: 20_000 })
  await expandButton.click()

  const dialogImage = page.locator('[data-slot="dialog-content"] .react-transform-component img')
  await expect
    .poll(async () => dialogImage.evaluate((el: HTMLImageElement) => el.naturalWidth), {
      timeout: 20_000,
    })
    .toBeGreaterThan(0)

  const geometry = await dialogImage.evaluate((img) => {
    const wrapper = img.closest('.react-transform-wrapper') as HTMLElement
    const i = img.getBoundingClientRect()
    const w = wrapper.getBoundingClientRect()
    return {
      img: { left: i.left, right: i.right, top: i.top, bottom: i.bottom, width: i.width },
      clip: { left: w.left, right: w.right, top: w.top, bottom: w.bottom, width: w.width },
      viewportWidth: window.innerWidth,
    }
  })

  const detail = JSON.stringify(geometry)
  expect(geometry.clip.width, detail).toBeGreaterThan(geometry.viewportWidth * 0.9)
  expect(geometry.img.left, detail).toBeGreaterThanOrEqual(geometry.clip.left - 1)
  expect(geometry.img.right, detail).toBeLessThanOrEqual(geometry.clip.right + 1)
  expect(geometry.img.top, detail).toBeGreaterThanOrEqual(geometry.clip.top - 1)
  expect(geometry.img.bottom, detail).toBeLessThanOrEqual(geometry.clip.bottom + 1)
  expect(geometry.img.width, detail).toBeGreaterThan(100)
})
