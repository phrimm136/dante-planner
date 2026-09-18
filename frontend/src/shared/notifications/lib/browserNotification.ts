export interface BrowserNotificationData {
  title: string
  body: string
  icon?: string
  url?: string
}

export function isNotificationSupported(): boolean {
  return 'Notification' in window
}

export type NotificationPermissionState = 'granted' | 'denied' | 'default' | 'unsupported'

export function getNotificationPermissionState(): NotificationPermissionState {
  if (!isNotificationSupported()) {
    return 'unsupported'
  }
  return Notification.permission as NotificationPermissionState
}

export function isNotificationPermissionGranted(): boolean {
  return getNotificationPermissionState() === 'granted'
}

export function isTabHidden(): boolean {
  return document.hidden
}

export async function requestNotificationPermission(): Promise<boolean> {
  switch (getNotificationPermissionState()) {
    case 'unsupported':
      return false
    case 'granted':
      return true
    // Denied is terminal: the browser refuses a second prompt.
    case 'denied':
      return false
    case 'default':
      try {
        return (await Notification.requestPermission()) === 'granted'
      } catch {
        // Older Safari resolves permission through a callback, not a promise.
        return false
      }
  }
}

export function showBrowserNotification(data: BrowserNotificationData): Notification | null {
  if (!isNotificationPermissionGranted()) {
    return null
  }

  if (!isTabHidden()) {
    return null
  }

  const notification = new Notification(data.title, {
    body: data.body,
    icon: data.icon ?? '/favicon.ico',
    tag: data.url ?? 'default', // Prevents duplicate notifications for same URL
  })

  if (data.url) {
    notification.onclick = () => {
      window.focus()

      window.location.href = data.url!

      notification.close()
    }
  }

  setTimeout(() => {
    notification.close()
  }, 10000)

  return notification
}
