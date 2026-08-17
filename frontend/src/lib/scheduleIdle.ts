/** How long the browser may defer an idle callback before running it anyway. */
const IDLE_TIMEOUT_MS = 500

/** Delay used where requestIdleCallback is unavailable (Safari). */
const IDLE_FALLBACK_DELAY_MS = 100

/**
 * The DOM lib declares both as always present; Safari disagrees, so this widens
 * them back to optional for an honest feature test.
 */
type IdleScheduler = Partial<Pick<Window, 'requestIdleCallback' | 'cancelIdleCallback'>>

/**
 * Run `callback` after the next frame has painted and the main thread is idle.
 *
 * Returns a canceller that unwinds whichever stage is still pending, so a
 * caller that unmounts mid-flight leaves nothing scheduled.
 */
export function scheduleIdle(callback: () => void): () => void {
  let cancelPending: () => void

  const frame = requestAnimationFrame(() => {
    const idleWindow: IdleScheduler = window
    const requestIdle = idleWindow.requestIdleCallback

    if (requestIdle) {
      const handle = requestIdle(callback, { timeout: IDLE_TIMEOUT_MS })
      cancelPending = () => idleWindow.cancelIdleCallback?.(handle)
      return
    }

    const timer = setTimeout(callback, IDLE_FALLBACK_DELAY_MS)
    cancelPending = () => clearTimeout(timer)
  })

  cancelPending = () => cancelAnimationFrame(frame)

  return () => cancelPending()
}
