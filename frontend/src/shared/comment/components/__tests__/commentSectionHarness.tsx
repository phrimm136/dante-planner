/**
 * Harness for the CommentSection suites: mutable hook state, module factories
 * the suites hand to `vi.mock`, prop-echoing child stubs, and the state matrix.
 */

import { vi } from 'vitest'

import { makeComment } from './commentSurfaceMatrix'
import type { CommentNode } from '../../types/CommentTypes'

export interface SectionState {
  tree: CommentNode[]
  isCreatePending: boolean
  isModeratorDeletePending: boolean
  newCommentsCount: number
  role: string
}

const DEFAULT_STATE: SectionState = {
  tree: [],
  isCreatePending: false,
  isModeratorDeletePending: false,
  newCommentsCount: 0,
  role: 'USER',
}

export const sectionState: SectionState = { ...DEFAULT_STATE }

export function setSectionState(overrides: Partial<SectionState>): void {
  Object.assign(sectionState, DEFAULT_STATE, overrides)
}

export const POPULATED_TREE: CommentNode[] = [
  makeComment({
    id: '33333333-3333-4333-8333-333333333333',
    replies: [makeComment({ id: '44444444-4444-4444-8444-444444444444' })],
  }),
]

// ---------------------------------------------------------------------------
// Module factories for vi.mock
// ---------------------------------------------------------------------------

export async function authModule() {
  const actual = await vi.importActual<typeof import('@/shared/auth')>('@/shared/auth')
  return { ...actual, useAuthQuery: () => ({ data: { role: sectionState.role } }) }
}

export function commentsQueryModule() {
  return {
    useCommentsQuery: () => sectionState.tree,
    commentsQueryKeys: { list: (plannerId: string) => ['comments', plannerId] },
  }
}

export function sseModule() {
  return {
    usePlannerCommentsSse: () => ({
      newCommentsCount: sectionState.newCommentsCount,
      resetCount: () => {},
    }),
  }
}

export function mutationsModule() {
  const mutation = () => ({ mutate: vi.fn(), isPending: false })
  return {
    useCreateComment: () => ({ mutate: vi.fn(), isPending: sectionState.isCreatePending }),
    useEditComment: mutation,
    useDeleteComment: mutation,
    useUpvoteComment: mutation,
    useReportComment: mutation,
    useToggleCommentNotifications: mutation,
  }
}

export function moderatorDeleteModule() {
  return {
    useModeratorCommentDelete: () => ({
      mutate: vi.fn(),
      isPending: sectionState.isModeratorDeletePending,
    }),
  }
}

// ---------------------------------------------------------------------------
// Child stubs: these suites pin the section's own composition, not its children.
// Every stub echoes the props it receives, so a prop rewiring shows up as a diff
// rather than passing silently.
// ---------------------------------------------------------------------------

function describeValue(value: unknown): unknown {
  if (typeof value === 'function') return 'fn'
  if (Array.isArray(value)) return value.map(describeValue)
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .filter(([, v]) => v !== undefined)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([k, v]) => [k, describeValue(v)]),
    )
  }
  return value
}

function stub(testId: string) {
  return function Stub(props: Record<string, unknown>) {
    return <div data-testid={testId} data-props={JSON.stringify(describeValue(props))} />
  }
}

export function editorStub() {
  return { CommentEditor: stub('comment-editor') }
}

export function threadStub() {
  return { CommentThread: stub('comment-thread') }
}

export function newCommentsBarStub() {
  return { NewCommentsBar: stub('new-comments-bar') }
}

export function moderationStub() {
  return { CommentDeleteDialog: stub('comment-delete-dialog') }
}

// ---------------------------------------------------------------------------
// Matrix
// ---------------------------------------------------------------------------

export interface SectionCase {
  label: string
  props: { plannerId: string; isPublished: boolean; isAuthenticated: boolean }
  arrange: () => void
}

const PLANNER_ID = 'planner-1'

function sectionCase(
  isPublished: boolean,
  isAuthenticated: boolean,
  populated: boolean,
  state: Partial<SectionState> = {},
  suffix = '',
): SectionCase {
  const flags = [
    isPublished ? 'published' : 'unpublished',
    isAuthenticated ? 'auth' : 'guest',
    populated ? 'populated' : 'empty',
  ]
  return {
    label: [...flags, suffix].filter(Boolean).join('+'),
    props: { plannerId: PLANNER_ID, isPublished, isAuthenticated },
    arrange: () => setSectionState({ tree: populated ? POPULATED_TREE : [], ...state }),
  }
}

const BOOLS = [false, true]

/** Every composer state, against both an empty and a populated tree. */
export const SECTION_CASES: SectionCase[] = [
  ...BOOLS.flatMap((isPublished) =>
    BOOLS.flatMap((isAuthenticated) =>
      BOOLS.map((populated) => sectionCase(isPublished, isAuthenticated, populated)),
    ),
  ),
  sectionCase(true, true, false, { isCreatePending: true }, 'submitting'),
  sectionCase(true, true, true, { newCommentsCount: 4 }, 'new-comments'),
  sectionCase(true, true, true, { role: 'MODERATOR' }, 'moderator'),
  sectionCase(
    true,
    true,
    true,
    { role: 'ADMIN', isModeratorDeletePending: true },
    'admin-deleting',
  ),
]
