/**
 * DOM snapshots of the comment action row, one row per combination that changes
 * what it renders, plus the handler wiring behind each action.
 *
 * The dropdown menu is stubbed so the inline surface and the menu surface both
 * land in a single `container.innerHTML`; the committed snapshots were produced
 * by an innerHTML comparison against the two hand-written surfaces, so they
 * record their markup exactly.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'

import { CommentActionButtons } from '../CommentActionButtons'
import { CommentCard } from '../CommentCard'
import {
  ACTION_CASES,
  DELETED_ACTION_CASES,
  NOOP_ACTIONS,
  makeComment,
} from './commentSurfaceMatrix'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'EN' } }),
  }
})

vi.mock('@/components/ui/dropdown-menu', async () => {
  const { createDropdownMenuStub } = await import('./commentSurfaceMatrix')
  return createDropdownMenuStub()
})

describe('CommentActionButtons DOM', () => {
  for (const testCase of ACTION_CASES) {
    it(`renders ${testCase.label}`, () => {
      expect(
        render(<CommentActionButtons {...testCase.props} />).container.innerHTML,
      ).toMatchSnapshot()
    })
  }
})

describe('CommentActionButtons wiring', () => {
  function renderWithSpies(overrides: Partial<Parameters<typeof CommentActionButtons>[0]>) {
    const spies = {
      onStartReply: vi.fn(),
      onStartEdit: vi.fn(),
      onDelete: vi.fn(),
      onModeratorDelete: vi.fn(),
      onUpvote: vi.fn(),
      onToggleNotifications: vi.fn(),
    }
    const { container } = render(
      <CommentActionButtons
        comment={makeComment()}
        isPublished
        viewer={{ kind: 'anon' }}
        actions={{ ...NOOP_ACTIONS, ...spies }}
        onStartReply={spies.onStartReply}
        onStartEdit={spies.onStartEdit}
        {...overrides}
      />,
    )
    return {
      spies,
      inline: [...container.querySelectorAll('.hidden.sm\\:flex button')],
      menu: [...container.querySelectorAll('[data-slot="dropdown-menu-item"]')],
    }
  }

  const AUTHOR_ACTIONS = [
    'onStartReply',
    'onStartEdit',
    'onDelete',
    'onToggleNotifications',
  ] as const

  it('fires the author actions in order on both surfaces', async () => {
    const user = userEvent.setup()
    const { spies, inline, menu } = renderWithSpies({
      viewer: { kind: 'user' },
      comment: makeComment({ isAuthor: true }),
    })

    expect(inline).toHaveLength(AUTHOR_ACTIONS.length)
    expect(menu).toHaveLength(AUTHOR_ACTIONS.length)

    for (const [index, name] of AUTHOR_ACTIONS.entries()) {
      await user.click(inline[index])
      await user.click(menu[index])
      expect(spies[name]).toHaveBeenCalledTimes(2)
    }
  })

  it('addresses the comment-scoped actions by id, and toggles notifications to their opposite', async () => {
    const user = userEvent.setup()
    const comment = makeComment({ isAuthor: true, authorNotificationsEnabled: true })
    const { spies, inline } = renderWithSpies({ viewer: { kind: 'user' }, comment })

    await user.click(inline[2])
    expect(spies.onDelete).toHaveBeenCalledWith(comment.id)

    await user.click(inline[3])
    expect(spies.onToggleNotifications).toHaveBeenCalledWith(comment.id, false)
  })

  it('fires the moderator delete on both surfaces', async () => {
    const user = userEvent.setup()
    const { spies, inline, menu } = renderWithSpies({ viewer: { kind: 'moderator' } })

    // Reply first, then the moderator delete: a moderator is a signed-in user.
    expect(inline).toHaveLength(2)
    expect(menu).toHaveLength(2)

    await user.click(inline[1])
    await user.click(menu[1])
    expect(spies.onModeratorDelete).toHaveBeenCalledTimes(2)
    expect(spies.onDelete).not.toHaveBeenCalled()
  })

  it('upvotes from the always-visible button and disables it once upvoted', async () => {
    const user = userEvent.setup()
    const live = renderWithSpies({})
    await user.click(screen.getAllByRole('button')[0])
    expect(live.spies.onUpvote).toHaveBeenCalledTimes(1)

    render(
      <CommentActionButtons
        comment={makeComment({ hasUpvoted: true })}
        isPublished
        viewer={{ kind: 'anon' }}
        actions={NOOP_ACTIONS}
        onStartReply={() => {}}
        onStartEdit={() => {}}
      />,
    )
    expect(screen.getAllByRole('button').at(-1)).toBeDisabled()
  })
})

describe('deleted comments never reach CommentActionButtons', () => {
  const cardProps = {
    isPublished: true,
    viewer: { kind: 'moderator' } as const,
    actions: NOOP_ACTIONS,
  }

  for (const testCase of DELETED_ACTION_CASES) {
    it(`renders the placeholder instead for ${testCase.label}`, () => {
      render(<CommentCard {...cardProps} comment={{ ...testCase.props.comment, content: '' }} />)
      expect(screen.queryByRole('button')).toBeNull()
      expect(screen.getByText('pages.plannerMD.comments.deletedComment')).toBeInTheDocument()
    })
  }

  it('renders the action row for a live comment', () => {
    render(<CommentCard {...cardProps} comment={makeComment({ content: '' })} />)
    expect(screen.getAllByRole('button').length).toBeGreaterThan(0)
  })
})
