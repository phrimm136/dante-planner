import { beforeAll, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

import enPlanner from '@static/i18n/EN/planner.json'
import krPlanner from '@static/i18n/KR/planner.json'
import i18n from '@/lib/i18n'

import { DeckImportDialog } from '../DeckImportDialog'
import type { ImportDraft } from '../../../hooks/useDeckClipboard'
import type { DecodedDeck } from '../../../lib/deckCode'

const CLEAN_DECK: DecodedDeck = { equipment: {}, deploymentOrder: [3, 4], warnings: [] }

const WARNED_DECK: DecodedDeck = {
  equipment: {},
  deploymentOrder: [],
  warnings: [
    { kind: 'identity', sinner: 'YiSang' },
    { kind: 'ego', sinner: 'Faust', grade: 'ZAYIN' },
  ],
}

const DRAFTS = {
  empty: { text: '', verdict: { kind: 'empty' } },
  invalid: { text: 'not a code', verdict: { kind: 'invalid' } },
  valid: { text: 'code', verdict: { kind: 'valid', deck: CLEAN_DECK } },
  warned: { text: 'code', verdict: { kind: 'valid', deck: WARNED_DECK } },
} satisfies Record<string, ImportDraft>

beforeAll(async () => {
  await i18n.changeLanguage('EN')
})

function renderDialog(draft: ImportDraft) {
  const onApply = vi.fn<(deck: DecodedDeck) => void>()
  const onClose = vi.fn<() => void>()
  const onTextChange = vi.fn<(text: string) => void>()
  render(
    <DeckImportDialog
      draft={draft}
      onTextChange={onTextChange}
      onApply={onApply}
      onClose={onClose}
    />,
  )
  return { onApply, onClose, onTextChange }
}

function applyButton() {
  return screen.getByRole('button', { name: 'Apply' })
}

describe('DeckImportDialog', () => {
  it('renders nothing while the draft is null', () => {
    render(
      <DeckImportDialog draft={null} onTextChange={vi.fn()} onApply={vi.fn()} onClose={vi.fn()} />,
    )

    expect(screen.queryByText('Import Deck Code')).not.toBeInTheDocument()
  })

  it('shows the title, description and a focused textarea with the placeholder, and no X', async () => {
    renderDialog(DRAFTS.empty)

    expect(screen.getByText('Import Deck Code')).toBeInTheDocument()
    expect(screen.getByText('Paste the copied deck code below')).toBeInTheDocument()
    const textarea = screen.getByTestId('deck-import-text')
    expect(textarea).toHaveAttribute('placeholder', 'Deck code')
    await waitFor(() => expect(textarea).toHaveFocus())
    expect(document.querySelector('[data-slot="dialog-close"]')).toBeNull()
  })

  it('renders close then apply in DOM order', () => {
    renderDialog(DRAFTS.empty)

    expect(screen.getAllByRole('button').map((button) => button.textContent)).toEqual([
      'Close',
      'Apply',
    ])
  })

  it('shows no status and a disabled apply for an empty draft', () => {
    renderDialog(DRAFTS.empty)

    expect(screen.queryByText('Invalid deck code')).not.toBeInTheDocument()
    expect(screen.queryByText('Apply this deck code?')).not.toBeInTheDocument()
    expect(applyButton()).toBeDisabled()
  })

  it('shows the error and a disabled apply for an invalid draft', () => {
    renderDialog(DRAFTS.invalid)

    expect(screen.getByText('Invalid deck code')).toBeInTheDocument()
    expect(applyButton()).toBeDisabled()
  })

  it('shows the confirmation and an enabled apply for a valid draft', () => {
    renderDialog(DRAFTS.valid)

    expect(screen.getByText('Apply this deck code?')).toBeInTheDocument()
    expect(applyButton()).toBeEnabled()
  })

  it('lists each warning by translated sinner name and grade', () => {
    renderDialog(DRAFTS.warned)

    expect(screen.getByText('Warnings')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Unknown identity for Yi Sang, replaced with the default',
      'Unknown ZAYIN E.G.O for Faust, replaced with the default',
    ])
    expect(applyButton()).toBeEnabled()
  })

  it('hands the decoded deck to onApply once and leaves closing to the caller', () => {
    const { onApply, onClose } = renderDialog(DRAFTS.valid)

    fireEvent.click(applyButton())

    expect(onApply).toHaveBeenCalledTimes(1)
    expect(onApply).toHaveBeenCalledWith(CLEAN_DECK)
    expect(onClose).not.toHaveBeenCalled()
  })

  it('calls onClose from the close button', () => {
    const { onClose } = renderDialog(DRAFTS.valid)

    fireEvent.click(screen.getByRole('button', { name: 'Close' }))

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('reports typed text through onTextChange', () => {
    const { onTextChange } = renderDialog(DRAFTS.empty)

    fireEvent.change(screen.getByTestId('deck-import-text'), { target: { value: 'abc' } })

    expect(onTextChange).toHaveBeenCalledWith('abc')
  })
})

describe('deck import dialog text', () => {
  const KEYS = [
    'importDescription',
    'importPlaceholder',
    'importWarningIdentity',
    'importWarningEgo',
  ] as const

  it.each([
    ['EN', enPlanner],
    ['KR', krPlanner],
  ])('%s has no full stop at the end of the new keys', (_language, bundle) => {
    for (const key of KEYS) {
      expect(bundle.deckBuilder[key]).not.toMatch(/[.。]$/)
    }
  })
})
