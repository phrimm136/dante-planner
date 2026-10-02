import { useTranslation } from 'react-i18next'
import { X } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { selectBanner, type BannerSignal } from '@/lib/banner/selectBanner'
import { useBannerSignals } from '@/lib/banner/useBannerSignals'
import { useDismissedBanners } from '@/lib/banner/useDismissedBanners'
import { cn } from '@/lib/utils'

function messageKey(signal: BannerSignal): string {
  switch (signal.kind) {
    case 'serverUnavailable':
      return 'errors.backendUnavailable'
    case 'storageUnavailable':
      return 'banner.storageUnavailable'
    case 'updateAvailable':
      return 'banner.updateAvailable'
  }
}

export function AppBanner() {
  const { t } = useTranslation(['common'])
  const signals = useBannerSignals()
  const [dismissed, dismiss] = useDismissedBanners()
  const signal = selectBanner(signals, dismissed)

  if (signal === null) return null

  const isServer = signal.kind === 'serverUnavailable'

  return (
    <div
      role="status"
      className={cn(
        'flex items-center justify-center gap-2 border-b px-4 py-1.5 text-sm',
        isServer
          ? 'border-destructive/40 bg-destructive/10 text-foreground'
          : 'border-border bg-card text-muted-foreground',
      )}
    >
      <p className="min-w-0 break-words">{t(messageKey(signal))}</p>
      {signal.kind === 'updateAvailable' && (
        <Button
          variant="outline"
          size="sm"
          className="shrink-0"
          onClick={() => window.location.reload()}
        >
          {t('banner.reload')}
        </Button>
      )}
      {!isServer && (
        <Button
          variant="ghost"
          size="icon-sm"
          className="shrink-0"
          aria-label={t('banner.dismiss')}
          title={t('banner.dismiss')}
          onClick={() => dismiss(signal)}
        >
          <X className="h-4 w-4" />
        </Button>
      )}
    </div>
  )
}
