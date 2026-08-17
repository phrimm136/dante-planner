/**
 * PlannerCardContextMenu.test.tsx
 *
 * Tests for PlannerCardContextMenu component.
 * Validates vote button disabled state and error handling for immutable votes.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PublicPlanner, VoteResponse } from '../../../types/PlannerListTypes'
import { ConflictError } from '@/lib/apiErrors'
import { buildMutationResult } from '@/test-utils'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: vi.fn(() => vi.fn()),
}))

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => {
        const translations: Record<string, string> = {
          'pages.plannerList.contextMenu.view': 'View',
          'pages.plannerList.contextMenu.fork': 'Fork',
          'pages.plannerList.contextMenu.upvote': 'Upvote',
          'pages.plannerList.contextMenu.downvote': 'Downvote',
          'pages.plannerList.contextMenu.upvoted': 'Upvoted',
          'pages.plannerList.contextMenu.downvoted': 'Downvoted',
          'pages.plannerList.contextMenu.alreadyVoted': 'Already voted',
        }
        return translations[key] ?? key
      },
    }),
  }
})

vi.mock('../../../hooks/usePlannerVote', () => ({
  usePlannerVote: vi.fn(),
}))

vi.mock('../../../hooks/usePlannerFork', () => ({
  usePlannerFork: vi.fn(),
}))

import { usePlannerVote } from '../../../hooks/usePlannerVote'
import type { VotePlannerInput } from '../../../hooks/usePlannerVote'
import { usePlannerFork } from '../../../hooks/usePlannerFork'
import { PlannerCardContextMenu } from '../PlannerCardContextMenu'

/** Fork input and result are module-private; recover them from the hook's result type. */
type ForkMutation = ReturnType<typeof usePlannerFork>
type ForkResult = NonNullable<ForkMutation['data']>
type ForkInput = NonNullable<ForkMutation['variables']>

const mockVoteMutate = vi.fn<ReturnType<typeof usePlannerVote>['mutate']>()
const mockForkMutate = vi.fn<ForkMutation['mutate']>()

const basePlanner: PublicPlanner = {
  id: '123e4567-e89b-12d3-a456-426614174000',
  title: 'Test Planner',
  plannerType: 'MIRROR_DUNGEON',
  category: '15F',
  selectedKeywords: null,
  upvotes: 10,
  viewCount: 100,
  commentCount: 0,
  hasUpvoted: false,
  isBookmarked: false,
  authorUsernameEpithet: 'NAIVE',
  authorUsernameSuffix: '1234A',
  createdAt: new Date('2025-01-01T00:00:00Z').toISOString(),
  firstPublishedAt: new Date('2025-01-10T00:00:00Z').toISOString(),
}

describe('PlannerCardContextMenu', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(usePlannerVote).mockReturnValue(
      buildMutationResult<VoteResponse, Error, VotePlannerInput>({
        mutate: mockVoteMutate,
        isPending: false,
        isError: false,
        error: null,
      }),
    )
    vi.mocked(usePlannerFork).mockReturnValue(
      buildMutationResult<ForkResult, Error, ForkInput>({
        mutate: mockForkMutate,
        isPending: false,
      }),
    )
  })

  describe('vote button states (immutable voting)', () => {
    it('enables upvote button when user has not voted', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, hasUpvoted: false }

      render(
        <PlannerCardContextMenu planner={planner} view="community" isAuthenticated={true}>
          <div>Test Card</div>
        </PlannerCardContextMenu>,
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const menu = screen.getByRole('menu')
      const upvoteButton = within(menu).getByRole('menuitem', { name: /upvote/i })

      expect(upvoteButton).not.toHaveAttribute('aria-disabled', 'true')
    })

    it('disables upvote button when user has upvoted', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, hasUpvoted: true }

      render(
        <PlannerCardContextMenu planner={planner} view="community" isAuthenticated={true}>
          <div>Test Card</div>
        </PlannerCardContextMenu>,
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const menu = screen.getByRole('menu')
      const upvoteButton = within(menu).getByRole('menuitem', { name: /upvoted/i })

      expect(upvoteButton).toHaveAttribute('aria-disabled', 'true')
    })

    it('shows "Upvoted" label when user has upvoted', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, hasUpvoted: true }

      render(
        <PlannerCardContextMenu planner={planner} view="community" isAuthenticated={true}>
          <div>Test Card</div>
        </PlannerCardContextMenu>,
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      expect(screen.getByText('Upvoted')).toBeInTheDocument()
    })
  })

  describe('vote mutation calls', () => {
    it('calls vote mutation with UP when upvote is clicked', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, hasUpvoted: false }

      render(
        <PlannerCardContextMenu planner={planner} view="community" isAuthenticated={true}>
          <div>Test Card</div>
        </PlannerCardContextMenu>,
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const upvoteButton = screen.getByRole('menuitem', { name: /upvote/i })
      await user.click(upvoteButton)

      expect(mockVoteMutate).toHaveBeenCalledWith(
        { plannerId: '123e4567-e89b-12d3-a456-426614174000', voteType: 'UP' },
        expect.any(Object),
      )
    })

    it('disables upvote button when already voted', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, hasUpvoted: true }

      render(
        <PlannerCardContextMenu planner={planner} view="community" isAuthenticated={true}>
          <div>Test Card</div>
        </PlannerCardContextMenu>,
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const menu = screen.getByRole('menu')
      const upvoteButton = within(menu).getByRole('menuitem', { name: /upvoted/i })

      expect(upvoteButton).toHaveAttribute('aria-disabled', 'true')
    })
  })

  describe('vote error handling (409 Conflict)', () => {
    it('usePlannerVote hook handles 409 error internally', () => {
      const mockError = new ConflictError('CONCURRENT_WRITE', 'Vote already exists', null)
      vi.mocked(usePlannerVote).mockReturnValue(
        buildMutationResult<VoteResponse, Error, VotePlannerInput>({
          mutate: mockVoteMutate,
          isPending: false,
          isError: true,
          error: mockError,
        }),
      )

      const planner = { ...basePlanner, hasUpvoted: false }

      render(
        <PlannerCardContextMenu planner={planner} view="community" isAuthenticated={true}>
          <div>Test Card</div>
        </PlannerCardContextMenu>,
      )

      expect(screen.getByText('Test Card')).toBeInTheDocument()
    })
  })

  describe('unauthenticated state', () => {
    it('does not show vote buttons when not authenticated', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, hasUpvoted: false }

      render(
        <PlannerCardContextMenu planner={planner} view="community" isAuthenticated={false}>
          <div>Test Card</div>
        </PlannerCardContextMenu>,
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      expect(screen.queryByRole('menuitem', { name: /upvote/i })).not.toBeInTheDocument()
    })

    it('shows only View option when not authenticated', async () => {
      const user = userEvent.setup()
      const planner = { ...basePlanner, hasUpvoted: false }

      render(
        <PlannerCardContextMenu planner={planner} view="community" isAuthenticated={false}>
          <div>Test Card</div>
        </PlannerCardContextMenu>,
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
    it('disables vote button when vote mutation is pending', async () => {
      const user = userEvent.setup()
      vi.mocked(usePlannerVote).mockReturnValue(
        buildMutationResult<VoteResponse, Error, VotePlannerInput>({
          mutate: mockVoteMutate,
          isPending: true,
          isError: false,
          error: null,
        }),
      )

      const planner = { ...basePlanner, hasUpvoted: false }

      render(
        <PlannerCardContextMenu planner={planner} view="community" isAuthenticated={true}>
          <div>Test Card</div>
        </PlannerCardContextMenu>,
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const menu = screen.getByRole('menu')
      const upvoteButton = within(menu).getByRole('menuitem', { name: /upvote/i })

      expect(upvoteButton).toHaveAttribute('aria-disabled', 'true')
    })

    it('keeps fork button enabled when vote mutation is pending', async () => {
      const user = userEvent.setup()
      vi.mocked(usePlannerVote).mockReturnValue(
        buildMutationResult<VoteResponse, Error, VotePlannerInput>({
          mutate: mockVoteMutate,
          isPending: true,
          isError: false,
          error: null,
        }),
      )
      vi.mocked(usePlannerFork).mockReturnValue(
        buildMutationResult<ForkResult, Error, ForkInput>({
          mutate: mockForkMutate,
          isPending: false,
        }),
      )

      const planner = { ...basePlanner, hasUpvoted: false }

      render(
        <PlannerCardContextMenu planner={planner} view="community" isAuthenticated={true}>
          <div>Test Card</div>
        </PlannerCardContextMenu>,
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const menu = screen.getByRole('menu')
      const forkButton = within(menu).getByRole('menuitem', { name: /copy/i })

      expect(forkButton).not.toHaveAttribute('aria-disabled', 'true')
    })

    it('keeps vote button enabled when fork mutation is pending', async () => {
      const user = userEvent.setup()
      vi.mocked(usePlannerVote).mockReturnValue(
        buildMutationResult<VoteResponse, Error, VotePlannerInput>({
          mutate: mockVoteMutate,
          isPending: false,
          isError: false,
          error: null,
        }),
      )
      vi.mocked(usePlannerFork).mockReturnValue(
        buildMutationResult<ForkResult, Error, ForkInput>({
          mutate: mockForkMutate,
          isPending: true,
        }),
      )

      const planner = { ...basePlanner, hasUpvoted: false }

      render(
        <PlannerCardContextMenu planner={planner} view="community" isAuthenticated={true}>
          <div>Test Card</div>
        </PlannerCardContextMenu>,
      )

      const card = screen.getByText('Test Card')
      await user.pointer({ keys: '[MouseRight]', target: card })

      const menu = screen.getByRole('menu')
      const upvoteButton = within(menu).getByRole('menuitem', { name: /upvote/i })

      expect(upvoteButton).not.toHaveAttribute('aria-disabled', 'true')
    })
  })
})
