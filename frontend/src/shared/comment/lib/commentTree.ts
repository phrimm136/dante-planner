/**
 * Pure recursions over the server-built comment tree.
 *
 * Every operation is immutable: it returns a new tree (or null when the tree
 * cannot answer the request) and never mutates the cached nodes React Query
 * hands it. The SSE graft path, the mutation cache patches and the section
 * header all read the same tree, so the recursions live here rather than once
 * per consumer.
 */

import type { CommentNode } from '../types/CommentTypes'

/** Whether `id` names a comment anywhere in the loaded tree. */
export function containsComment(nodes: CommentNode[], id: string): boolean {
  return nodes.some((n) => n?.id === id || containsComment(n?.replies ?? [], id))
}

/**
 * Place `added` under the parent it names, or at the top level when it names none.
 *
 * Returns null when the named parent is not in the loaded tree — the caller's
 * counter still moves, but no comment is grafted where it does not belong.
 */
export function insertComment(nodes: CommentNode[], added: CommentNode): CommentNode[] | null {
  if (!added.parentCommentId) {
    return [...nodes, { ...added, replies: added.replies ?? [] }]
  }

  let grafted = false
  const graft = (list: CommentNode[]): CommentNode[] =>
    list.map((node) => {
      if (node?.id === added.parentCommentId) {
        grafted = true
        return { ...node, replies: [...(node.replies ?? []), { ...added, replies: [] }] }
      }
      return { ...node, replies: graft(node?.replies ?? []) }
    })

  const next = graft(nodes)
  return grafted ? next : null
}

/**
 * Replace the comment named by `targetId` with `updater`'s result.
 *
 * Nodes outside the path to the target are returned by identity.
 */
export function updateCommentInTree(
  nodes: CommentNode[],
  targetId: string,
  updater: (node: CommentNode) => CommentNode,
): CommentNode[] {
  return nodes.map((node) => {
    if (node.id === targetId) {
      return updater(node)
    }
    if (node.replies.length > 0) {
      return { ...node, replies: updateCommentInTree(node.replies, targetId, updater) }
    }
    return node
  })
}

/** Total comments in the tree, replies included. */
export function countComments(nodes: CommentNode[]): number {
  return nodes.reduce((acc, node) => acc + 1 + countComments(node.replies), 0)
}
