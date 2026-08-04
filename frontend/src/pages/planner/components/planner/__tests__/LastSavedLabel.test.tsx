/**
 * Parity tests for the extracted last-saved label.
 *
 * `LegacyLastSaved` is the pre-split inline IIFE, transcribed verbatim from the
 * editor. Every row renders both and compares innerHTML, so the extraction
 * cannot have changed a byte of the emitted DOM.
 */

import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { formatDistanceToNow } from 'date-fns'
import { enUS, ja, ko, zhCN } from 'date-fns/locale'
import { LastSavedLabel } from '../LastSavedLabel'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) =>
      params && 'time' in params ? `${key}:${String(params.time)}` : key,
    i18n: { language: 'EN' },
  }),
}))

const dateFnsLocale = { EN: enUS, JP: ja, KR: ko, CN: zhCN }['EN'] ?? enUS

const t = (key: string, params?: Record<string, unknown>) =>
  params && 'time' in params ? `${key}:${String(params.time)}` : key

/** The pre-split inline suffix, rendered inside the auto-saving span. */
function LegacyInline({ lastSavedAt }: { lastSavedAt: string | null }) {
  return (
    <span className="text-sm text-muted-foreground">
      {t('pages.plannerMD.save.autoSaving')}
      {lastSavedAt &&
        (() => {
          try {
            const parsedDate = new Date(lastSavedAt)
            if (isNaN(parsedDate.getTime())) return null
            return ` - ${t('sync.lastSaved', { time: formatDistanceToNow(parsedDate, { addSuffix: true, locale: dateFnsLocale }) })}`
          } catch {
            return null
          }
        })()}
    </span>
  )
}

/** The pre-split standalone span. */
function LegacyStandalone({ lastSavedAt }: { lastSavedAt: string | null }) {
  return (
    <>
      {lastSavedAt &&
        (() => {
          try {
            const parsedDate = new Date(lastSavedAt)
            if (isNaN(parsedDate.getTime())) return null
            return (
              <span className="text-sm text-muted-foreground">
                {t('sync.lastSaved', {
                  time: formatDistanceToNow(parsedDate, {
                    addSuffix: true,
                    locale: dateFnsLocale,
                  }),
                })}
              </span>
            )
          } catch {
            return null
          }
        })()}
    </>
  )
}

const CASES: Array<[string, string | null]> = [
  ['a recent timestamp', new Date(Date.now() - 120_000).toISOString()],
  ['an old timestamp', '2020-01-01T00:00:00.000Z'],
  ['a future timestamp', new Date(Date.now() + 600_000).toISOString()],
  ['an unparseable string', 'not-a-date'],
  ['an empty string', ''],
  ['null', null],
]

describe('LastSavedLabel DOM parity', () => {
  it.each(CASES)('matches the legacy standalone span for %s', (_label, lastSavedAt) => {
    const legacy = render(<LegacyStandalone lastSavedAt={lastSavedAt} />)
    const legacyHtml = legacy.container.innerHTML
    legacy.unmount()

    const next = render(<LastSavedLabel lastSavedAt={lastSavedAt} />)

    expect(next.container.innerHTML).toBe(legacyHtml)
  })

  it.each(CASES)('matches the legacy inline suffix for %s', (_label, lastSavedAt) => {
    const legacy = render(<LegacyInline lastSavedAt={lastSavedAt} />)
    const legacyHtml = legacy.container.innerHTML
    legacy.unmount()

    const next = render(
      <span className="text-sm text-muted-foreground">
        {t('pages.plannerMD.save.autoSaving')}
        <LastSavedLabel lastSavedAt={lastSavedAt} inline />
      </span>,
    )

    expect(next.container.innerHTML).toBe(legacyHtml)
  })
})
