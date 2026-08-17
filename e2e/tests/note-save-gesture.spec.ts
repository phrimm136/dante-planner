import { test, expect } from '../src/browser'
import { authenticateContext } from '../src/auth'
import { producesRequest, settled, withoutScrollShift } from '../src/gestures'
import { seedLocalPlanner } from '../src/localPlanner'
import { dropPlanner, seedPlanner } from '../src/plannerFixture'
import { closeSeedPool } from '../src/seed'

/**
 * Editing the closing note and clicking Save once has to reach the server. `mousedown` blurs the
 * note, and the toolbar the editor mounts on `isFocused` is a row in the flow: dropping it on the
 * blur removes that row and lifts everything below by its height, so the Save button travels out
 * from under the pointer between `mousedown` and `mouseup`, `mouseup` lands on the container
 * behind it, and no `click` is composed. Nothing downstream runs, so there is no failure to
 * report — the save is simply lost, silently.
 *
 * Three assertions at increasing distance from the cause: the editor opts out of scroll
 * anchoring, the viewport does not move, and the write leaves the browser.
 */

/** The toolbar's height, measured as the document's shrink when the note blurs. */
const TOOLBAR_HEIGHT_PX = 41

/** How far back from the document end the gesture is staged, with the button still in view. */
const SCROLL_BACKOFF_PX = 120

test.afterAll(closeSeedPool)

test('a section note edit reaches the server when Save is clicked once', async ({
  page,
  context,
  baseURL,
}) => {
  const fixture = await seedPlanner(baseURL!, 'note-save')

  try {
    await authenticateContext(context, fixture.user.id, baseURL!)
    await seedLocalPlanner(page, { plannerId: fixture.plannerId, title: fixture.title })

    await page.goto(`/planner/md/${fixture.plannerId}/edit`, { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: 'Closing Notes' })).toBeVisible({
      timeout: 20_000,
    })
    await settled(page)

    const notes = page.locator('.note-editor').last()
    await expect(notes).toHaveCSS('overflow-anchor', 'none')

    await notes.locator('.note-editor-content').click()
    await page.keyboard.type(`edited by ${fixture.title}`)
    await settled(page)

    // The closing note's Save button is the second of two; the first sits above the editor and
    // does not move when the toolbar goes.
    const save = page.getByRole('button', { name: 'Save', exact: true }).last()

    // Centring clamps to the document end, where the shrink moves the scroll instead of the
    // button and the pointer keeps its target by accident. Backing off restores the real case,
    // and Playwright does not scroll for a click whose target is already in view.
    await save.evaluate((element) => element.scrollIntoView({ block: 'center' }))
    await page.evaluate((backoff: number) => window.scrollBy(0, -backoff), SCROLL_BACKOFF_PX)
    await expect(save).toBeInViewport()

    const viewport = await page.evaluate(() => ({
      scrollY: Math.round(window.scrollY),
      remaining: Math.round(
        document.documentElement.scrollHeight - window.innerHeight - window.scrollY,
      ),
    }))
    expect(viewport.scrollY, 'at offset zero there is nothing for anchoring to shift')
      .toBeGreaterThan(0)
    expect(
      viewport.remaining,
      'at the document end the scroll clamps by exactly the toolbar height, which carries the ' +
        'button back under the pointer and hides the defect',
    ).toBeGreaterThan(TOOLBAR_HEIGHT_PX)

    const request = await producesRequest(
      page,
      { method: 'PUT', url: `/api/planner/md/${fixture.plannerId}` },
      () => withoutScrollShift(page, () => save.click()),
    )

    expect(JSON.parse(request.postData() ?? '{}')).toMatchObject({ id: fixture.plannerId })
    await expect(page.getByText('Plan saved successfully')).toBeVisible()
  } finally {
    await dropPlanner(fixture)
  }
})
