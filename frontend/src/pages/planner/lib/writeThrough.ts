/** Coalesce store notifications into one `onChange` per task, run as a microtask. */
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
