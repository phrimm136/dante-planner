/**
 * DOM snapshot of the deck builder's dialog chrome, open and closed.
 *
 * The committed snapshots were produced by an innerHTML comparison against the
 * pane that hosted the builder itself, so they record its markup exactly.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, cleanup } from '@testing-library/react'
import type { ReactElement } from 'react'

import { DeckBuilderPane } from '../DeckBuilderPane'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'EN' } }),
  }
})

/** Radix mints a fresh id per mount, which would otherwise churn the snapshot. */
function normalized(ui: ReactElement): string {
  cleanup()
  render(ui)
  return document.body.innerHTML.replace(/radix-[^"\s]+/g, 'radix-id')
}

const MATRIX: Array<[string, () => ReactElement]> = [
  [
    'open, hosting a builder',
    () => (
      <DeckBuilderPane open onOpenChange={() => {}}>
        <div data-testid="builder" data-active="true" />
      </DeckBuilderPane>
    ),
  ],
  [
    'closed',
    () => (
      <DeckBuilderPane open={false} onOpenChange={() => {}}>
        <div data-testid="builder" data-active="false" />
      </DeckBuilderPane>
    ),
  ],
]

describe('DeckBuilderPane DOM', () => {
  it.each(MATRIX)('renders %s', (_label, make) => {
    expect(normalized(make())).toMatchSnapshot()
  })
})
