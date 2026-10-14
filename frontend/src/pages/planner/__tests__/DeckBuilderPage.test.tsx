import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { usePlannerEditorStore } from '../stores/usePlannerEditorStore'
import DeckBuilderPage from '../DeckBuilderPage'

vi.mock('../hooks/useDeckClipboard', () => ({
  useDeckClipboard: () => ({
    handleImport: vi.fn(),
    handleExport: vi.fn(),
    pendingImport: null,
    clearPending: vi.fn(),
  }),
}))

vi.mock('../components/deckBuilder/DeckBuilderSummary', () => ({
  StoreBoundDeckBuilderSummary: () => {
    const level = usePlannerEditorStore((s) => s.equipment['1']?.identity.level)
    return <div data-testid="default-level">{level}</div>
  },
}))

vi.mock('../components/deckBuilder/DeckBuilderPane', () => ({
  DeckBuilderPane: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('../components/deckBuilder/DeckBuilderContent', () => ({
  StoreBoundDeckBuilderContent: () => {
    const maxLevel = usePlannerEditorStore((s) => s.identityMaxLevel)
    return <div data-testid="max-level">{maxLevel}</div>
  },
}))

vi.mock('../components/deckBuilder/DeckImportConfirmDialog', () => ({
  DeckImportConfirmDialog: () => null,
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

    expect((await screen.findByTestId('max-level')).textContent).toBe('60')
  })

  it("equips the default deck at the current season's identity level", async () => {
    renderPage()

    expect((await screen.findByTestId('default-level')).textContent).toBe('60')
  })
})
