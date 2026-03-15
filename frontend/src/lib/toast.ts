import { toast as sonnerToast, type ExternalToast } from 'sonner'
import i18n from '@/lib/i18n'

function toastError(message: string | React.ReactNode, options?: ExternalToast) {
  return sonnerToast.error(message, {
    ...options,
    description: options?.description
      ? options.description
      : i18n.t('errors.contactOnRepeat'),
  })
}

/**
 * Wrapped toast that appends a contact-support description to every error toast.
 * All other methods (success, warning, dismiss, custom) pass through to sonner.
 */
export const toast = new Proxy(sonnerToast, {
  get(target, prop, receiver) {
    if (prop === 'error') return toastError
    return Reflect.get(target, prop, receiver)
  },
})
