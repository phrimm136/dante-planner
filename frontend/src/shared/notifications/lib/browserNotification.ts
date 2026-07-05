/**
 * Browser Notification Utility
 *
 * Handles Web Notifications API for real-time push notifications.
 * Notifications only show when tab is not visible.
 */

/**
 * Notification data structure for display
 */
export interface BrowserNotificationData {
  /** Notification title */
  title: string
  /** Notification body text */
  body: string
  /** Icon URL (optional) */
  icon?: string
  /** URL to navigate to on click */
  url?: string
}

/**
 * Check if browser supports notifications
 */
export function isNotificationSupported(): boolean {
  return 'Notification' in window
}

/**
 * Check if notification permission is granted
 */
export function isNotificationPermissionGranted(): boolean {
  return isNotificationSupported() && Notification.permission === 'granted'
}

/**
 * Check if notification permission is denied (user explicitly blocked)
 */
export function isNotificationPermissionDenied(): boolean {
  return isNotificationSupported() && Notification.permission === 'denied'
}

/**
 * Permission state type for UI display
 */
export type NotificationPermissionState = 'granted' | 'denied' | 'default' | 'unsupported'

/**
 * Get current notification permission state for UI display.
 * Useful for showing permission status in settings.
 *
 * @returns Permission state: 'granted' | 'denied' | 'default' | 'unsupported'
 */
export function getNotificationPermissionState(): NotificationPermissionState {
  if (!isNotificationSupported()) {
    return 'unsupported'
  }
  return Notification.permission as NotificationPermissionState
}

/**
 * Check if current tab is hidden (not visible to user)
 */
export function isTabHidden(): boolean {
  return document.hidden
}

/**
 * Request notification permission.
 * Should be called from user gesture (click handler).
 * Safe to call multiple times - no-op if already granted/denied.
 *
 * @returns Promise<boolean> true if permission granted
 */
export async function requestNotificationPermission(): Promise<boolean> {
  if (!isNotificationSupported()) {
    return false
  }

  // Already granted
  if (Notification.permission === 'granted') {
    return true
  }

  // Already denied - can't request again
  if (Notification.permission === 'denied') {
    return false
  }

  // Request permission (requires user gesture)
  try {
    const result = await Notification.requestPermission()
    return result === 'granted'
  } catch {
    // Safari older versions use callback instead of promise
    return false
  }
}

/**
 * Show a browser notification.
 * Only shows if:
 * - Notifications are supported
 * - Permission is granted
 * - Tab is hidden (not visible)
 *
 * @param data Notification content
 * @returns The Notification object if shown, null otherwise
 */
export function showBrowserNotification(data: BrowserNotificationData): Notification | null {
  // Guard: not supported or not permitted
  if (!isNotificationPermissionGranted()) {
    return null
  }

  // Guard: tab is visible - don't show browser notification
  if (!isTabHidden()) {
    return null
  }

  const notification = new Notification(data.title, {
    body: data.body,
    icon: data.icon ?? '/favicon.ico',
    tag: data.url ?? 'default', // Prevents duplicate notifications for same URL
  })

  // Handle click - focus window and navigate
  if (data.url) {
    notification.onclick = () => {
      // Focus the window/tab
      window.focus()

      // Navigate to the URL
      window.location.href = data.url!

      // Close the notification
      notification.close()
    }
  }

  // Auto-close after 10 seconds to avoid notification pile-up
  setTimeout(() => {
    notification.close()
  }, 10000)

  return notification
}
