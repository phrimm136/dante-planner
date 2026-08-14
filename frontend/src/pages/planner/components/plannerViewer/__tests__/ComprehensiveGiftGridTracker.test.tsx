import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ComprehensiveGiftGridTracker } from '../ComprehensiveGiftGridTracker'
import { encodeGiftSelection } from '@/pages/egoGift'
import { buildEgoGiftSpecList, buildFloorSelection } from '@/test-utils'

// The card is the seam the grid renders through, so the mock surfaces the three
// values the grid computes: which gift, at which enhancement, and highlighted or not.
vi.mock('@/pages/egoGift/components/EGOGiftCard', () => ({
  EGOGiftCard: ({
    gift,
    enhancement,
    isSelected,
  }: {
    gift: { id: string; name: string }
    enhancement: number
    isSelected: boolean
  }) => (
    <div
      data-testid={`gift-card-${gift.id}`}
      data-enhancement={String(enhancement)}
      data-selected={String(isSelected)}
    >
      {gift.name}
    </div>
  ),
}))

// The factory may not import: `@/test-utils` reaches the `@/pages/egoGift` barrel, which
// re-exports this very module, so an awaiting factory waits on its own pending promise.
vi.mock('@/pages/egoGift/hooks/useEGOGiftListData', () => ({
  useEGOGiftListData: () => GIFT_CATALOG,
}))

// The spec entries pass through the boundary schema the catalog validates with, so a
// mock that drifts from the real spec shape fails here instead of rendering nothing.
const GIFT_CATALOG = {
  spec: buildEgoGiftSpecList({
    9001: { tag: ['TIER_3'], keyword: 'Burn', attributeType: 'WRATH', themePack: ['1001'] },
    9002: { tag: ['TIER_2'], keyword: 'Bleed', attributeType: 'LUST', themePack: ['1002'] },
    9003: { tag: ['TIER_1'], keyword: 'Tremor', attributeType: 'PRIDE', themePack: ['1001'] },
  }),
  i18n: {
    9001: 'Gift One',
    9002: 'Gift Two',
    9003: 'Gift Three',
  },
}

vi.mock('@/shared/filter/hooks/useSearchMappings', () => ({
  useSearchMappingsDeferred: () => ({
    keywordToValue: new Map(),
  }),
}))

vi.mock('@/pages/egoGift/components/EGOGiftTooltip', () => ({
  EGOGiftTooltip: ({ children }: { children: React.ReactElement }) => children,
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
    it('renders the empty state when no floor and no comprehensive gift is selected', () => {
      const { container } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={[]}
          comprehensiveGiftIds={[]}
          hoveredThemePackId={null}
        />,
        { wrapper: createWrapper() },
      )

      expect(container.textContent).toContain('pages.plannerMD.emptyState.noEgoGifts')
      expect(container.querySelector('[data-testid^="gift-card-"]')).toBeNull()
    })

    // The populated grid reserves a fixed scroll height. The Suspense skeleton in
    // GuideModeViewer / TrackerModeViewer hardcodes the same md:h-[178px]
    // lg:h-[416px] so the section does not jump when the grid resolves. If this
    // height changes, those two skeletons must change with it.
    it('reserves the fixed scroll height that the Suspense skeleton mirrors', () => {
      const { container } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={[buildFloorSelection({ giftIds: ['9001'] })]}
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
      const { getByTestId } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={[buildFloorSelection({ giftIds: [] })]}
          comprehensiveGiftIds={['9001']}
          hoveredThemePackId={null}
        />,
        { wrapper: createWrapper() },
      )

      expect(getByTestId('gift-card-9001')).toBeInTheDocument()
      expect(getByTestId('gift-card-9001')).toHaveTextContent('Gift One')
    })

    it('displays gifts present in a floor giftIds and in comprehensiveGiftIds', () => {
      const { getByTestId } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={[buildFloorSelection({ giftIds: ['9002'] })]}
          comprehensiveGiftIds={['9001']}
          hoveredThemePackId={null}
        />,
        { wrapper: createWrapper() },
      )

      expect(getByTestId('gift-card-9001')).toBeInTheDocument()
      expect(getByTestId('gift-card-9002')).toBeInTheDocument()
    })
  })

  describe('decode survival', () => {
    it('renders the base gift and its enhancement for an encoded selection', () => {
      const encoded = encodeGiftSelection(2, '9001')

      const { getByTestId } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={[buildFloorSelection({ giftIds: [] })]}
          comprehensiveGiftIds={[encoded]}
          hoveredThemePackId={null}
        />,
        { wrapper: createWrapper() },
      )

      expect(encoded).toBe('29001')
      expect(getByTestId('gift-card-9001')).toBeInTheDocument()
      expect(getByTestId('gift-card-9001')).toHaveAttribute('data-enhancement', '2')
    })

    it('keeps an enhanced and an unenhanced selection of the same base gift distinct', () => {
      const { getAllByTestId } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={[buildFloorSelection({ giftIds: [] })]}
          comprehensiveGiftIds={[encodeGiftSelection(0, '9001'), encodeGiftSelection(1, '9001')]}
          hoveredThemePackId={null}
        />,
        { wrapper: createWrapper() },
      )

      const enhancements = getAllByTestId('gift-card-9001')
        .map((node) => node.getAttribute('data-enhancement'))
        .sort((a, b) => String(a).localeCompare(String(b)))
      expect(enhancements).toEqual(['0', '1'])
    })
  })

  describe('Highlighting logic', () => {
    it('marks the hovered theme pack floor gifts as selected', () => {
      const floor = buildFloorSelection({ themePackId: '1001', giftIds: ['9001'] })

      const { getByTestId } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={[floor]}
          comprehensiveGiftIds={['9001', '9002']}
          hoveredThemePackId="1001"
        />,
        { wrapper: createWrapper() },
      )

      expect(getByTestId('gift-card-9001')).toHaveAttribute('data-selected', 'true')
      expect(getByTestId('gift-card-9002')).toHaveAttribute('data-selected', 'false')
    })

    it('leaves every gift unselected when no theme pack is hovered', () => {
      const floor = buildFloorSelection({ themePackId: '1001', giftIds: ['9001'] })

      const { getByTestId } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={[floor]}
          comprehensiveGiftIds={['9001']}
          hoveredThemePackId={null}
        />,
        { wrapper: createWrapper() },
      )

      expect(getByTestId('gift-card-9001')).toHaveAttribute('data-selected', 'false')
    })
  })

  describe('egoGiftDoneMarks', () => {
    it('dims a gift whose encoded id is marked done', () => {
      const { getByTestId } = render(
        <ComprehensiveGiftGridTracker
          floorSelections={[buildFloorSelection({ giftIds: [] })]}
          comprehensiveGiftIds={['9001', '9002']}
          hoveredThemePackId={null}
          egoGiftDoneMarks={new Set(['9001'])}
        />,
        { wrapper: createWrapper() },
      )

      expect(getByTestId('gift-card-9001').parentElement).toHaveClass('brightness-50')
      expect(getByTestId('gift-card-9002').parentElement).not.toHaveClass('brightness-50')
    })
  })
})
