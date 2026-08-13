import { SSE_TRANSPORT } from '@/lib/constants'
import { env } from '@/lib/env'

/**
 * SSE transport — one `fetch` per connection, decoded here rather than by the
 * browser, so the request carries `Last-Event-ID` and the response's status and
 * `Retry-After` reach the caller.
 */

const LINE_SEPARATOR = '\n'
const FRAME_SEPARATOR = '\n\n'
const FIELD_SEPARATOR = ':'
const DEFAULT_EVENT_TYPE = 'message'
const COMMENT_PREFIX = ':'
const VALUE_PADDING = ' '

/** One dispatched SSE frame. `data` is null when the frame carried no data field. */
export interface SseFrame {
  type: string
  data: string | null
  id: string | null
  retryMs: number | null
}

export interface SseStreamCallbacks {
  /** The response is a live event stream. */
  onOpen: () => void
  /** A complete frame was decoded. */
  onFrame: (frame: SseFrame) => void
  /** The server refused with 429; the argument is its cooldown, when parseable. */
  onRateLimited: (retryAfterMs: number | null) => void
  /** The stream ended or never started, and was not aborted by the caller. */
  onClosed: () => void
}

/** Parses one frame's field lines into the event it dispatches. */
export function parseSseFrame(raw: string): SseFrame {
  const dataLines: string[] = []
  let type = DEFAULT_EVENT_TYPE
  let id: string | null = null
  let retryMs: number | null = null

  for (const line of raw.split(LINE_SEPARATOR)) {
    if (line.length === 0 || line.startsWith(COMMENT_PREFIX)) continue

    const boundary = line.indexOf(FIELD_SEPARATOR)
    const field = boundary === -1 ? line : line.slice(0, boundary)
    const rawValue = boundary === -1 ? '' : line.slice(boundary + 1)
    const value = rawValue.startsWith(VALUE_PADDING) ? rawValue.slice(1) : rawValue

    if (field === 'event') {
      type = value
    } else if (field === 'data') {
      dataLines.push(value)
    } else if (field === 'id') {
      id = value
    } else if (field === 'retry') {
      const parsed = Number(value)
      if (Number.isFinite(parsed) && parsed >= 0) retryMs = parsed
    }
  }

  return {
    type,
    data: dataLines.length > 0 ? dataLines.join(LINE_SEPARATOR) : null,
    id,
    retryMs,
  }
}

/**
 * Rewrites CR and CRLF terminators to LF, holding back a trailing CR that may
 * still turn out to be the first half of a CRLF split across two chunks.
 */
function normalizeLineEndings(buffer: string): string {
  const heldCr = buffer.endsWith('\r')
  const body = heldCr ? buffer.slice(0, -1) : buffer
  return heldCr
    ? `${body.replace(/\r\n?/g, LINE_SEPARATOR)}\r`
    : body.replace(/\r\n?/g, LINE_SEPARATOR)
}

/** Seconds → milliseconds; null when the header is absent or not a number. */
function parseRetryAfterMs(header: string | null): number | null {
  if (header === null) return null
  const seconds = Number(header.trim())
  if (!Number.isFinite(seconds) || seconds < 0) return null
  return seconds * SSE_TRANSPORT.RETRY_AFTER_UNIT_MS
}

function buildHeaders(lastEventId: string | null): Record<string, string> {
  const headers: Record<string, string> = { Accept: SSE_TRANSPORT.ACCEPT }
  if (lastEventId !== null) {
    headers[SSE_TRANSPORT.LAST_EVENT_ID_HEADER] = lastEventId
  }
  return headers
}

async function pumpFrames(
  body: ReadableStream<Uint8Array>,
  signal: AbortSignal,
  onFrame: (frame: SseFrame) => void,
): Promise<void> {
  const decoder = new TextDecoderStream() as ReadableWritablePair<string, Uint8Array>
  const reader = body.pipeThrough(decoder).getReader()
  const cancel = () => {
    void reader.cancel().catch(() => undefined)
  }
  signal.addEventListener('abort', cancel, { once: true })

  let buffer = ''
  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read()
      if (done) break

      buffer = normalizeLineEndings(buffer + value)
      let boundary = buffer.indexOf(FRAME_SEPARATOR)
      while (boundary !== -1) {
        const frame = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + FRAME_SEPARATOR.length)
        onFrame(parseSseFrame(frame))
        boundary = buffer.indexOf(FRAME_SEPARATOR)
      }
    }
  } finally {
    signal.removeEventListener('abort', cancel)
  }
}

/**
 * Opens `path` as an event stream and drives `callbacks` until it ends.
 *
 * Resolves once the connection is over. An abort resolves silently: the caller
 * asked for the teardown, so neither `onClosed` nor `onRateLimited` fires.
 */
export async function runSseStream(
  path: string,
  lastEventId: string | null,
  signal: AbortSignal,
  callbacks: SseStreamCallbacks,
): Promise<void> {
  let response: Response
  try {
    response = await fetch(`${env.VITE_API_BASE_URL}${path}`, {
      credentials: 'include',
      signal,
      headers: buildHeaders(lastEventId),
    })
  } catch {
    if (!signal.aborted) callbacks.onClosed()
    return
  }

  if (signal.aborted) return

  if (response.status === SSE_TRANSPORT.RATE_LIMITED_STATUS) {
    callbacks.onRateLimited(
      parseRetryAfterMs(response.headers.get(SSE_TRANSPORT.RETRY_AFTER_HEADER)),
    )
    return
  }

  if (!response.ok || !response.body) {
    callbacks.onClosed()
    return
  }

  callbacks.onOpen()

  try {
    await pumpFrames(response.body, signal, callbacks.onFrame)
  } catch {
    if (!signal.aborted) callbacks.onClosed()
    return
  }

  if (!signal.aborted) callbacks.onClosed()
}
