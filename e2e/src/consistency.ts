import {
  LIVENESS_DEADLINE_MS,
  LIVENESS_POLL_INTERVAL_MS,
  SAFETY_PROBE_INTERVAL_MS,
  SAFETY_WINDOW_MS,
} from './staging'

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export interface SafetyWindow {
  windowMs?: number
  intervalMs?: number
}

/**
 * Re-runs a safety probe for the whole window. Every invocation asserts on its own answer, so one
 * violated instant fails the test — the loop widens the set of instants observed, it never retries
 * a failed one.
 *
 * @returns how many times the probe ran
 */
export async function holdsThroughout(
  probe: (attempt: number) => Promise<void>,
  options: SafetyWindow = {},
): Promise<number> {
  const windowMs = options.windowMs ?? SAFETY_WINDOW_MS
  const intervalMs = options.intervalMs ?? SAFETY_PROBE_INTERVAL_MS
  const deadline = Date.now() + windowMs

  let attempts = 0
  do {
    attempts += 1
    await probe(attempts)
    await sleep(intervalMs)
  } while (Date.now() < deadline)

  return attempts
}

export interface LivenessDeadline {
  deadlineMs?: number
  intervalMs?: number
  what?: string
}

/**
 * Polls a liveness property until it holds.
 *
 * @returns how long it took to become true, in milliseconds
 * @throws if it has not become true by the deadline
 */
export async function becomesTrue(
  probe: () => Promise<boolean>,
  options: LivenessDeadline = {},
): Promise<number> {
  const deadlineMs = options.deadlineMs ?? LIVENESS_DEADLINE_MS
  const intervalMs = options.intervalMs ?? LIVENESS_POLL_INTERVAL_MS
  const started = Date.now()
  const deadline = started + deadlineMs

  for (;;) {
    if (await probe()) return Date.now() - started
    if (Date.now() >= deadline) {
      throw new Error(`${options.what ?? 'condition'} did not hold within ${deadlineMs}ms`)
    }
    await sleep(intervalMs)
  }
}
