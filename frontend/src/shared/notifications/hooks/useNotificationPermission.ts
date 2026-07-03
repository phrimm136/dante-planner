import { useSyncExternalStore } from 'react'

import {
  getNotificationPermissionState,
  requestNotificationPermission,
  type NotificationPermissionState,
} from '../lib/browserNotification'

/**
 * Shared reactive view of the browser's Notification permission.
 *
 * `Notification.permission` is a single external global that can change from any
 * of our surfaces (settings notice, notification-pane CTA) or from the browser's
 * own site-settings UI. Mirroring it into per-component `useState` lets the
 * copies drift — granting in one place leaves the other stale. This backs every
 * consumer off one `useSyncExternalStore` source so a single change fans out to
 * all of them, and (where the Permissions API is available) reflects external
 * browser-settings changes too.
 */

const listeners = new Set<() => void>()
let permissionStatus: PermissionStatus | null = null

function emitChange(): void {
  for (const listener of listeners) listener()
}

function subscribe(onStoreChange: () => void): () => void {
  listeners.add(onStoreChange)

  // Lazily wire the browser's own permission-change event so a change made in
  // the browser UI (grant, or reset site permissions) syncs every consumer.
  // request()-driven emitChange covers browsers without the Permissions API.
  if (permissionStatus === null && typeof navigator !== 'undefined' && 'permissions' in navigator) {
    navigator.permissions
      .query({ name: 'notifications' as PermissionName })
      .then((status) => {
        permissionStatus = status
        status.onchange = emitChange
      })
      .catch(() => {
        // Permissions API doesn't support 'notifications' in this browser.
      })
  }

  return () => {
    listeners.delete(onStoreChange)
  }
}

function getSnapshot(): NotificationPermissionState {
  return getNotificationPermissionState()
}

function getServerSnapshot(): NotificationPermissionState {
  return 'unsupported'
}

export function useNotificationPermission(): {
  state: NotificationPermissionState
  request: () => void
} {
  const state = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)

  const request = (): void => {
    void requestNotificationPermission().then(emitChange)
  }

  return { state, request }
}
