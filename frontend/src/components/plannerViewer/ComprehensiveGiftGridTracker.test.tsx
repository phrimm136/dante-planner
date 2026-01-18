import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ComprehensiveGiftGridTracker } from './ComprehensiveGiftGridTracker'
import type { SerializableFloorSelection } from '@/types/PlannerTypes'

// Mock hooks
vi.mock('@/hooks/useEGOGiftListData', () => ({
  useEGOGiftListData: () => ({
    spec: {
      gift1: {
        tag: 'ATTACK',
        keyword: 'Burn',
        attributeType: 'WRATH',
        themePack: 'pack1',
        maxEnhancement: 3,
      },
      gift2: {
        tag: 'DEFENSE',
        keyword: 'Bleed',
        attributeType: 'LUST',
        themePack: 'pack2',
        maxEnhancement: 3,
      },
      gift3: {
        tag: 'ATTACK',
        keyword: 'Tremor',
        attributeType: 'PRIDE',
        themePack: 'pack1',
        maxEnhancement: 3,
      },
    },
    i18n: {
      gift1: 'Gift One',
      gift2: 'Gift Two',
      gift3: 'Gift Three',
    },
  }),
}))

vi.mock('@/hooks/useSearchMappings', () => ({
  useSearchMappingsDeferred: () => ({
    keywordToValue: new Map(),
  }),
}))

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'EN' },
    }),
  }
})

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('ComprehensiveGiftGridTracker', () => {
  describe('Rendering', () => {
    it('renders component without crashing', () => {
      const floorSelections: SerializableFloorSelection[] = [
        { floorIndex: 0, themePackId: 'pack1', giftIds: ['0:gift1', '0:gift2', '0:gift3'] },
      ]

      const { container } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={floorSelections}
          doneMarks={{}}
          hoveredThemePackId={null}
        />,
        { wrapper: createWrapper() }
      )

      expect(container).toBeDefined()
    })

    it('renders empty state when no gifts selected', () => {
      const { container } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={[]}
          doneMarks={{}}
          hoveredThemePackId={null}
        />,
        { wrapper: createWrapper() }
      )

      expect(container.textContent).toContain('pages.plannerMD.emptyState.noEgoGifts')
    })
  })

  describe('Highlighting Logic', () => {
    it('accepts hoveredThemePackId prop', () => {
      const floorSelections: SerializableFloorSelection[] = [
        { floorIndex: 0, themePackId: 'pack1', giftIds: ['0:gift1', '0:gift2'] },
      ]

      const { container } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={floorSelections}
          doneMarks={{}}
          hoveredThemePackId="pack1"
        />,
        { wrapper: createWrapper() }
      )

      // Should render without errors
      expect(container).toBeDefined()
    })

    it('accepts doneMarks prop', () => {
      const floorSelections: SerializableFloorSelection[] = [
        { floorIndex: 0, themePackId: 'pack1', giftIds: ['0:gift1', '0:gift2'] },
      ]
      const doneMarks: Record<number, Set<string>> = {
        0: new Set(['pack1']),
      }

      const { container } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={floorSelections}
          doneMarks={doneMarks}
          hoveredThemePackId={null}
        />,
        { wrapper: createWrapper() }
      )

      // Should render without errors
      expect(container).toBeDefined()
    })
  })
})
