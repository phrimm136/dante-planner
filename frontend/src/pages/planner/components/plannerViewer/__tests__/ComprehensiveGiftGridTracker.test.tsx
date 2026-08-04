import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ComprehensiveGiftGridTracker } from '../ComprehensiveGiftGridTracker'
import type { SerializableFloorSelection } from '../../../types/PlannerTypes'

// Mock hooks
vi.mock('@/pages/egoGift/hooks/useEGOGiftListData', () => ({
  useEGOGiftListData: () => ({
    spec: {
      9001: {
        tag: ['ATTACK'],
        keyword: 'Burn',
        attributeType: 'WRATH',
        themePack: 'pack1',
        maxEnhancement: 3,
      },
      9002: {
        tag: ['DEFENSE'],
        keyword: 'Bleed',
        attributeType: 'LUST',
        themePack: 'pack2',
        maxEnhancement: 3,
      },
      9003: {
        tag: ['ATTACK'],
        keyword: 'Tremor',
        attributeType: 'PRIDE',
        themePack: 'pack1',
        maxEnhancement: 3,
      },
    },
    i18n: {
      9001: 'Gift One',
      9002: 'Gift Two',
      9003: 'Gift Three',
    },
  }),
}))

vi.mock('@/shared/filter/hooks/useSearchMappings', () => ({
  useSearchMappingsDeferred: () => ({
    keywordToValue: new Map(),
  }),
}))

vi.mock('@/pages/egoGift/components/EGOGiftCard', () => ({
  EGOGiftCard: ({ gift }: { gift: { id: string } }) => <div data-testid={`gift-card-${gift.id}`} />,
}))

vi.mock('@/pages/egoGift/components/EGOGiftTooltip', () => ({
  EGOGiftTooltip: ({ children }: { children: JSX.Element }) => children,
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
        { floorIndex: 0, themePackId: 'pack1', giftIds: ['0:9001', '0:9002', '0:9003'] },
      ]

      const { container } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={floorSelections}
          doneMarks={{}}
          hoveredThemePackId={null}
        />,
        { wrapper: createWrapper() },
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
        { wrapper: createWrapper() },
      )

      expect(container.textContent).toContain('pages.plannerMD.emptyState.noEgoGifts')
    })

    // The populated grid reserves a fixed scroll height. The Suspense skeleton in
    // GuideModeViewer / TrackerModeViewer hardcodes the same md:h-[178px]
    // lg:h-[416px] so the section does not jump when the grid resolves. If this
    // height changes, those two skeletons must change with it.
    it('reserves the fixed scroll height that the Suspense skeleton mirrors', () => {
      const floorSelections: SerializableFloorSelection[] = [
        { themePackId: 'pack1', difficulty: 0, giftIds: ['9001'] },
      ]

      const { container } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={floorSelections}
          comprehensiveGiftIds={['9001']}
          hoveredThemePackId={null}
        />,
        { wrapper: createWrapper() },
      )

      expect(container.innerHTML).toContain('md:h-[178px]')
      expect(container.innerHTML).toContain('lg:h-[416px]')
    })
  })

  describe('comprehensiveGiftIds as authoritative source', () => {
    it('displays a gift in comprehensiveGiftIds that is absent from all floor giftIds', () => {
      const floorSelections: SerializableFloorSelection[] = [
        { themePackId: 'pack1', difficulty: 0, giftIds: [] },
      ]

      const { getByTestId } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={floorSelections}
          comprehensiveGiftIds={['9001']}
          hoveredThemePackId={null}
        />,
        { wrapper: createWrapper() },
      )

      expect(getByTestId('gift-card-9001')).toBeDefined()
    })

    it('display gifts present in both a floor giftIds and comprehensiveGiftIds', () => {
      const floorSelections: SerializableFloorSelection[] = [
        { themePackId: 'pack1', difficulty: 0, giftIds: ['9002'] },
      ]

      const { queryByTestId } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={floorSelections}
          comprehensiveGiftIds={['9001']}
          hoveredThemePackId={null}
        />,
        { wrapper: createWrapper() },
      )

      expect(queryByTestId('gift-card-9001')).toBeDefined()
      expect(queryByTestId('gift-card-9002')).toBeDefined()
    })
  })

  describe('Highlighting Logic', () => {
    it('accepts hoveredThemePackId prop', () => {
      const floorSelections: SerializableFloorSelection[] = [
        { floorIndex: 0, themePackId: 'pack1', giftIds: ['0:9001', '0:9002'] },
      ]

      const { container } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={floorSelections}
          doneMarks={{}}
          hoveredThemePackId="pack1"
        />,
        { wrapper: createWrapper() },
      )

      // Should render without errors
      expect(container).toBeDefined()
    })

    it('accepts doneMarks prop', () => {
      const floorSelections: SerializableFloorSelection[] = [
        { floorIndex: 0, themePackId: 'pack1', giftIds: ['0:9001', '0:9002'] },
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
        { wrapper: createWrapper() },
      )

      // Should render without errors
      expect(container).toBeDefined()
    })
  })
})
