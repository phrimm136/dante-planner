import { createContext, useContext, useMemo, useRef, type ReactNode } from 'react'

/**
 * The mounted note editors an owner can collect pending text from.
 *
 * Editors hold typed text for one debounce interval before handing it to their
 * owner. When the page is going away the owner needs that text before it decides
 * anything, and it cannot get there by having each editor listen for the unload
 * itself: at the `window` target, listeners run in registration order regardless
 * of the capture flag, and an editor in a progressively revealed section
 * registers after its owner. So the owner pulls instead.
 */
export interface NoteDeliveryRegistry {
  /** Add a pending-delivery callback; the returned function removes it. */
  register: (deliver: () => void) => () => void
  /** Run every registered delivery, synchronously. */
  drain: () => void
}

const NoteDeliveryContext = createContext<NoteDeliveryRegistry | null>(null)

/** Build the registry an owner provides and drains. */
export function useNoteDeliveryRegistry(): NoteDeliveryRegistry {
  const deliveries = useRef<Set<() => void>>(new Set())

  return useMemo(
    () => ({
      register: (deliver: () => void) => {
        deliveries.current.add(deliver)
        return () => {
          deliveries.current.delete(deliver)
        }
      },
      drain: () => {
        // A delivery may unregister its own editor as it runs, so iterate a copy.
        // One that throws must not cost the editors after it their text, or the
        // caller the decision it drained for.
        const snapshot = Array.from(deliveries.current)
        for (const deliver of snapshot) {
          try {
            deliver()
          } catch (failure: unknown) {
            console.error('A note editor failed to hand over its pending text', failure)
          }
        }
      },
    }),
    [],
  )
}

export function NoteDeliveryProvider({
  registry,
  children,
}: {
  registry: NoteDeliveryRegistry
  children: ReactNode
}) {
  return <NoteDeliveryContext.Provider value={registry}>{children}</NoteDeliveryContext.Provider>
}

/** Null when an editor renders outside any owner, which is a valid standalone use. */
export function useNoteDelivery(): NoteDeliveryRegistry | null {
  return useContext(NoteDeliveryContext)
}
