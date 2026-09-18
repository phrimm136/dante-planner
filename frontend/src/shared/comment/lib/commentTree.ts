import type { CommentNode } from '../types/CommentTypes'

export function containsComment(nodes: CommentNode[], id: string): boolean {
  return nodes.some((n) => n?.id === id || containsComment(n?.replies ?? [], id))
}

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

export function countComments(nodes: CommentNode[]): number {
  return nodes.reduce((acc, node) => acc + 1 + countComments(node.replies), 0)
}
