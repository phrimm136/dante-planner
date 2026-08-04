/**
 * Fixtures for the comment surface suites: a comment node factory, the
 * action-visibility matrix, and a structural stub of the dropdown menu so both
 * the inline row and the menu row land in one `container.innerHTML`.
 */

import type { ReactNode } from 'react'

import type { CommentNode } from '../../types/CommentTypes'

export function makeComment(overrides: Partial<CommentNode> = {}): CommentNode {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    content: '<p>Hello</p>',
    authorEpithet: 'W_CORP',
    authorSuffix: 'A1B2C',
    isAuthor: false,
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: null,
    isUpdated: false,
    isDeleted: false,
    upvoteCount: 3,
    hasUpvoted: false,
    authorNotificationsEnabled: false,
    replies: [],
    ...overrides,
  }
}

export interface ActionSurfaceProps {
  comment: CommentNode
  isPublished: boolean
  isAuthenticated: boolean
  isModerator: boolean
  onReply: () => void
  onEdit: () => void
  onDelete: () => void
  onModeratorDelete: () => void
  onUpvote: () => void
  onToggleNotifications: () => void
  onReport?: () => void
  isUpvoting?: boolean
}

export interface ActionCase {
  label: string
  props: ActionSurfaceProps
}

const noop = () => {}

const HANDLERS = {
  onReply: noop,
  onEdit: noop,
  onDelete: noop,
  onModeratorDelete: noop,
  onUpvote: noop,
  onToggleNotifications: noop,
}

interface ActionFlags {
  isPublished: boolean
  isAuthenticated: boolean
  isAuthor: boolean
  isModerator: boolean
  authorNotificationsEnabled: boolean
  hasUpvoted: boolean
  isDeleted: boolean
  isUpvoting: boolean
  isReply: boolean
}

const FLAG_DEFAULTS: ActionFlags = {
  isPublished: true,
  isAuthenticated: false,
  isAuthor: false,
  isModerator: false,
  authorNotificationsEnabled: false,
  hasUpvoted: false,
  isDeleted: false,
  isUpvoting: false,
  isReply: false,
}

const FLAG_ABBREVIATIONS: Record<keyof ActionFlags, string> = {
  isPublished: 'published',
  isAuthenticated: 'auth',
  isAuthor: 'author',
  isModerator: 'mod',
  authorNotificationsEnabled: 'notif',
  hasUpvoted: 'upvoted',
  isDeleted: 'deleted',
  isUpvoting: 'upvoting',
  isReply: 'reply',
}

function label(flags: ActionFlags): string {
  const keys = Object.keys(FLAG_ABBREVIATIONS) as (keyof ActionFlags)[]
  const on = keys.filter((key) => flags[key]).map((key) => FLAG_ABBREVIATIONS[key])
  return on.length > 0 ? on.join('+') : 'none'
}

export function actionCase(overrides: Partial<ActionFlags>): ActionCase {
  const flags = { ...FLAG_DEFAULTS, ...overrides }
  const reply = flags.isReply
    ? [makeComment({ id: '22222222-2222-4222-8222-222222222222' })]
    : undefined
  return {
    label: label(flags),
    props: {
      comment: makeComment({
        isAuthor: flags.isAuthor,
        isDeleted: flags.isDeleted,
        hasUpvoted: flags.hasUpvoted,
        authorNotificationsEnabled: flags.authorNotificationsEnabled,
        ...(reply ? { replies: reply } : {}),
      }),
      isPublished: flags.isPublished,
      isAuthenticated: flags.isAuthenticated,
      isModerator: flags.isModerator,
      isUpvoting: flags.isUpvoting,
      ...HANDLERS,
    },
  }
}

function cartesian(dimensions: (keyof ActionFlags)[]): Partial<ActionFlags>[] {
  return dimensions.reduce<Partial<ActionFlags>[]>(
    (rows, dimension) =>
      rows.flatMap((row) => [
        { ...row, [dimension]: false },
        { ...row, [dimension]: true },
      ]),
    [{}],
  )
}

const VISIBILITY_DIMENSIONS: (keyof ActionFlags)[] = [
  'isPublished',
  'isAuthenticated',
  'isAuthor',
  'isModerator',
  'authorNotificationsEnabled',
]

/** Every action-visibility combination reachable from CommentCard. */
export const ACTION_CASES: ActionCase[] = [
  ...cartesian(VISIBILITY_DIMENSIONS).map(actionCase),
  actionCase({ hasUpvoted: true }),
  actionCase({ isAuthenticated: true, isAuthor: true, isUpvoting: true }),
  actionCase({ isAuthenticated: true, hasUpvoted: true, isUpvoting: true }),
  actionCase({ isAuthenticated: true, isAuthor: true, isReply: true }),
  actionCase({ isModerator: true, isReply: true }),
]

/** Deleted comments, which CommentCard answers with a placeholder instead. */
export const DELETED_ACTION_CASES: ActionCase[] = cartesian([
  'isAuthenticated',
  'isAuthor',
  'isModerator',
]).map((flags) => actionCase({ ...flags, isDeleted: true }))

interface StubItemProps {
  children?: ReactNode
  className?: string
  onClick?: () => void
}

/**
 * Replaces the Radix menu with inline elements: the real one portals its
 * content only while open, which would keep the menu surface out of the
 * rendered container.
 */
export function createDropdownMenuStub() {
  return {
    DropdownMenu: ({ children }: { children?: ReactNode }) => (
      <div data-slot="dropdown-menu">{children}</div>
    ),
    DropdownMenuTrigger: ({ children }: { children?: ReactNode }) => (
      <div data-slot="dropdown-menu-trigger">{children}</div>
    ),
    DropdownMenuContent: ({ children, align }: { children?: ReactNode; align?: string }) => (
      <div data-slot="dropdown-menu-content" data-align={align}>
        {children}
      </div>
    ),
    DropdownMenuItem: ({ children, className, onClick }: StubItemProps) => (
      <button type="button" data-slot="dropdown-menu-item" className={className} onClick={onClick}>
        {children}
      </button>
    ),
  }
}
