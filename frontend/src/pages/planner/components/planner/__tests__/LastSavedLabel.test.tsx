/**
 * Rendering rules for the last-saved label: the standalone span, the inline
 * suffix, and the timestamps that must render nothing at all.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render } from '@testing-library/react'
import { LastSavedLabel } from '../LastSavedLabel'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) =>
      params && 'time' in params ? `${key}:${String(params.time)}` : key,
    i18n: { language: 'EN' },
  }),
}))

const NOW = new Date('2026-08-15T00:00:00.000Z')

const AUTO_SAVING = 'pages.plannerMD.save.autoSaving'

/** Timestamp, and the relative phrase the label must render for it. */
const RENDERED_CASES: Array<[string, string, string]> = [
  ['a recent timestamp', new Date(NOW.getTime() - 120_000).toISOString(), '2 minutes ago'],
  ['an old timestamp', '2020-01-01T00:00:00.000Z', '6 years ago'],
  ['a future timestamp', new Date(NOW.getTime() + 600_000).toISOString(), 'in 10 minutes'],
]

const EMPTY_CASES: Array<[string, string | null]> = [
  ['an unparseable string', 'not-a-date'],
  ['an empty string', ''],
  ['null', null],
]

describe('LastSavedLabel', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it.each(RENDERED_CASES)(
    'renders the standalone span for %s',
    (_label, lastSavedAt, relativeTime) => {
      const { container } = render(<LastSavedLabel lastSavedAt={lastSavedAt} />)

      expect(container.innerHTML).toBe(
        `<span class="text-sm text-muted-foreground">sync.lastSaved:${relativeTime}</span>`,
      )
    },
  )

  it.each(RENDERED_CASES)(
    'renders the inline suffix for %s',
    (_label, lastSavedAt, relativeTime) => {
      const { container } = render(
        <span className="text-sm text-muted-foreground">
          {AUTO_SAVING}
          <LastSavedLabel lastSavedAt={lastSavedAt} inline />
        </span>,
      )

      const line = container.firstElementChild

      expect(line?.childElementCount).toBe(0)
      expect(line?.textContent).toBe(`${AUTO_SAVING} - sync.lastSaved:${relativeTime}`)
    },
  )

  it.each(EMPTY_CASES)('renders nothing standalone for %s', (_label, lastSavedAt) => {
    const { container } = render(<LastSavedLabel lastSavedAt={lastSavedAt} />)

    expect(container.innerHTML).toBe('')
  })

  it.each(EMPTY_CASES)('renders no inline suffix for %s', (_label, lastSavedAt) => {
    const { container } = render(
      <span className="text-sm text-muted-foreground">
        {AUTO_SAVING}
        <LastSavedLabel lastSavedAt={lastSavedAt} inline />
      </span>,
    )

    const line = container.firstElementChild

    expect(line?.childElementCount).toBe(0)
    expect(line?.textContent).toBe(AUTO_SAVING)
  })
})
