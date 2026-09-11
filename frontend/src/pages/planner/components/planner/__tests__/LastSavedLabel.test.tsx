/**
 * Rendering rules for the last-saved label: the span it renders, the timestamps
 * that must render nothing, and the store subscription that moves it.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, act } from '@testing-library/react'
import { LastSavedLabel } from '../LastSavedLabel'
import { createSaveStatusStore } from '../../../stores/saveStatus'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) =>
      params && 'time' in params ? `${key}:${String(params.time)}` : key,
    i18n: { language: 'EN' },
  }),
}))

const NOW = new Date('2026-08-15T00:00:00.000Z')

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

  it.each(RENDERED_CASES)('renders the span for %s', (_label, lastSavedAt, relativeTime) => {
    const { container } = render(<LastSavedLabel status={createSaveStatusStore(lastSavedAt)} />)

    expect(container.innerHTML).toBe(
      `<span class="text-sm text-muted-foreground">sync.lastSaved:${relativeTime}</span>`,
    )
  })

  it.each(EMPTY_CASES)('renders nothing for %s', (_label, lastSavedAt) => {
    const { container } = render(<LastSavedLabel status={createSaveStatusStore(lastSavedAt)} />)

    expect(container.innerHTML).toBe('')
  })

  it('follows the store without being re-rendered by a parent', () => {
    const status = createSaveStatusStore(null)
    const { container } = render(<LastSavedLabel status={status} />)
    expect(container.innerHTML).toBe('')

    act(() => {
      status.setState({ lastSavedAt: new Date(NOW.getTime() - 120_000).toISOString() })
    })

    expect(container.textContent).toBe('sync.lastSaved:2 minutes ago')
  })
})
