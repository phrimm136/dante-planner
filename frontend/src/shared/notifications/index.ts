export { NotificationDialog } from './components/NotificationDialog'
export { showNotificationToast } from './components/NotificationToast'

export { useUnreadCountQuery } from './hooks/useUnreadCountQuery'
export { notificationQueryKeys } from './hooks/useNotificationsQuery'

export {
  requestNotificationPermission,
  showBrowserNotification,
  isTabHidden,
} from './lib/browserNotification'

export { useNotificationPermission } from './hooks/useNotificationPermission'

export {
  SseNotificationEventSchema,
  SsePublishedEventSchema,
} from './schemas/NotificationSchemas'
export type { SseNotificationEvent } from './schemas/NotificationSchemas'
