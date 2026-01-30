/**
 * PersonalPlannerCard.test.tsx
 *
 * Tests for PersonalPlannerCard indicator state logic.
 * Verifies 6 mutually exclusive indicator states based on auth, sync, and planner status.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { PlannerSummary } from '@/types/PlannerTypes'

// Mock dependencies BEFORE importing component
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, params }: { children: React.ReactNode; to: string; params: Record<string, string> }) => (
    <a href={`${to.replace('$id', params.id)}`}>{children}</a>
  ),
  useSearch: () => ({}),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'pages.plannerList.status.draft': 'Draft',
        'pages.plannerList.status.unsynced': 'Unsynced',
        'pages.plannerList.status.unpublished': 'Unpublished',
        'pages.plannerList.status.published': 'Published',
        untitled: 'Untitled',
      }
      return translations[key] ?? key
    },
  }),
}))

vi.mock('@/lib/assetPaths', () => ({
  getKeywordIconPath: (keyword: string) => `/icons/${keyword}.webp`,
}))

vi.mock('@/lib/formatDate', () => ({
  formatPlannerDate: (date: string) => date,
}))

vi.mock('@/lib/utils', () => ({
  cn: (...classes: (string | undefined | null | false)[]) => classes.filter(Boolean).join(' '),
}))

// Import AFTER mocking
import { PersonalPlannerCard } from './PersonalPlannerCard'

/**
 * Create a mock PlannerSummary for testing
 */
function createMockPlanner(overrides: Partial<PlannerSummary> = {}): PlannerSummary {
  return {
    id: 'test-planner-id',
    title: 'Test Planner',
    plannerType: 'MIRROR_DUNGEON',
    category: '5F',
    status: 'draft',
    lastModifiedAt: '2024-01-01T00:00:00.000Z',
    savedAt: null,
    published: false,
    selectedKeywords: [],
    ...overrides,
  }
}

describe('PersonalPlannerCard', () => {
  describe('Indicator State Logic', () => {
    it('shows "Unpublished" when published=true and status=draft', () => {
      const planner = createMockPlanner({ published: true, status: 'draft' })
      render(
        <PersonalPlannerCard
          planner={planner}
          isAuthenticated={true}
          syncEnabled={true}
        />
      )
      expect(screen.getByLabelText('Unpublished changes')).toBeInTheDocument()
    })

    it('shows "Published" when published=true and status=saved', () => {
      const planner = createMockPlanner({ published: true, status: 'saved', savedAt: '2024-01-01T00:00:00.000Z' })
      render(
        <PersonalPlannerCard
          planner={planner}
          isAuthenticated={true}
          syncEnabled={true}
        />
      )
      expect(screen.getByLabelText('Published')).toBeInTheDocument()
    })

    it('shows "Unsynced" when auth + sync ON + status=draft', () => {
      const planner = createMockPlanner({ status: 'draft', published: false })
      render(
        <PersonalPlannerCard
          planner={planner}
          isAuthenticated={true}
          syncEnabled={true}
        />
      )
      expect(screen.getByLabelText('Unsynced')).toBeInTheDocument()
    })

    it('shows synced icon when auth + sync ON + status=saved', () => {
      const planner = createMockPlanner({ status: 'saved', savedAt: '2024-01-01T00:00:00.000Z', published: false })
      render(
        <PersonalPlannerCard
          planner={planner}
          isAuthenticated={true}
          syncEnabled={true}
        />
      )
      // CheckCircle icon is rendered - check for the icon container
      const indicator = screen.getByText('Test Planner').closest('div')?.parentElement
      expect(indicator?.querySelector('svg')).toBeInTheDocument()
      // Ensure no badge text is shown
      expect(screen.queryByText('Draft')).not.toBeInTheDocument()
      expect(screen.queryByText('Unsynced')).not.toBeInTheDocument()
    })

    it('shows "Draft" when guest user with draft status', () => {
      const planner = createMockPlanner({ status: 'draft' })
      render(
        <PersonalPlannerCard
          planner={planner}
          isAuthenticated={false}
          syncEnabled={null}
        />
      )
      expect(screen.getByLabelText('Draft')).toBeInTheDocument()
    })

    it('shows "Draft" when sync disabled with draft status', () => {
      const planner = createMockPlanner({ status: 'draft' })
      render(
        <PersonalPlannerCard
          planner={planner}
          isAuthenticated={true}
          syncEnabled={false}
        />
      )
      expect(screen.getByLabelText('Draft')).toBeInTheDocument()
    })

    it('shows nothing when guest with saved status', () => {
      const planner = createMockPlanner({ status: 'saved', savedAt: '2024-01-01T00:00:00.000Z' })
      render(
        <PersonalPlannerCard
          planner={planner}
          isAuthenticated={false}
          syncEnabled={null}
        />
      )
      expect(screen.queryByText('Draft')).not.toBeInTheDocument()
      expect(screen.queryByText('Unsynced')).not.toBeInTheDocument()
      expect(screen.queryByText('Published')).not.toBeInTheDocument()
    })
  })

  describe('Keywords Display', () => {
    it('renders keyword icons when keywords present', () => {
      const planner = createMockPlanner({ selectedKeywords: ['Burn', 'Slash'] })
      render(
        <PersonalPlannerCard
          planner={planner}
          isAuthenticated={false}
          syncEnabled={null}
        />
      )
      expect(screen.getByAltText('Burn')).toBeInTheDocument()
      expect(screen.getByAltText('Slash')).toBeInTheDocument()
    })

    it('shows +N overflow when more than 3 keywords', () => {
      const planner = createMockPlanner({
        selectedKeywords: ['Burn', 'Slash', 'Pierce', 'Blunt', 'Rupture'],
      })
      render(
        <PersonalPlannerCard
          planner={planner}
          isAuthenticated={false}
          syncEnabled={null}
        />
      )
      expect(screen.getByText('+2')).toBeInTheDocument()
    })

    it('shows no overflow when exactly 3 keywords', () => {
      const planner = createMockPlanner({
        selectedKeywords: ['Burn', 'Slash', 'Pierce'],
      })
      render(
        <PersonalPlannerCard
          planner={planner}
          isAuthenticated={false}
          syncEnabled={null}
        />
      )
      expect(screen.queryByText(/\+\d/)).not.toBeInTheDocument()
    })
  })

  describe('Priority Order', () => {
    it('published status takes priority over sync status', () => {
      // Even with auth+sync ON, published+draft shows "Unpublished" not "Unsynced"
      const planner = createMockPlanner({ published: true, status: 'draft' })
      render(
        <PersonalPlannerCard
          planner={planner}
          isAuthenticated={true}
          syncEnabled={true}
        />
      )
      expect(screen.getByLabelText('Unpublished changes')).toBeInTheDocument()
      expect(screen.queryByLabelText('Unsynced')).not.toBeInTheDocument()
    })
  })
})
