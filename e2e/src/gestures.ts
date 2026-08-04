import { expect } from '@playwright/test'
import type { Page, Request } from '@playwright/test'

/**
 * Browser-tier gesture primitives.
 *
 * A gesture is a click or a keypress plus everything the page does in response. Playwright's own
 * waits cover navigation and locator state; these cover the three things that go wrong between a
 * `mousedown` and the request it was supposed to cause.
 */

/** Frames `settled` will spend waiting for the document to stop growing. */
const SETTLE_FRAME_BUDGET = 600

/** Wall-clock ceiling on `settled`, enforced outside the page so a faked clock cannot stop it. */
const SETTLE_DEADLINE_MS = 15_000

/** Default window a gesture has to emit its request in. */
const REQUEST_TIMEOUT_MS = 10_000

/**
 * The planner editor's auto-save debounce, mirroring `AUTO_SAVE_DEBOUNCE_MS` in
 * `frontend/src/lib/constants/planner.ts`. `page.clock.runFor` this to reach the write.
 *
 * Auto-save writes IndexedDB and nothing else — the server PUT is the manual Save button's. A
 * spec that waits for a request after typing is waiting for one that will never come.
 */
export const AUTO_SAVE_DEBOUNCE_MS = 1_000

interface Growth {
  scrollY: number
  height: number
}

function measure(page: Page): Promise<Growth> {
  return page.evaluate(() => ({
    scrollY: Math.round(window.scrollY),
    height: document.body.scrollHeight,
  }))
}

/**
 * Resolves once webfonts have loaded and the document has stopped growing for two consecutive
 * frames.
 *
 * `load` and `domcontentloaded` both fire while the page is still getting taller: lists render
 * through `useProgressiveCount`, which appends a slice per animation frame, and a webfont swap
 * reflows everything below it. A gesture aimed at a coordinate measured before either finishes
 * lands somewhere else.
 *
 * Two frames rather than one because a single equal pair is also what a slice boundary looks
 * like — `useProgressiveCount` appends nothing on the frame it schedules the next slice in.
 *
 * **This never resolves under `page.clock.install()`**, which fakes `requestAnimationFrame` along
 * with the timers: no frame is delivered until the clock is run forward. Call `settled` before
 * installing the clock, or `page.clock.runFor` the reveal first. The wall-clock deadline turns
 * that into a named failure instead of a hang.
 */
export async function settled(page: Page): Promise<void> {
  const reveal = page
    .evaluate(async (frameBudget: number) => {
      await document.fonts.ready

      return new Promise<boolean>((resolve) => {
        let previous = -1
        let steady = 0
        let frames = 0

        const tick = (): void => {
          const height = document.body.scrollHeight
          steady = height <= previous ? steady + 1 : 0
          previous = height

          if (steady >= 2) return resolve(true)
          if (++frames >= frameBudget) return resolve(false)
          requestAnimationFrame(tick)
        }

        requestAnimationFrame(tick)
      })
    }, SETTLE_FRAME_BUDGET)
    .catch(() => false)

  const stalled = Symbol('stalled')
  const deadline = new Promise<typeof stalled>((resolve) => {
    setTimeout(() => resolve(stalled), SETTLE_DEADLINE_MS).unref?.()
  })

  const outcome = await Promise.race([reveal, deadline])

  if (outcome === stalled) {
    throw new Error(
      `the document did not settle within ${SETTLE_DEADLINE_MS}ms and no frame budget was ` +
        'spent, so frames are not being delivered — page.clock.install() fakes ' +
        'requestAnimationFrame, and the reveal needs page.clock.runFor to advance',
    )
  }
  if (outcome === false) {
    throw new Error(
      `the document was still growing after ${SETTLE_FRAME_BUDGET} frames; something is ` +
        'appending to it indefinitely',
    )
  }
}

/**
 * Runs a gesture and fails if the viewport moved under it.
 *
 * Chrome's scroll anchoring compensates when content above the viewport disappears — an editor
 * toolbar unmounting on blur, a collapsing panel — by scrolling to keep the anchored element
 * still. To a user mid-click that is the button walking out from under the pointer between
 * `mousedown` and `mouseup`, and the `click` event is never composed. Nothing throws: the gesture
 * simply has no effect.
 *
 * The document height rides along in the failure message because the scroll shift is the symptom
 * and the shrink is the cause.
 */
export async function withoutScrollShift<T>(page: Page, gesture: () => Promise<T>): Promise<T> {
  const before = await measure(page)
  const result = await gesture()
  const after = await measure(page)

  expect(
    after.scrollY,
    `the gesture shifted the viewport by ${after.scrollY - before.scrollY}px ` +
      `(scrollY ${before.scrollY} → ${after.scrollY}) as the document changed by ` +
      `${after.height - before.height}px (scrollHeight ${before.height} → ${after.height}). ` +
      'Chrome re-anchors the scroll position when content above the viewport is removed; the ' +
      'element under the pointer moves with it and the click never completes.',
  ).toBe(before.scrollY)

  return result
}

export interface RequestMatcher {
  /** Uppercase HTTP method. Omitted, any method matches. */
  method?: string
  /** A string matches as a substring of the URL; a RegExp is tested against it. */
  url: string | RegExp
}

function describe(matcher: RequestMatcher): string {
  return `${matcher.method ?? 'any'} ${matcher.url}`
}

function matches(request: Request, matcher: RequestMatcher): boolean {
  if (matcher.method && request.method() !== matcher.method) return false
  const url = request.url()
  return typeof matcher.url === 'string' ? url.includes(matcher.url) : matcher.url.test(url)
}

/**
 * Runs a gesture and fails if it emitted no matching request.
 *
 * The failure a UI test most often misses is the silent one — the handler never ran, so there is
 * no error, no toast, and no state change to assert the absence of. Naming the request the
 * gesture exists to cause turns that into a failure at the gesture rather than at whatever the
 * spec checked next.
 *
 * The rejected requests are listed on failure: a gesture that fired the wrong endpoint and one
 * that fired nothing at all are different bugs, and the assertion cannot tell them apart.
 */
export async function producesRequest<T>(
  page: Page,
  matcher: RequestMatcher,
  gesture: () => Promise<T>,
  options: { timeout?: number } = {},
): Promise<Request> {
  const seen: string[] = []
  const record = (request: Request): void => {
    seen.push(`${request.method()} ${request.url()}`)
  }
  page.on('request', record)

  // Registered before the gesture runs, and awaited after: a request that races the gesture's
  // own promise to completion is still caught.
  const pending = page
    .waitForRequest((request) => matches(request, matcher), {
      timeout: options.timeout ?? REQUEST_TIMEOUT_MS,
    })
    .catch(() => null)

  try {
    await gesture()
    const request = await pending

    if (!request) {
      const attempted = seen.length ? seen.join('\n  ') : '(the page made no request at all)'
      throw new Error(
        `the gesture emitted no ${describe(matcher)} request.\nRequests seen:\n  ${attempted}`,
      )
    }
    return request
  } finally {
    page.off('request', record)
  }
}
