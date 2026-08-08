/**
 * The comment tree recursions, table-driven over the shapes the cache holds:
 * flat roots, nested replies, and a parent the loaded tree does not carry.
 */

import { describe, it, expect } from 'vitest'

import { containsComment, insertComment, updateCommentInTree, countComments } from '../commentTree'
import type { CommentNode } from '../../types/CommentTypes'

function node(id: string, replies: CommentNode[] = [], overrides: Partial<CommentNode> = {}) {
  return {
    id,
    content: `<p>${id}</p>`,
    authorEpithet: 'W_CORP',
    authorSuffix: 'A1B2C',
    isAuthor: false,
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: null,
    isDeleted: false,
    upvoteCount: 0,
    hasUpvoted: false,
    authorNotificationsEnabled: false,
    replies,
    ...overrides,
  } satisfies CommentNode
}

/** root → reply → nested reply, plus a childless sibling of root. */
const NESTED: CommentNode[] = [node('c1', [node('r1', [node('r1a')])]), node('c2')]

describe('containsComment', () => {
  it.each([
    ['a root', 'c1', true],
    ['a childless root', 'c2', true],
    ['a reply', 'r1', true],
    ['a reply of a reply', 'r1a', true],
    ['an id nowhere in the tree', 'nope', false],
  ] as const)('finds %s', (_label, id, expected) => {
    expect(containsComment(NESTED, id)).toBe(expected)
  })

  it('answers false for an empty tree', () => {
    expect(containsComment([], 'c1')).toBe(false)
  })

  it('treats a node without a replies array as a leaf', () => {
    const missingReplies = [{ id: 'c1' } as CommentNode]
    expect(containsComment(missingReplies, 'c1')).toBe(true)
    expect(containsComment(missingReplies, 'other')).toBe(false)
  })
})

describe('insertComment', () => {
  it('appends a parentless comment at the top level', () => {
    const next = insertComment(NESTED, node('c3'))
    expect(next?.map((n) => n.id)).toEqual(['c1', 'c2', 'c3'])
  })

  it.each([
    ['a root', 'c1', (tree: CommentNode[]) => tree[0].replies],
    ['a reply', 'r1', (tree: CommentNode[]) => tree[0].replies[0].replies],
    ['a reply of a reply', 'r1a', (tree: CommentNode[]) => tree[0].replies[0].replies[0].replies],
  ] as const)('grafts a reply under %s', (_label, parentCommentId, select) => {
    const next = insertComment(NESTED, node('new', [], { parentCommentId }))
    expect(next).not.toBeNull()
    expect(select(next as CommentNode[]).map((n) => n.id)).toContain('new')
  })

  it('returns null when the named parent is outside the loaded tree', () => {
    expect(insertComment(NESTED, node('new', [], { parentCommentId: 'unloaded' }))).toBeNull()
  })

  it('grafts a reply with an empty replies array of its own', () => {
    const next = insertComment(NESTED, node('new', [node('ignored')], { parentCommentId: 'c1' }))
    expect(next?.[0].replies.at(-1)).toEqual(expect.objectContaining({ id: 'new', replies: [] }))
  })

  it('leaves the input tree untouched', () => {
    const before = structuredClone(NESTED)
    insertComment(NESTED, node('new', [], { parentCommentId: 'r1' }))
    insertComment(NESTED, node('new2'))
    expect(NESTED).toEqual(before)
  })
})

describe('updateCommentInTree', () => {
  const upvote = (n: CommentNode) => ({ ...n, upvoteCount: n.upvoteCount + 1, hasUpvoted: true })

  it.each([
    ['a root', 'c1', (tree: CommentNode[]) => tree[0]],
    ['a reply', 'r1', (tree: CommentNode[]) => tree[0].replies[0]],
    ['a reply of a reply', 'r1a', (tree: CommentNode[]) => tree[0].replies[0].replies[0]],
  ] as const)('updates %s in place', (_label, targetId, select) => {
    const next = updateCommentInTree(NESTED, targetId, upvote)
    expect(select(next)).toEqual(expect.objectContaining({ upvoteCount: 1, hasUpvoted: true }))
  })

  it('preserves the shape of the tree around the updated node', () => {
    const next = updateCommentInTree(NESTED, 'r1', upvote)
    expect(next.map((n) => n.id)).toEqual(['c1', 'c2'])
    expect(next[0].replies.map((n) => n.id)).toEqual(['r1'])
    expect(next[0].replies[0].replies.map((n) => n.id)).toEqual(['r1a'])
    expect(countComments(next)).toBe(countComments(NESTED))
  })

  it('returns untouched branches by identity', () => {
    const next = updateCommentInTree(NESTED, 'r1a', upvote)
    expect(next[1]).toBe(NESTED[1])
  })

  it('leaves the tree unchanged when no node carries the id', () => {
    expect(updateCommentInTree(NESTED, 'nope', upvote)).toEqual(NESTED)
  })

  it('leaves the input tree untouched', () => {
    const before = structuredClone(NESTED)
    updateCommentInTree(NESTED, 'r1', upvote)
    expect(NESTED).toEqual(before)
  })
})

describe('countComments', () => {
  it.each([
    ['an empty tree', [], 0],
    ['flat roots', [node('c1'), node('c2')], 2],
    ['a root with replies', [node('c1', [node('r1'), node('r2')])], 3],
    ['a nested tree', NESTED, 4],
    ['two nested trees', [...NESTED, node('c3', [node('r3', [node('r3a')])])], 7],
  ] as const)('counts %s', (_label, tree, expected) => {
    expect(countComments(tree as CommentNode[])).toBe(expected)
  })
})
