/**
 * StartGiftSummary.test.tsx
 *
 * Unit tests for StartGiftSummary component.
 * Tests empty state, selected state, and accessibility.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StartGiftSummary } from '../StartGiftSummary'
import type { EGOGiftSpec, EGOGiftNameList } from '@/types/EGOGiftTypes'

// Mock react-i18next
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => {
        const translations: Record<string, string> = {
          'pages.plannerMD.startEgoGift': 'Start EGO Gift',
          'pages.plannerMD.selectStartEgoGift': 'Select a start EGO gift',
          'pages.plannerMD.noEgoGiftSelected': 'No EGO gift selected',
        }
        return translations[key] ?? key
      },
      i18n: { language: 'EN' },
    }),
  }
})

// Mock gift spec data
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

// Mock planner editor store (safe version)
vi.mock('@/stores/usePlannerEditorStore', () => ({
  usePlannerEditorStoreSafe: (selector: (state: Record<string, unknown>) => unknown) => {
    const mockState = {
      selectedGiftKeyword: null,
      selectedGiftIds: new Set<string>(),
    }
    return selector(mockState)
  },
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
  getKeywordIconPath: (keyword: string) => `/icons/${keyword}.webp`,
}))

describe('StartGiftSummary', () => {
  const defaultProps = {
    selectedKeywordOverride: null as string | null,
    selectedGiftIdsOverride: new Set<string>(),
    onClick: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('empty state (UT1)', () => {
    it('renders placeholder with dashed border when no selection', () => {
      render(<StartGiftSummary {...defaultProps} />)

      // Check placeholder text
      expect(screen.getByText('Select a start EGO gift')).toBeInTheDocument()
    })

    it('renders section title', () => {
      render(<StartGiftSummary {...defaultProps} />)

      expect(screen.getByText('Start EGO Gift')).toBeInTheDocument()
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
        selectedKeywordOverride: 'Burn',
        selectedGiftIdsOverride: new Set(['9001']),
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
        selectedKeywordOverride: 'Burn',
        selectedGiftIdsOverride: new Set(['9001', '9002']),
      }

      render(<StartGiftSummary {...props} />)

      expect(screen.getByTestId('gift-card-9001')).toBeInTheDocument()
      expect(screen.getByTestId('gift-card-9002')).toBeInTheDocument()
    })

    it('does not render placeholder when selection exists', () => {
      const props = {
        ...defaultProps,
        selectedKeywordOverride: 'Burn',
        selectedGiftIdsOverride: new Set(['9001']),
      }

      render(<StartGiftSummary {...props} />)

      expect(screen.queryByText('Select a start EGO gift')).not.toBeInTheDocument()
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

    it('uses native button for accessibility', () => {
      render(<StartGiftSummary {...defaultProps} />)

      const button = screen.getByRole('button')
      // Native button has type="button" and doesn't need tabIndex
      expect(button).toHaveAttribute('type', 'button')
      expect(button.tagName).toBe('BUTTON')
    })
  })

  describe('edge cases', () => {
    it('shows keyword with "no EGO gift selected" message when keyword is selected without gifts', () => {
      const props = {
        ...defaultProps,
        selectedKeywordOverride: 'Burn',
        selectedGiftIdsOverride: new Set<string>(), // No gifts selected
      }

      render(<StartGiftSummary {...props} />)

      // Keyword icon should be visible
      const icon = screen.getByRole('img')
      expect(icon).toHaveAttribute('src', '/icons/Burn.webp')

      // Should show "no EGO gift selected" message
      expect(screen.getByText('No EGO gift selected')).toBeInTheDocument()

      // Should NOT show the empty placeholder
      expect(screen.queryByText('Select a start EGO gift')).not.toBeInTheDocument()
    })
  })
})
