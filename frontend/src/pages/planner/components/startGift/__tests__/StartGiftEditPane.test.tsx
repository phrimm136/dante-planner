/**
 * StartGiftEditPane.test.tsx
 *
 * Unit tests for StartGiftEditPane component.
 * Tests dialog visibility, row rendering, EA counter, and Done button.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement } from 'react'
import type { StoreApi } from 'zustand'
import { StartGiftEditPane } from '../StartGiftEditPane'
import { usePlannerStoreApi } from '../../../stores/usePlannerStore'
import type { PlannerStore, PlannerStoreSeed } from '../../../stores/usePlannerStore'
import { SeededPlannerStore } from '../../../stores/__tests__/plannerStoreHarness'
import { asEncodedGiftId } from '@/test-utils/fixtures'
import type { EGOGiftSpec, EGOGiftNameList } from '@/shared/egoGift'
import { PLANNER_CONFIG } from '@/lib/constants'
import { buildEgoGiftSpecList } from '@/test-utils'

// Mock react-i18next
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => {
        const translations: Record<string, string> = {
          'pages.plannerMD.startEgoGift': 'Start EGO Gift',
          'pages.plannerMD.egoGiftSelection': 'EA',
          'common:done': 'Done',
        }
        return translations[key] ?? key
      },
      i18n: { language: 'EN' },
    }),
  }
})

let seed: PlannerStoreSeed = {}

function StoreProbe({ onStore }: { onStore: (store: StoreApi<PlannerStore>) => void }) {
  onStore(usePlannerStoreApi())
  return null
}

function renderInStore(ui: ReactElement) {
  let store: StoreApi<PlannerStore> | undefined
  render(
    <SeededPlannerStore mdVersion={PLANNER_CONFIG.mdCurrentVersion} initialState={seed}>
      {ui}
      <StoreProbe
        onStore={(api) => {
          store = api
        }}
      />
    </SeededPlannerStore>,
  )
  return () => {
    if (store === undefined) throw new Error('the planner store never mounted')
    return store
  }
}

const mockPools: Record<string, string[]> = {
  Burn: ['9001', '9002', '9003'],
  Bleed: ['9004', '9005', '9006'],
}

const mockSpec: Record<string, EGOGiftSpec> = buildEgoGiftSpecList({
  '9001': {
    tag: ['TIER_1'],
    keyword: 'Burn',
    battleKeywordList: [],
    attributeType: 'Red',
    themePack: [],
    maxEnhancement: 0,
  },
  '9002': {
    tag: ['TIER_2'],
    keyword: 'Burn',
    battleKeywordList: [],
    attributeType: 'Red',
    themePack: [],
    maxEnhancement: 0,
  },
  '9003': {
    tag: ['TIER_3'],
    keyword: 'Burn',
    battleKeywordList: [],
    attributeType: 'Red',
    themePack: [],
    maxEnhancement: 0,
  },
})

const mockI18n: EGOGiftNameList = {
  '9001': 'Burning Gift 1',
  '9002': 'Burning Gift 2',
  '9003': 'Burning Gift 3',
}

vi.mock('../../../hooks/useStartBuffList', () => ({
  useStartBuffListSpec: () => ({
    '100': {
      baseId: 100,
      level: 1,
      localizeId: 'buff100',
      cost: 0,
      effects: [],
      uiConfig: { iconSpriteId: 'buff100' },
    },
    '200': {
      baseId: 100,
      level: 2,
      localizeId: 'buff200',
      cost: 10,
      effects: [{ type: 'ADDITIONAL_START_EGO_GIFT_SELECT', value: 1, isTypoExist: false }],
      uiConfig: { iconSpriteId: 'buff200' },
    },
  }),
  useStartBuffListI18n: () => ({ buff100: 'Base Buff', buff200: 'Enhanced Buff' }),
}))

vi.mock('../../../hooks/useStartGiftPools', () => ({
  useStartGiftPools: () => ({
    data: mockPools,
  }),
}))

vi.mock('@/shared/egoGift/hooks/useEGOGiftListData', () => ({
  useEGOGiftListSpec: () =>
    ({
      spec: mockSpec,
      i18n: mockI18n,
    }).spec,
  useEGOGiftListI18n: () =>
    ({
      spec: mockSpec,
      i18n: mockI18n,
    }).i18n,
}))

vi.mock('../../../lib/startGiftCalculator', () => ({
  calculateMaxGiftSelection: (_buffs: unknown[], selectedBuffIds: Set<number>) => {
    // Return 2 if buff 200 is selected, else 1
    return selectedBuffIds.has(200) ? 2 : 1
  },
}))

// Mock StartGiftRow to simplify testing
vi.mock('../StartGiftRow', () => ({
  StartGiftRow: ({
    keyword,
    isRowSelected,
    onRowSelect,
  }: {
    keyword: string
    isRowSelected: boolean
    onRowSelect: (keyword: string) => void
  }) => (
    <button
      type="button"
      data-testid={`row-${keyword}`}
      data-selected={isRowSelected}
      onClick={() => {
        onRowSelect(keyword)
      }}
    >
      {keyword}
    </button>
  ),
}))

describe('StartGiftEditPane', () => {
  const defaultProps = {
    open: true,
    onOpenChange: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
    seed = {}
  })

  describe('dialog visibility (UT5)', () => {
    it('renders dialog when open=true', () => {
      renderInStore(<StartGiftEditPane {...defaultProps} open />)

      expect(screen.getByRole('dialog')).toBeInTheDocument()
      expect(screen.getByText('Start EGO Gift')).toBeInTheDocument()
    })

    it('does not render dialog when open=false', () => {
      renderInStore(<StartGiftEditPane {...defaultProps} open={false} />)

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  describe('row rendering (UT6)', () => {
    it('renders keyword rows from pools', () => {
      renderInStore(<StartGiftEditPane {...defaultProps} />)

      expect(screen.getByTestId('row-Burn')).toBeInTheDocument()
      expect(screen.getByTestId('row-Bleed')).toBeInTheDocument()
    })

    it('marks selected keyword row', () => {
      seed = { selectedGiftKeyword: 'Burn' }

      renderInStore(<StartGiftEditPane {...defaultProps} />)

      expect(screen.getByTestId('row-Burn')).toHaveAttribute('data-selected', 'true')
      expect(screen.getByTestId('row-Bleed')).toHaveAttribute('data-selected', 'false')
    })
  })

  describe('Done button (UT7)', () => {
    it('renders Done button', () => {
      renderInStore(<StartGiftEditPane {...defaultProps} />)

      expect(screen.getByRole('button', { name: 'Done' })).toBeInTheDocument()
    })

    it('calls onOpenChange(false) when Done is clicked', async () => {
      const onOpenChange = vi.fn()
      const user = userEvent.setup()

      renderInStore(<StartGiftEditPane {...defaultProps} onOpenChange={onOpenChange} />)

      await user.click(screen.getByRole('button', { name: 'Done' }))

      expect(onOpenChange).toHaveBeenCalledWith(false)
    })
  })

  describe('EA counter', () => {
    it('shows EA counter with correct format', () => {
      seed = { selectedGiftIds: new Set([asEncodedGiftId('9001')]) }

      renderInStore(<StartGiftEditPane {...defaultProps} />)

      // EA counter is in format "EA: X/Y"
      expect(screen.getByText(/1\/1/)).toBeInTheDocument()
    })

    it('shows increased max when buff with ADDITIONAL_START_EGO_GIFT_SELECT is selected', () => {
      seed = {
        selectedBuffIds: new Set([200]),
        selectedGiftIds: new Set([asEncodedGiftId('9001')]),
      }

      renderInStore(<StartGiftEditPane {...defaultProps} />)

      // Base 1 + 1 from buff effect = 2
      expect(screen.getByText(/1\/2/)).toBeInTheDocument()
    })
  })

  describe('keyword selection', () => {
    it('calls setSelectedGiftKeyword when row is clicked', async () => {
      const user = userEvent.setup()

      const getStore = renderInStore(<StartGiftEditPane {...defaultProps} />)

      await user.click(screen.getByTestId('row-Burn'))

      expect(getStore().getState().selectedGiftKeyword).toBe('Burn')
    })
  })
})
