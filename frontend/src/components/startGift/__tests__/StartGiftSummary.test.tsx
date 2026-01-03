/**
 * StartGiftSummary.test.tsx
 *
 * Unit tests for StartGiftSummary component.
 * Tests empty state, selected state, EA counter, and accessibility.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StartGiftSummary } from '../StartGiftSummary'
import type { StartBuff } from '@/types/StartBuffTypes'
import type { EGOGiftSpec, EGOGiftNameList } from '@/types/EGOGiftTypes'

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'pages.plannerMD.startGift': 'Start Gift',
        'pages.plannerMD.selectStartGift': 'Select a start gift',
        'pages.plannerMD.noGiftSelected': 'No gift selected',
        'pages.plannerMD.giftSelection': 'EA',
      }
      return translations[key] ?? key
    },
    i18n: { language: 'EN' },
  }),
}))

// Mock hooks
const mockBuffs: StartBuff[] = [
  {
    id: '100',
    baseId: 100,
    level: 1,
    name: 'Base Buff',
    cost: 0,
    effects: [],
    iconSpriteId: 'icon100',
  },
  {
    id: '200',
    baseId: 100,
    level: 2,
    name: 'Enhanced Buff',
    cost: 10,
    effects: [{ type: 'ADDITIONAL_START_EGO_GIFT_SELECT', value: 1, isTypoExist: false }],
    iconSpriteId: 'icon200',
  },
]

const mockSpec: Record<string, EGOGiftSpec> = {
  '9001': {
    tag: ['TIER_1'] as EGOGiftSpec['tag'],
    keyword: 'Burn',
    attributeType: 'Red',
    themePack: [],
  },
  '9002': {
    tag: ['TIER_2'] as EGOGiftSpec['tag'],
    keyword: 'Burn',
    attributeType: 'Red',
    themePack: [],
  },
}

const mockI18n: EGOGiftNameList = {
  '9001': 'Burning Gift 1',
  '9002': 'Burning Gift 2',
}

vi.mock('@/hooks/useStartBuffData', () => ({
  useStartBuffData: () => ({
    data: mockBuffs,
    i18n: {},
  }),
  getBuffById: (buffs: StartBuff[] | undefined, id: number) =>
    buffs?.find((b) => b.id === String(id)),
}))

vi.mock('@/hooks/useEGOGiftListData', () => ({
  useEGOGiftListData: () => ({
    spec: mockSpec,
    i18n: mockI18n,
  }),
}))

// Mock PlannerSection to simplify testing
vi.mock('@/components/common/PlannerSection', () => ({
  PlannerSection: ({ title, children }: { title: string; children: React.ReactNode }) => (
    <section data-testid="planner-section">
      <h2>{title}</h2>
      <div>{children}</div>
    </section>
  ),
}))

// Mock EGOGiftCard to simplify testing
vi.mock('@/components/egoGift/EGOGiftCard', () => ({
  EGOGiftCard: ({ gift }: { gift: { id: string; name: string } }) => (
    <div data-testid={`gift-card-${gift.id}`}>{gift.name}</div>
  ),
}))

// Mock assetPaths
vi.mock('@/lib/assetPaths', () => ({
  getStatusEffectIconPath: (keyword: string) => `/icons/${keyword}.webp`,
}))

describe('StartGiftSummary', () => {
  const defaultProps = {
    mdVersion: 6 as const,
    selectedBuffIds: new Set<number>(),
    selectedKeyword: null,
    selectedGiftIds: new Set<string>(),
    onClick: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('empty state (UT1)', () => {
    it('renders placeholder with dashed border when no selection', () => {
      render(<StartGiftSummary {...defaultProps} />)

      // Check placeholder text
      expect(screen.getByText('Select a start gift')).toBeInTheDocument()

      // Check EA counter shows 0/1
      expect(screen.getByText('0/1')).toBeInTheDocument()
    })

    it('renders section title', () => {
      render(<StartGiftSummary {...defaultProps} />)

      expect(screen.getByText('Start Gift')).toBeInTheDocument()
    })

    it('has dashed border container', () => {
      const { container } = render(<StartGiftSummary {...defaultProps} />)

      const dashedContainer = container.querySelector('.border-dashed')
      expect(dashedContainer).toBeInTheDocument()
    })
  })

  describe('selected state (UT2)', () => {
    it('renders keyword icon and gift cards when selection exists', () => {
      const props = {
        ...defaultProps,
        selectedKeyword: 'Burn',
        selectedGiftIds: new Set(['9001']),
      }

      render(<StartGiftSummary {...props} />)

      // Check keyword icon is rendered
      const icon = screen.getByRole('img')
      expect(icon).toHaveAttribute('src', '/icons/Burn.webp')
      expect(icon).toHaveAttribute('alt', 'Burn')

      // Check gift card is rendered
      expect(screen.getByTestId('gift-card-9001')).toBeInTheDocument()
      expect(screen.getByText('Burning Gift 1')).toBeInTheDocument()
    })

    it('renders multiple gift cards', () => {
      const props = {
        ...defaultProps,
        selectedKeyword: 'Burn',
        selectedGiftIds: new Set(['9001', '9002']),
      }

      render(<StartGiftSummary {...props} />)

      expect(screen.getByTestId('gift-card-9001')).toBeInTheDocument()
      expect(screen.getByTestId('gift-card-9002')).toBeInTheDocument()
    })

    it('does not render placeholder when selection exists', () => {
      const props = {
        ...defaultProps,
        selectedKeyword: 'Burn',
        selectedGiftIds: new Set(['9001']),
      }

      render(<StartGiftSummary {...props} />)

      expect(screen.queryByText('Select a start gift')).not.toBeInTheDocument()
    })
  })

  describe('EA counter format (UT3)', () => {
    it('shows 0/1 when no buffs selected', () => {
      render(<StartGiftSummary {...defaultProps} />)

      expect(screen.getByText('0/1')).toBeInTheDocument()
    })

    it('shows {selected}/{max} format with selection', () => {
      const props = {
        ...defaultProps,
        selectedKeyword: 'Burn',
        selectedGiftIds: new Set(['9001']),
      }

      render(<StartGiftSummary {...props} />)

      expect(screen.getByText('1/1')).toBeInTheDocument()
    })

    it('increases max when buff with ADDITIONAL_START_EGO_GIFT_SELECT is selected', () => {
      const props = {
        ...defaultProps,
        selectedBuffIds: new Set([200]), // Enhanced buff with +1 gift select
        selectedKeyword: 'Burn',
        selectedGiftIds: new Set(['9001']),
      }

      render(<StartGiftSummary {...props} />)

      // Base 1 + 1 from buff effect = 2
      expect(screen.getByText('1/2')).toBeInTheDocument()
    })
  })

  describe('click and keyboard handling (UT4)', () => {
    it('calls onClick when clicked', async () => {
      const onClick = vi.fn()
      const user = userEvent.setup()

      render(<StartGiftSummary {...defaultProps} onClick={onClick} />)

      const clickableArea = screen.getByRole('button')
      await user.click(clickableArea)

      expect(onClick).toHaveBeenCalledTimes(1)
    })

    it('calls onClick when Enter is pressed', async () => {
      const onClick = vi.fn()
      const user = userEvent.setup()

      render(<StartGiftSummary {...defaultProps} onClick={onClick} />)

      const clickableArea = screen.getByRole('button')
      clickableArea.focus()
      await user.keyboard('{Enter}')

      expect(onClick).toHaveBeenCalledTimes(1)
    })

    it('calls onClick when Space is pressed', async () => {
      const onClick = vi.fn()
      const user = userEvent.setup()

      render(<StartGiftSummary {...defaultProps} onClick={onClick} />)

      const clickableArea = screen.getByRole('button')
      clickableArea.focus()
      await user.keyboard(' ')

      expect(onClick).toHaveBeenCalledTimes(1)
    })

    it('has correct accessibility attributes', () => {
      render(<StartGiftSummary {...defaultProps} />)

      const clickableArea = screen.getByRole('button')
      expect(clickableArea).toHaveAttribute('tabIndex', '0')
    })
  })

  describe('edge cases', () => {
    it('shows keyword with "no gift selected" message when keyword is selected without gifts', () => {
      const props = {
        ...defaultProps,
        selectedKeyword: 'Burn',
        selectedGiftIds: new Set<string>(), // No gifts selected
      }

      render(<StartGiftSummary {...props} />)

      // Keyword icon should be visible
      const icon = screen.getByRole('img')
      expect(icon).toHaveAttribute('src', '/icons/Burn.webp')

      // Should show "no gift selected" message
      expect(screen.getByText('No gift selected')).toBeInTheDocument()

      // EA counter should show 0/1
      expect(screen.getByText('0/1')).toBeInTheDocument()

      // Should NOT show the empty placeholder
      expect(screen.queryByText('Select a start gift')).not.toBeInTheDocument()
    })
  })
})
