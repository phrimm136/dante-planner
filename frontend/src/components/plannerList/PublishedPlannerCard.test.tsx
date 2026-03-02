/**
 * PublishedPlannerCard.test.tsx
 *
 * Tests for PublishedPlannerCard stats row display.
 * Verifies that upvotes, view count, and comment count are all rendered.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { PublicPlanner } from '@/types/PlannerListTypes'

// Mock dependencies BEFORE importing component
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
}))

vi.mock('@/lib/assetPaths', () => ({
  getKeywordIconPath: (keyword: string) => `/icons/${keyword}.webp`,
}))

vi.mock('@/lib/formatDate', () => ({
  formatPlannerDate: (date: string) => date,
}))

vi.mock('@/lib/formatUsername', () => ({
  formatUsername: () => 'W_CORP#test1',
}))

vi.mock('@/lib/utils', () => ({
  cn: (...classes: (string | undefined | null | false)[]) => classes.filter(Boolean).join(' '),
}))

// Import AFTER mocking
import { PublishedPlannerCard } from './PublishedPlannerCard'

function createMockPlanner(overrides: Partial<PublicPlanner> = {}): PublicPlanner {
  return {
    id: '00000000-0000-0000-0000-000000000001',
    title: 'Test Planner',
    plannerType: 'MIRROR_DUNGEON',
    category: '5F',
    selectedKeywords: [],
    upvotes: 10,
    viewCount: 200,
    commentCount: 5,
    authorUsernameEpithet: 'W_CORP',
    authorUsernameSuffix: 'test1',
    createdAt: '2024-01-01T00:00:00.000Z',
    lastModifiedAt: null,
    hasUpvoted: null,
    isBookmarked: null,
    ...overrides,
  }
}

describe('PublishedPlannerCard', () => {
  describe('Stats Row', () => {
    it('renders upvote count', () => {
      render(<PublishedPlannerCard planner={createMockPlanner({ upvotes: 42 })} />)
      expect(screen.getByText('42')).toBeInTheDocument()
    })

    it('renders view count', () => {
      render(<PublishedPlannerCard planner={createMockPlanner({ viewCount: 999 })} />)
      expect(screen.getByText('999')).toBeInTheDocument()
    })

    it('renders comment count', () => {
      render(<PublishedPlannerCard planner={createMockPlanner({ commentCount: 7 })} />)
      expect(screen.getByText('7')).toBeInTheDocument()
    })

    it('renders comment count of zero', () => {
      render(<PublishedPlannerCard planner={createMockPlanner({ commentCount: 0 })} />)
      expect(screen.getByText('0')).toBeInTheDocument()
    })

    it('renders all three stats together', () => {
      render(
        <PublishedPlannerCard
          planner={createMockPlanner({ upvotes: 10, viewCount: 200, commentCount: 5 })}
        />,
      )
      expect(screen.getByText('10')).toBeInTheDocument()
      expect(screen.getByText('200')).toBeInTheDocument()
      expect(screen.getByText('5')).toBeInTheDocument()
    })
  })

  describe('Keywords Display', () => {
    it('renders keyword icons', () => {
      render(<PublishedPlannerCard planner={createMockPlanner({ selectedKeywords: ['Burn', 'Slash'] })} />)
      expect(screen.getByAltText('Burn')).toBeInTheDocument()
      expect(screen.getByAltText('Slash')).toBeInTheDocument()
    })

    it('shows +N overflow beyond 3 keywords', () => {
      render(
        <PublishedPlannerCard
          planner={createMockPlanner({ selectedKeywords: ['Burn', 'Slash', 'Pierce', 'Blunt', 'Rupture'] })}
        />,
      )
      expect(screen.getByText('+2')).toBeInTheDocument()
    })
  })
})
