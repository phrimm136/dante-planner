import { useEffect, useRef, useState, startTransition } from 'react'

interface CappedSelectionOptions {
  /** Upper bound on the selection size. */
  cap: number
  selected: Set<string>
  onSelectedChange: (next: Set<string>) => void
  /** Superset the selection mirrors into: entries enter and leave alongside. */
  mirror: Set<string>
  onMirrorChange: (next: Set<string>) => void
}

interface CappedSelection {
  /** Adds the id while the cap allows it, or removes it if already selected. */
  toggle: (id: string) => void
  /** Empties the selection and withdraws its ids from the mirror. */
  clear: () => void
}

/**
 * Selection bounded by a cap, mirrored into a wider set.
 *
 * A cap that shrinks below the current size trims the excess, so a selection
 * made under a larger cap cannot survive as an over-count.
 *
 * Both callbacks keep one identity for the hook's lifetime: they read state
 * through a ref rather than closing over it, so a toggle does not hand every
 * card a new callback and re-render the whole grid to change one of them.
 */
export function useCappedSelection({
  cap,
  selected,
  onSelectedChange,
  mirror,
  onMirrorChange,
}: CappedSelectionOptions): CappedSelection {
  const latest = useRef({ cap, selected, onSelectedChange, mirror, onMirrorChange })
  useEffect(() => {
    latest.current = { cap, selected, onSelectedChange, mirror, onMirrorChange }
  })

  useEffect(() => {
    if (selected.size <= cap) return

    const kept = new Set<string>()
    const trimmed: string[] = []
    for (const id of selected) {
      if (kept.size < cap) {
        kept.add(id)
      } else {
        trimmed.push(id)
      }
    }

    if (trimmed.length > 0) {
      const nextMirror = new Set(mirror)
      for (const id of trimmed) {
        nextMirror.delete(id)
      }
      onMirrorChange(nextMirror)
    }
    onSelectedChange(kept)
  }, [cap, selected, onSelectedChange, mirror, onMirrorChange])

  const [toggle] = useState(() => (id: string) => {
    startTransition(() => {
      const current = latest.current
      const nextSelected = new Set(current.selected)
      const nextMirror = new Set(current.mirror)

      if (nextSelected.has(id)) {
        nextSelected.delete(id)
        nextMirror.delete(id)
      } else if (nextSelected.size < current.cap) {
        nextSelected.add(id)
        nextMirror.add(id)
      }

      current.onMirrorChange(nextMirror)
      current.onSelectedChange(nextSelected)
    })
  })

  const [clear] = useState(() => () => {
    const current = latest.current
    if (current.selected.size > 0) {
      const nextMirror = new Set(current.mirror)
      for (const id of current.selected) {
        nextMirror.delete(id)
      }
      current.onMirrorChange(nextMirror)
    }
    current.onSelectedChange(new Set())
  })

  return { toggle, clear }
}
