import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { usePlannerEditorStore } from '../stores/usePlannerEditorStore'
import DeckBuilderPage from '../DeckBuilderPage'
import type { DeckBuilderDeck } from '../types/DeckTypes'

vi.mock('../components/deckBuilder/DeckBuilderBlock', () => ({
  DeckBuilderBlock: ({ deck }: { deck: DeckBuilderDeck }) => {
    const maxLevel = usePlannerEditorStore((s) => s.identityMaxLevel)
    return (
      <>
        <div data-testid="default-level">{deck.equipment['1']?.identity.level}</div>
        <div data-testid="max-level">{maxLevel}</div>
      </>
    )
  },
}))

describe('DeckBuilderPage', () => {
  function renderPage() {
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <DeckBuilderPage />
      </QueryClientProvider>,
    )
  }

  it("caps the deck builder at the current season's identity level", async () => {
    renderPage()

    expect((await screen.findByTestId('max-level')).textContent).toBe('65')
  })

  it("equips the default deck at the current season's identity level", async () => {
    renderPage()

    expect((await screen.findByTestId('default-level')).textContent).toBe('65')
  })
})
