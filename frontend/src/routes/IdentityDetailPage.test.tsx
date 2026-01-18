import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Suspense } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { MAX_LEVEL } from '@/lib/constants'
import IdentityDetailPage from './IdentityDetailPage'

// Mock react-i18next with proper i18n instance and initReactI18next
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, fallback?: string) => {
        const translations: Record<string, string> = {
          'skill.skill1': 'Skill 1',
          'skill.skill2': 'Skill 2',
          'skill.skill3': 'Skill 3',
          'skill.defense': 'Defense',
          'passive.battle': 'Battle Passives',
          'passive.support': 'Support Passives',
          'passive.resonance': 'Resonance',
          'passive.stock': 'Stock',
          'sanity.title': 'Sanity',
          'sanity.panicType': 'Panic Type',
          'sanity.panicEffect': 'Panic Effect',
          'sanity.increaseHeader': 'Factors increasing Sanity',
          'sanity.decreaseHeader': 'Factors decreasing Sanity',
          'identity.unitKeyword': 'Unit Keywords',
        }
        return translations[key] ?? fallback ?? key
      },
      i18n: { language: 'EN' },
    }),
  }
})

// Mock static data based on identity 10101 (LCB Sinner - base identity)
const mockIdentityData10101 = {
  updatedDate: 20230227,
  skillKeywordList: ['Sinking'],
  panicType: 9999,
  season: 0,
  rank: 1,
  hp: { defaultStat: 72, incrementByLevel: 2.48 },
  defCorrection: -2,
  minSpeedList: [4, 4, 4, 4],
  maxSpeedList: [6, 7, 8, 8],
  unitKeywordList: ['BASE_APPEARANCE', 'SMALL', 'LIMBUS_COMPANY', 'LIMBUS_COMPANY_LCB'],
  staggerList: [65, 35, 15],
  ResistInfo: { SLASH: 2, PENETRATE: 0.5, HIT: 1 },
  mentalConditionInfo: {
    add: ['OnWinDuelAsParryingCountMultiply10AndPlus20Percent'],
    min: ['OnDieAllyAsLevelRatio10'],
  },
  skills: {
    skill1: [{ id: 1010101, skillData: [{ attributeType: 'AZURE', atkType: 'SLASH' }] }],
    skill2: [{ id: 1010102, skillData: [{ attributeType: 'VIOLET', atkType: 'PENETRATE' }] }],
    skill3: [{ id: 1010103, skillData: [{}, {}, { attributeType: 'AMBER', atkType: 'SLASH' }, {}] }],
    skillDef: [{ id: 1010104, skillData: [{ attributeType: 'NEUTRAL', atkType: 'NONE' }] }],
  },
  passives: {
    battlePassiveList: [[1010101], [], [], []], // Only uptie 1 has battle passive
    supportPassiveList: [[], [], [1010121], []], // Only uptie 3 has support passive
    conditions: {
      '1010101': { type: 'RESONANCE', values: { AZURE: 4 } },
      '1010121': { type: 'STOCK', values: { AZURE: 4 } },
    },
  },
}

const mockIdentityI18n10101 = {
  name: 'LCB\nSinner',
  skills: {
    '1010101': { name: 'Deflect', descs: [{ desc: '', coinDescs: ['Inflict 1 Sinking'] }] },
    '1010102': { name: 'End-stop Stab', descs: [{ desc: '', coinDescs: [] }] },
    '1010103': { name: 'Enjamb', descs: [{ desc: '', coinDescs: [] }] },
    '1010104': { name: 'Guard', descs: [{ desc: '', coinDescs: [] }] },
  },
  passives: {
    '1010101': { name: 'Information Relay', desc: 'Apply 1 Damage Up to 2 allies' },
    '1010121': { name: 'Information Neutralization', desc: 'Heal 10 SP for 1 ally' },
  },
}

// Mock static data based on identity 10114 (Heishou Pack - complex identity)
const mockIdentityData10114 = {
  updatedDate: 20250828,
  skillKeywordList: ['Burst', 'Vibration'],
  panicType: 9999,
  season: 6,
  rank: 3,
  hp: { defaultStat: 66, incrementByLevel: 3.41 },
  defCorrection: 5,
  minSpeedList: [3, 3, 4, 4],
  maxSpeedList: [5, 6, 7, 7],
  unitKeywordList: ['SMALL', 'BLACK_BEAST', 'BLACK_BEAST_CHIEF', 'FAMILY_GA', 'BLACK_BEAST_HORSE', 'H_CORP'],
  staggerList: [60, 30],
  ResistInfo: { SLASH: 0.5, PENETRATE: 2, HIT: 1 },
  mentalConditionInfo: {
    add: ['OnWinDuelAsParryingCountMultiply10AndPlus20Percent'],
    min: ['OnDieAllyAsLevelRatio10'],
  },
  skills: {
    skill1: [{ id: 1011401, skillData: [{ attributeType: 'AMBER', atkType: 'SLASH' }] }],
    skill2: [{ id: 1011402, skillData: [{ attributeType: 'VIOLET', atkType: 'SLASH' }] }],
    skill3: [{ id: 1011403, skillData: [{}, {}, { attributeType: 'SHAMROCK', atkType: 'SLASH' }, {}] }],
    skillDef: [{ id: 1011404, skillData: [{ attributeType: 'NEUTRAL', atkType: 'NONE' }] }],
  },
  passives: {
    battlePassiveList: [[1011402, 1011403], [1011402, 1011403, 1011401], [], [1011402, 1011403, 1011411]],
    supportPassiveList: [[], [], [1011421], []],
    conditions: {
      '1011401': { type: 'STOCK', values: { SHAMROCK: 5 } },
      '1011421': { type: 'STOCK', values: { SHAMROCK: 4 } },
    },
  },
}

const mockIdentityI18n10114 = {
  name: 'Heishou Pack -\nWu Branch Adept',
  skills: {
    '1011401': { name: 'Cut Down and Trample', descs: [{ desc: 'Test skill', coinDescs: [] }] },
    '1011402': { name: 'Crescent Blade Strike', descs: [{ desc: 'Test skill 2', coinDescs: [] }] },
    '1011403': { name: "Cavalry's Vanguard Charge", descs: [{ desc: 'Test skill 3', coinDescs: [] }] },
    '1011404': { name: 'Preparation Afore the Charge', descs: [{ desc: 'Defense skill', coinDescs: [] }] },
  },
  passives: {
    '1011402': { name: 'Passive 1', desc: 'Passive description 1' },
    '1011403': { name: 'Passive 2', desc: 'Passive description 2' },
    '1011411': { name: 'Passive 3', desc: 'Passive description 3' },
    '1011421': { name: 'Support Passive', desc: 'Support passive description' },
    '1011401': { name: 'Battle Passive', desc: 'Battle passive description' },
  },
}

// Active mock data - can be switched per test
let mockIdentityData = mockIdentityData10114
let mockIdentityI18n = mockIdentityI18n10114

// Mock the hooks
vi.mock('@tanstack/react-router', () => ({
  useParams: () => ({ id: '10114' }),
}))

vi.mock('@/hooks/useIdentityDetailData', () => ({
  useIdentityDetailSpec: () => mockIdentityData,
  useIdentityDetailI18n: () => mockIdentityI18n,
  // Keep backward compatible export
  useIdentityDetailData: () => ({
    spec: mockIdentityData,
    i18n: mockIdentityI18n,
  }),
}))

// Mock the useIsBreakpoint hook to simulate desktop by default
vi.mock('@/hooks/use-is-breakpoint', () => ({
  useIsBreakpoint: vi.fn(() => false), // false = not mobile (desktop)
}))

// Mock usePanicInfo hook (used in PanicTypeSectionI18n)
vi.mock('@/hooks/usePanicInfo', () => ({
  usePanicInfo: () => ({
    data: {
      '9999': { name: 'Standard Panic', panicDesc: 'Standard panic effect' },
    },
  }),
  getPanicEntry: (_panicInfo: Record<string, unknown>, panicType: number) => {
    if (panicType === 9999) {
      return { name: 'Standard Panic', panicDesc: 'Standard panic effect' }
    }
    return null
  },
}))

// Mock useTraitsI18n hook (used in TraitsDisplay)
vi.mock('@/hooks/useTraitsI18n', () => ({
  useTraitsI18n: () => ({
    LIMBUS_COMPANY: 'Limbus Company',
    LIMBUS_COMPANY_LCB: 'LCB',
    BLACK_BEAST: 'Black Beast',
    BLACK_BEAST_CHIEF: 'Black Beast Chief',
    FAMILY_GA: 'Family Ga',
    BLACK_BEAST_HORSE: 'Black Beast Horse',
    H_CORP: 'H Corp',
  }),
}))

// Mock asset paths - use importOriginal for complete mock
vi.mock('@/lib/assetPaths', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/assetPaths')>()
  return {
    ...actual,
    // Override specific paths if needed for testing
  }
})

// Mock sanity condition formatter
vi.mock('@/lib/sanityConditionFormatter', () => ({
  useSanityConditionFormatter: () => ({
    formatAll: (conditions: string[]) => conditions.map(() => 'Formatted condition'),
  }),
}))

// Mock TraitsDisplay component to avoid fetch
vi.mock('@/components/identity/TraitsDisplay', () => ({
  TraitsDisplay: ({ traits }: { traits: string[] }) => (
    <div data-testid="traits-display">Traits: {traits.length}</div>
  ),
}))

// Mock fetch for hooks that use fetch
beforeEach(() => {
  vi.spyOn(global, 'fetch').mockImplementation((url) => {
    const urlStr = url.toString()
    // Return mock data for various fetch endpoints
    if (urlStr.includes('skillTag.json')) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({}),
      } as Response)
    }
    if (urlStr.includes('unitKeywords.json')) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({
          LIMBUS_COMPANY: 'Limbus Company',
          BLACK_BEAST: 'Black Beast',
        }),
      } as Response)
    }
    return Promise.resolve({
      ok: true,
      json: () => Promise.resolve({}),
    } as Response)
  })
})

// Helper to render with providers
function renderWithProviders(ui: React.ReactElement) {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={<div data-testid="page-skeleton">Loading...</div>}>
        {ui}
      </Suspense>
    </QueryClientProvider>
  )
}

describe('IdentityDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders identity name', async () => {
    renderWithProviders(<IdentityDetailPage />)

    await waitFor(() => {
      expect(screen.getByText(/Heishou Pack/i)).toBeDefined()
    })
  })

  it('renders all 4 uptie buttons in selector', async () => {
    renderWithProviders(<IdentityDetailPage />)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /tier 2/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /tier 3/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /tier 4/i })).toBeDefined()
    })
  })

  it('renders level display with default MAX_LEVEL', async () => {
    renderWithProviders(<IdentityDetailPage />)

    await waitFor(() => {
      // Level label should be present in 'Lv. X' format
      expect(screen.getByText(`Lv. ${MAX_LEVEL}`)).toBeDefined()
      // Slider should exist
      expect(screen.getByRole('slider')).toBeDefined()
    })
  })

  it('renders skill slot buttons', async () => {
    renderWithProviders(<IdentityDetailPage />)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /skill 1/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /skill 2/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /skill 3/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /defense/i })).toBeDefined()
    })
  })

  it('updates uptie when tier button clicked', async () => {
    renderWithProviders(<IdentityDetailPage />)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
    })

    // Click tier 1 button
    fireEvent.click(screen.getByRole('button', { name: /tier 1/i }))

    // The page should re-render - verify the passives section header exists
    await waitFor(() => {
      const passiveElements = screen.getAllByText(/Passives/i)
      expect(passiveElements.length).toBeGreaterThan(0)
    })
  })

  it('renders slider for level adjustment', async () => {
    renderWithProviders(<IdentityDetailPage />)

    await waitFor(() => {
      // The slider component should be rendered
      expect(screen.getByRole('slider')).toBeDefined()
      // Level value from constants (appears in multiple places)
      const levelElements = screen.getAllByText(String(MAX_LEVEL))
      expect(levelElements.length).toBeGreaterThan(0)
    })
  })

  it('renders sanity section in right column', async () => {
    renderWithProviders(<IdentityDetailPage />)

    await waitFor(() => {
      // Sanity should be in the page (moved to right column)
      const sanityElements = screen.getAllByText(/Sanity/i)
      expect(sanityElements.length).toBeGreaterThan(0)
      expect(screen.getByText(/Panic Type/i)).toBeDefined()
    })
  })

  it('switches skill slots when buttons clicked', async () => {
    renderWithProviders(<IdentityDetailPage />)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /skill 2/i })).toBeDefined()
    })

    // Click Skill 2 button
    fireEvent.click(screen.getByRole('button', { name: /skill 2/i }))

    // Skill 2 button should now be active (has inline background color style)
    await waitFor(() => {
      const skill2Button = screen.getByRole('button', { name: /skill 2/i })
      expect(skill2Button.style.backgroundColor).toBeTruthy()
    })
  })

  describe('with LCB Sinner data (10101)', () => {
    beforeEach(() => {
      // Switch to LCB Sinner mock data
      mockIdentityData = mockIdentityData10101
      mockIdentityI18n = mockIdentityI18n10101
    })

    afterEach(() => {
      // Reset to default mock data
      mockIdentityData = mockIdentityData10114
      mockIdentityI18n = mockIdentityI18n10114
    })

    it('renders LCB Sinner name', async () => {
      renderWithProviders(<IdentityDetailPage />)

      await waitFor(() => {
        expect(screen.getByText(/LCB/i)).toBeDefined()
        expect(screen.getByText(/Sinner/i)).toBeDefined()
      })
    })

    it('shows different passive distribution than Heishou Pack', async () => {
      renderWithProviders(<IdentityDetailPage />)

      // At uptie 4 (default), LCB Sinner has no battle passives (empty array at index 3)
      // But Heishou Pack has passives at uptie 4
      // The page should still render the passives section
      await waitFor(() => {
        const passiveElements = screen.getAllByText(/Passives/i)
        expect(passiveElements.length).toBeGreaterThan(0)
      })
    })
  })
})
