import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { ComprehensiveGiftGridTracker } from './ComprehensiveGiftGridTracker'

// Mock hooks
vi.mock('@/hooks/useEGOGiftListData', () => ({
  useEGOGiftListData: () => ({
    spec: {
      gift1: {
        tag: 'ATTACK',
        keyword: 'Burn',
        attributeType: 'WRATH',
        themePack: 'pack1',
      },
      gift2: {
        tag: 'DEFENSE',
        keyword: 'Bleed',
        attributeType: 'LUST',
        themePack: 'pack2',
      },
      gift3: {
        tag: 'ATTACK',
        keyword: 'Tremor',
        attributeType: 'PRIDE',
        themePack: 'pack1',
      },
    },
    i18n: {
      gift1: 'Gift One',
      gift2: 'Gift Two',
      gift3: 'Gift Three',
    },
  }),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}))

describe('ComprehensiveGiftGridTracker', () => {
  describe('Rendering', () => {
    it('renders component without crashing', () => {
      const selectedGiftIds = new Set(['0:gift1', '0:gift2', '0:gift3'])

      const { container } = render(
        <ComprehensiveGiftGridTracker
          selectedGiftIds={selectedGiftIds}
          highlightedGiftIds={new Set()}
          doneThemePackGiftIds={new Set()}
        />
      )

      expect(container).toBeDefined()
    })

    it('renders empty state when no gifts selected', () => {
      const { container } = render(
        <ComprehensiveGiftGridTracker
          selectedGiftIds={new Set()}
          highlightedGiftIds={new Set()}
          doneThemePackGiftIds={new Set()}
        />
      )

      expect(container.textContent).toContain('pages.plannerMD.selectEgoGifts')
    })
  })

  describe('Highlighting Logic', () => {
    it('accepts highlighted gift IDs prop', () => {
      const selectedGiftIds = new Set(['0:gift1', '0:gift2'])
      const highlightedGiftIds = new Set(['0:gift1'])

      const { container } = render(
        <ComprehensiveGiftGridTracker
          selectedGiftIds={selectedGiftIds}
          highlightedGiftIds={highlightedGiftIds}
          doneThemePackGiftIds={new Set()}
        />
      )

      // Should render without errors
      expect(container).toBeDefined()
    })

    it('accepts done theme pack gift IDs prop', () => {
      const selectedGiftIds = new Set(['0:gift1', '0:gift2'])
      const doneThemePackGiftIds = new Set(['0:gift2'])

      const { container } = render(
        <ComprehensiveGiftGridTracker
          selectedGiftIds={selectedGiftIds}
          highlightedGiftIds={new Set()}
          doneThemePackGiftIds={doneThemePackGiftIds}
        />
      )

      // Should render without errors
      expect(container).toBeDefined()
    })
  })
})
