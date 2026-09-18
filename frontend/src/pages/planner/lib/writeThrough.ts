export function createWriteThrough(
  subscribe: (listener: () => void) => () => void,
  onChange: () => void,
): () => void {
  let scheduled = false

  return subscribe(() => {
    if (scheduled) return
    scheduled = true
    queueMicrotask(() => {
      scheduled = false
      onChange()
    })
  })
}
