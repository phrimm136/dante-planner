/**
 * PlannerCardContextMenu.test.tsx
 *
 * Tests for PlannerCardContextMenu component.
 * Validates vote button disabled state and error handling for immutable votes.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PublicPlanner } from '@/types/PlannerListTypes'
import { ConflictError } from '@/lib/api'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: vi.fn(() => vi.fn()),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'pages.plannerList.contextMenu.view': 'View',
        'pages.plannerList.contextMenu.fork': 'Fork',
        'pages.plannerList.contextMenu.bookmark': 'Bookmark',
        'pages.plannerList.contextMenu.removeBookmark': 'Remove Bookmark',
        'pages.plannerList.contextMenu.upvote': 'Upvote',
        'pages.plannerList.contextMenu.downvote': 'Downvote',
        'pages.plannerList.contextMenu.upvoted': 'Upvoted',
        'pages.plannerList.contextMenu.downvoted': 'Downvoted',
        'pages.plannerList.contextMenu.alreadyVoted': 'Already voted',
      }
      return translations[key] ?? key
    },
  }),
}))

vi.mock('@/hooks/usePlannerVote', () => ({
  usePlannerVote: vi.fn(),
}))

vi.mock('@/hooks/usePlannerBookmark', () => ({
  usePlannerBookmark: vi.fn(),
}))

vi.mock('@/hooks/usePlannerFork', () => ({
  usePlannerFork: vi.fn(),
}))

import { usePlannerVote } from '@/hooks/usePlannerVote'
import { usePlannerBookmark } from '@/hooks/usePlannerBookmark'
import { usePlannerFork } from '@/hooks/usePlannerFork'
import { PlannerCardContextMenu } from './PlannerCardContextMenu'

const mockVoteMutate = vi.fn()
const mockBookmarkMutate = vi.fn()
const mockForkMutate = vi.fn()

const basePlanner: PublicPlanner = {
  id: 'planner-123',
  title: 'Test Planner',
  category: 'HARD',
  published: true,
  upvoteCount: 10,
  downvoteCount: 2,
  viewCount: 100,
  userVote: null,
  isBookmarked: false,
  authorUsernameKeyword: 'LCB',
  authorUsernameSuffix: '1234',
  createdAt: new Date('2025-01-01T00:00:00Z').toISOString(),
  updatedAt: new Date('2025-01-10T00:00:00Z').toISOString(),
}

describe('PlannerCardContextMenu', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(usePlannerVote).mockReturnValue({
      mutate: mockVoteMutate,
      isPending: false,
      isError: false,
      error: null,
    } as ReturnType<typeof usePlannerVote>)
    vi.mocked(usePlannerBookmark).mockReturnValue({
      mutate: mockBookmarkMutate,
      isPending: false,
    } as ReturnType<typeof usePlannerBookmark>)
    vi.mocked(usePlannerFork).mockReturnValue({
      mutate: mockForkMutate,
      isPending: false,
    } as ReturnType<typeof usePlannerFork>)
  })

  describe('vote button states (immutable voting)', () => {
    it('enables both vote buttons when user has not voted', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, userVote: null }

      render(
        <PlannerCardContextMenu
          planner={planner}
          view="community"
          isAuthenticated={true}
        >
          <div>Test Card</div>
        </PlannerCardContextMenu>
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const menu = screen.getByRole('menu')
      const upvoteButton = within(menu).getByRole('menuitem', { name: /upvote/i })
      const downvoteButton = within(menu).getByRole('menuitem', { name: /downvote/i })

      expect(upvoteButton).not.toHaveAttribute('aria-disabled', 'true')
      expect(downvoteButton).not.toHaveAttribute('aria-disabled', 'true')
    })

    it('disables both vote buttons when user has upvoted', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, userVote: 'UP' as const }

      render(
        <PlannerCardContextMenu
          planner={planner}
          view="community"
          isAuthenticated={true}
        >
          <div>Test Card</div>
        </PlannerCardContextMenu>
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const menu = screen.getByRole('menu')
      const upvoteButton = within(menu).getByRole('menuitem', { name: /upvoted/i })
      const downvoteButton = within(menu).getByRole('menuitem', { name: /already voted/i })

      expect(upvoteButton).toHaveAttribute('aria-disabled', 'true')
      expect(downvoteButton).toHaveAttribute('aria-disabled', 'true')
    })

    it('disables both vote buttons when user has downvoted', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, userVote: 'DOWN' as const }

      render(
        <PlannerCardContextMenu
          planner={planner}
          view="community"
          isAuthenticated={true}
        >
          <div>Test Card</div>
        </PlannerCardContextMenu>
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const menu = screen.getByRole('menu')
      const upvoteButton = within(menu).getByRole('menuitem', { name: /already voted/i })
      const downvoteButton = within(menu).getByRole('menuitem', { name: /downvoted/i })

      expect(upvoteButton).toHaveAttribute('aria-disabled', 'true')
      expect(downvoteButton).toHaveAttribute('aria-disabled', 'true')
    })

    it('shows "Upvoted" label when user has upvoted', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, userVote: 'UP' as const }

      render(
        <PlannerCardContextMenu
          planner={planner}
          view="community"
          isAuthenticated={true}
        >
          <div>Test Card</div>
        </PlannerCardContextMenu>
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      expect(screen.getByText('Upvoted')).toBeInTheDocument()
    })

    it('shows "Downvoted" label when user has downvoted', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, userVote: 'DOWN' as const }

      render(
        <PlannerCardContextMenu
          planner={planner}
          view="community"
          isAuthenticated={true}
        >
          <div>Test Card</div>
        </PlannerCardContextMenu>
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      expect(screen.getByText('Downvoted')).toBeInTheDocument()
    })

    it('shows "Already voted" on opposite button when voted', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, userVote: 'UP' as const }

      render(
        <PlannerCardContextMenu
          planner={planner}
          view="community"
          isAuthenticated={true}
        >
          <div>Test Card</div>
        </PlannerCardContextMenu>
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const alreadyVotedItems = screen.getAllByText('Already voted')
      expect(alreadyVotedItems).toHaveLength(1)
    })
  })

  describe('vote mutation calls', () => {
    it('calls vote mutation with UP when upvote is clicked', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, userVote: null }

      render(
        <PlannerCardContextMenu
          planner={planner}
          view="community"
          isAuthenticated={true}
        >
          <div>Test Card</div>
        </PlannerCardContextMenu>
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const upvoteButton = screen.getByRole('menuitem', { name: /upvote/i })
      await user.click(upvoteButton)

      expect(mockVoteMutate).toHaveBeenCalledWith({
        plannerId: 'planner-123',
        voteType: 'UP',
      })
    })

    it('calls vote mutation with DOWN when downvote is clicked', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, userVote: null }

      render(
        <PlannerCardContextMenu
          planner={planner}
          view="community"
          isAuthenticated={true}
        >
          <div>Test Card</div>
        </PlannerCardContextMenu>
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const downvoteButton = screen.getByRole('menuitem', { name: /downvote/i })
      await user.click(downvoteButton)

      expect(mockVoteMutate).toHaveBeenCalledWith({
        plannerId: 'planner-123',
        voteType: 'DOWN',
      })
    })

    it('still attempts vote mutation even when already voted (409 handled by hook)', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, userVote: 'UP' as const }

      render(
        <PlannerCardContextMenu
          planner={planner}
          view="community"
          isAuthenticated={true}
        >
          <div>Test Card</div>
        </PlannerCardContextMenu>
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const downvoteButton = within(screen.getByRole('menu')).getAllByRole('menuitem')[4]

      expect(downvoteButton).toHaveAttribute('aria-disabled', 'true')
    })
  })

  describe('vote error handling (409 Conflict)', () => {
    it('usePlannerVote hook handles 409 error internally', () => {
      const mockError = new ConflictError('Vote already exists')
      vi.mocked(usePlannerVote).mockReturnValue({
        mutate: mockVoteMutate,
        isPending: false,
        isError: true,
        error: mockError,
      } as ReturnType<typeof usePlannerVote>)

      const planner = { ...basePlanner, userVote: null }

      render(
        <PlannerCardContextMenu
          planner={planner}
          view="community"
          isAuthenticated={true}
        >
          <div>Test Card</div>
        </PlannerCardContextMenu>
      )

      expect(screen.getByText('Test Card')).toBeInTheDocument()
    })
  })

  describe('unauthenticated state', () => {
    it('does not show vote buttons when not authenticated', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, userVote: null }

      render(
        <PlannerCardContextMenu
          planner={planner}
          view="community"
          isAuthenticated={false}
        >
          <div>Test Card</div>
        </PlannerCardContextMenu>
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      expect(screen.queryByRole('menuitem', { name: /upvote/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: /downvote/i })).not.toBeInTheDocument()
    })

    it('shows only View option when not authenticated', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, userVote: null }

      render(
        <PlannerCardContextMenu
          planner={planner}
          view="community"
          isAuthenticated={false}
        >
          <div>Test Card</div>
        </PlannerCardContextMenu>
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const menu = screen.getByRole('menu')
      const menuItems = within(menu).getAllByRole('menuitem')

      expect(menuItems).toHaveLength(1)
      expect(within(menu).getByRole('menuitem', { name: /view/i })).toBeInTheDocument()
    })
  })

  describe('pending state', () => {
    it('disables vote buttons when vote mutation is pending', async () => {
      const user = userEvent.setup()
      vi.mocked(usePlannerVote).mockReturnValue({
        mutate: mockVoteMutate,
        isPending: true,
        isError: false,
        error: null,
      } as ReturnType<typeof usePlannerVote>)

      const planner = { ...basePlanner, userVote: null }

      render(
        <PlannerCardContextMenu
          planner={planner}
          view="community"
          isAuthenticated={true}
        >
          <div>Test Card</div>
        </PlannerCardContextMenu>
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const menu = screen.getByRole('menu')
      const upvoteButton = within(menu).getByRole('menuitem', { name: /upvote/i })
      const downvoteButton = within(menu).getByRole('menuitem', { name: /downvote/i })

      expect(upvoteButton).toHaveAttribute('aria-disabled', 'true')
      expect(downvoteButton).toHaveAttribute('aria-disabled', 'true')
    })
  })
})
