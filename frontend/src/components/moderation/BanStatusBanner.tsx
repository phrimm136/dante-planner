import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { AlertTriangle, X } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { useAuthQuery } from '@/hooks/useAuthQuery'

/**
 * Global banner displaying account suspension status (ban or timeout).
 * Shows reason and contact email. Dismissible but persists across page loads.
 */
export function BanStatusBanner() {
  const { t } = useTranslation(['common'])
  const { data: user } = useAuthQuery()
  const [isDismissed, setIsDismissed] = useState(false)

  // Only show if user is restricted and not dismissed
  const isRestricted = user?.isBanned === true || user?.isTimedOut === true
  if (!isRestricted || isDismissed) {
    return null
  }

  const isBanned = user.isBanned === true
  const reason = isBanned ? user.banReason : user.timeoutReason
  const message = reason
    ? t('moderation.accountSuspended', { reason })
    : t('moderation.accountSuspendedNoReason')

  return (
    <div className="bg-red-600 text-white px-4 py-3 flex items-center justify-between gap-4">
      <div className="flex items-center gap-3">
        <AlertTriangle className="h-5 w-5 flex-shrink-0" />
        <div className="text-sm">
          <p className="font-medium">{message}</p>
          <p className="text-red-100 mt-1">{t('moderation.contactSupport')}</p>
        </div>
      </div>
      <Button
        variant="ghost"
        size="sm"
        onClick={() => setIsDismissed(true)}
        className="text-white hover:bg-red-700 flex-shrink-0"
      >
        <X className="h-4 w-4" />
      </Button>
    </div>
  )
}
