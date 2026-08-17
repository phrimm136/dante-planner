/**
 * Permission handling reads one `getNotificationPermissionState()` and switches
 * on it, so unsupported browsers are a fourth state rather than a separate
 * `'Notification' in window` guard repeated at each call site. These pin the
 * full state × outcome table, including the unsupported column.
 */

import { describe, it, expect, vi, afterEach } from 'vitest'

import {
  getNotificationPermissionState,
  isNotificationSupported,
  isNotificationPermissionGranted,
  requestNotificationPermission,
} from '../browserNotification'

const ORIGINAL = Object.getOwnPropertyDescriptor(globalThis, 'Notification')

/** Install a Notification global with the given permission, or remove it. */
function setNotification(permission: NotificationPermission | null, request = vi.fn()) {
  if (permission === null) {
    Reflect.deleteProperty(globalThis, 'Notification')
    return request
  }
  Object.defineProperty(globalThis, 'Notification', {
    value: { permission, requestPermission: request },
    configurable: true,
    writable: true,
  })
  return request
}

afterEach(() => {
  if (ORIGINAL) {
    Object.defineProperty(globalThis, 'Notification', ORIGINAL)
  } else {
    Reflect.deleteProperty(globalThis, 'Notification')
  }
  vi.restoreAllMocks()
})

describe('getNotificationPermissionState', () => {
  it.each(['granted', 'denied', 'default'] as const)('reports %s verbatim', (permission) => {
    setNotification(permission)
    expect(getNotificationPermissionState()).toBe(permission)
  })

  it('folds an unsupported browser into its own state', () => {
    setNotification(null)
    expect(getNotificationPermissionState()).toBe('unsupported')
    expect(isNotificationSupported()).toBe(false)
  })
})

describe('isNotificationPermissionGranted', () => {
  it.each([
    ['granted', true],
    ['denied', false],
    ['default', false],
  ] as const)('is %s -> %s', (permission, expected) => {
    setNotification(permission)
    expect(isNotificationPermissionGranted()).toBe(expected)
  })

  it('is false when unsupported', () => {
    setNotification(null)
    expect(isNotificationPermissionGranted()).toBe(false)
  })
})

describe('requestNotificationPermission', () => {
  it('resolves true without prompting when already granted', async () => {
    const request = setNotification('granted')
    await expect(requestNotificationPermission()).resolves.toBe(true)
    expect(request).not.toHaveBeenCalled()
  })

  it('resolves false without prompting when denied — the browser refuses a second prompt', async () => {
    const request = setNotification('denied')
    await expect(requestNotificationPermission()).resolves.toBe(false)
    expect(request).not.toHaveBeenCalled()
  })

  it('resolves false without prompting when unsupported', async () => {
    setNotification(null)
    await expect(requestNotificationPermission()).resolves.toBe(false)
  })

  it('prompts from the default state and reports the grant', async () => {
    const request = setNotification('default', vi.fn().mockResolvedValue('granted'))
    await expect(requestNotificationPermission()).resolves.toBe(true)
    expect(request).toHaveBeenCalledTimes(1)
  })

  it('prompts from the default state and reports a refusal', async () => {
    setNotification('default', vi.fn().mockResolvedValue('denied'))
    await expect(requestNotificationPermission()).resolves.toBe(false)
  })

  it('reports false when the prompt rejects, as older Safari does', async () => {
    setNotification('default', vi.fn().mockRejectedValue(new Error('callback API')))
    await expect(requestNotificationPermission()).resolves.toBe(false)
  })
})
