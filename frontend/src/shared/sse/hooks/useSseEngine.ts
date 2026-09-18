import { useEffect, useRef } from 'react'

import { SSE_CONNECTION, SSE_EVENTS, SSE_TRANSPORT, type SseEventType } from '@/lib/constants'
import { useSseStore } from '../stores/useSseStore'
import { runSseStream, type SseFrame } from '../lib/sseStream'

export interface SseReconnectPolicy {
  initialDelayMs: number
  baseDelayMs: number
  maxDelayMs: number
  maxJitterMs: number
  maxAttempts: number
  idleResetMs: number
  proactiveReconnectMs: number | null
  stableAfterMs: number
}

export const DEFAULT_SSE_POLICY: SseReconnectPolicy = {
  initialDelayMs: SSE_CONNECTION.INITIAL_DELAY,
  baseDelayMs: SSE_CONNECTION.BASE_DELAY,
  maxDelayMs: SSE_CONNECTION.MAX_DELAY,
  maxJitterMs: SSE_CONNECTION.MAX_JITTER,
  maxAttempts: SSE_CONNECTION.MAX_ATTEMPTS,
  idleResetMs: SSE_CONNECTION.IDLE_RESET_TIMEOUT,
  proactiveReconnectMs: SSE_CONNECTION.PROACTIVE_RECONNECT_INTERVAL,
  stableAfterMs: SSE_CONNECTION.STABLE_CONNECTION_THRESHOLD,
}

const SSE_EVENT_TYPES: readonly string[] = Object.values(SSE_EVENTS)

function isKnownEventType(type: string): type is SseEventType {
  return SSE_EVENT_TYPES.includes(type)
}

export interface SseConnectionState {
  getAttempts: () => number
  incrementAttempts: () => void
  resetAttempts: () => void
  setConnected: (connected: boolean) => void
}

export interface SseEngineConfig {
  shouldConnect: boolean
  streamKey?: string
  url: string
  handlers: Partial<Record<SseEventType, (event: MessageEvent) => void>>
  onConnected?: () => void
  stopOnNotFound?: boolean
  onStreamGone?: () => void
  policy?: SseReconnectPolicy
  state?: SseConnectionState
}

export function useSseEngine({
  shouldConnect,
  streamKey = '',
  url,
  handlers,
  onConnected,
  stopOnNotFound = false,
  onStreamGone,
  policy = DEFAULT_SSE_POLICY,
  state,
}: SseEngineConfig): void {
  const configRef = useRef({
    url,
    handlers,
    onConnected,
    stopOnNotFound,
    onStreamGone,
    policy,
    state,
  })
  useEffect(() => {
    configRef.current = {
      url,
      handlers,
      onConnected,
      stopOnNotFound,
      onStreamGone,
      policy,
      state,
    }
  })

  const setConnected = useSseStore((s) => s.setConnected)
  const incrementReconnectAttempts = useSseStore((s) => s.incrementReconnectAttempts)
  const resetReconnectAttempts = useSseStore((s) => s.resetReconnectAttempts)

  useEffect(() => {
    const { policy: timings } = configRef.current
    const connectionState: SseConnectionState = configRef.current.state ?? {
      getAttempts: () => useSseStore.getState().reconnectAttempts,
      incrementAttempts: incrementReconnectAttempts,
      resetAttempts: resetReconnectAttempts,
      setConnected,
    }

    let controller: AbortController | null = null
    let reconnectTimeout: ReturnType<typeof setTimeout> | null = null
    let proactiveReconnect: ReturnType<typeof setTimeout> | null = null
    let idleResetTimeout: ReturnType<typeof setTimeout> | null = null
    let connectionStartTime = 0
    let serverRetryMs: number | null = null
    let streamGone = false

    function clearAllTimers() {
      if (reconnectTimeout) {
        clearTimeout(reconnectTimeout)
        reconnectTimeout = null
      }
      if (proactiveReconnect) {
        clearTimeout(proactiveReconnect)
        proactiveReconnect = null
      }
      if (idleResetTimeout) {
        clearTimeout(idleResetTimeout)
        idleResetTimeout = null
      }
    }

    function disconnect() {
      if (controller) {
        controller.abort()
        controller = null
      }
      clearAllTimers()
      connectionState.setConnected(false)
    }

    function dispatchFrame(frame: SseFrame) {
      if (frame.retryMs !== null) serverRetryMs = frame.retryMs

      if (frame.type === SSE_EVENTS.CONNECTED) {
        connectionState.setConnected(true)
      }
      if (frame.data === null) return
      if (!isKnownEventType(frame.type)) return

      configRef.current.handlers[frame.type]?.(
        new MessageEvent(frame.type, { data: frame.data, lastEventId: frame.id ?? '' }),
      )
    }

    function handleOpen() {
      connectionStartTime = Date.now()
      connectionState.setConnected(true)

      if (idleResetTimeout) {
        clearTimeout(idleResetTimeout)
        idleResetTimeout = null
      }

      if (proactiveReconnect) {
        clearTimeout(proactiveReconnect)
        proactiveReconnect = null
      }
      if (timings.proactiveReconnectMs !== null) {
        proactiveReconnect = setTimeout(() => {
          if (controller) {
            controller.abort()
            controller = null
          }
          connectionState.setConnected(false)

          reconnectTimeout = setTimeout(() => {
            connectionState.resetAttempts()
            openStream()
          }, timings.initialDelayMs)
        }, timings.proactiveReconnectMs)
      }

      configRef.current.onConnected?.()
    }

    function handleClosed(status: number | null) {
      connectionState.setConnected(false)

      if (configRef.current.stopOnNotFound && status === SSE_TRANSPORT.STREAM_GONE_STATUS) {
        streamGone = true
        clearAllTimers()
        configRef.current.onStreamGone?.()
        return
      }

      if (connectionStartTime > 0 && Date.now() - connectionStartTime >= timings.stableAfterMs) {
        connectionState.resetAttempts()
      }

      if (proactiveReconnect) {
        clearTimeout(proactiveReconnect)
        proactiveReconnect = null
      }

      if (connectionState.getAttempts() >= timings.maxAttempts) {
        console.warn('SSE: Max reconnection attempts reached, waiting for idle reset')
        return
      }

      scheduleReconnect()
    }

    function handleRateLimited(retryAfterMs: number | null) {
      connectionState.setConnected(false)

      if (proactiveReconnect) {
        clearTimeout(proactiveReconnect)
        proactiveReconnect = null
      }
      if (idleResetTimeout) {
        clearTimeout(idleResetTimeout)
        idleResetTimeout = null
      }
      if (reconnectTimeout) {
        clearTimeout(reconnectTimeout)
      }

      const delay =
        Math.max(retryAfterMs ?? 0, timings.maxDelayMs) + Math.random() * timings.maxJitterMs
      reconnectTimeout = setTimeout(openStream, delay)
    }

    function openStream() {
      if (controller || streamGone) return
      const active = new AbortController()
      controller = active
      connectionStartTime = 0

      void runSseStream(configRef.current.url, active.signal, {
        onOpen: () => {
          if (controller === active) handleOpen()
        },
        onFrame: (frame) => {
          if (controller === active) dispatchFrame(frame)
        },
        onRateLimited: (retryAfterMs) => {
          if (controller !== active) return
          controller = null
          handleRateLimited(retryAfterMs)
        },
        onClosed: (status) => {
          if (controller !== active) return
          controller = null
          handleClosed(status)
        },
      })
    }

    function scheduleReconnect() {
      const attemptsBeforeIncrement = connectionState.getAttempts()
      connectionState.incrementAttempts()

      const backoff = Math.min(
        timings.baseDelayMs * Math.pow(2, attemptsBeforeIncrement),
        timings.maxDelayMs,
      )
      const delay = Math.max(backoff, serverRetryMs ?? 0) + Math.random() * timings.maxJitterMs

      if (idleResetTimeout) {
        clearTimeout(idleResetTimeout)
      }
      idleResetTimeout = setTimeout(() => {
        connectionState.resetAttempts()
        openStream()
      }, timings.idleResetMs)

      reconnectTimeout = setTimeout(openStream, delay)
    }

    function startInitialConnection() {
      if (controller) return
      if (connectionState.getAttempts() >= timings.maxAttempts) {
        console.warn('SSE: Max reconnection attempts reached, giving up')
        return
      }
      openStream()
    }

    if (!shouldConnect) {
      disconnect()
      connectionState.resetAttempts()
      return disconnect
    }

    let connectTimeout: ReturnType<typeof setTimeout> | null = null
    if (timings.initialDelayMs > 0) {
      connectTimeout = setTimeout(startInitialConnection, timings.initialDelayMs)
    } else {
      startInitialConnection()
    }

    return () => {
      if (connectTimeout) clearTimeout(connectTimeout)
      disconnect()
    }
  }, [shouldConnect, streamKey, setConnected, incrementReconnectAttempts, resetReconnectAttempts])
}
