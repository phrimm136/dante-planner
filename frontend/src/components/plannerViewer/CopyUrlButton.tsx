import { useTranslation } from 'react-i18next'
import { Link } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'

const isClient = typeof window !== 'undefined'

interface CopyUrlButtonProps {
  /** URL to copy. Defaults to window.location.href */
  url?: string
}

/**
 * Button that copies a URL to the clipboard.
 * Shows URL text on desktop, icon-only on mobile.
 * Shows toast notification on success.
 *
 * @example
 * <CopyUrlButton />
 * <CopyUrlButton url="https://example.com/planner/123" />
 */
export function CopyUrlButton({ url }: CopyUrlButtonProps) {
  const { t } = useTranslation(['planner', 'common'])

  const handleCopy = async () => {
    const urlToCopy = url ?? window.location.href

    try {
      await navigator.clipboard.writeText(urlToCopy)
      toast.success(t('pages.detail.urlCopied'))
    } catch (error) {
      console.error('Failed to copy URL:', error)
      toast.error(t('common:error'))
    }
  }

  const displayUrl = url ?? (isClient ? window.location.href : '')

  return (
    <Button
      variant="ghost"
      size="sm"
      onClick={handleCopy}
      className="gap-1.5 text-muted-foreground"
      aria-label={t('pages.detail.copyUrl')}
    >
      <span className="hidden lg:inline text-xs">{displayUrl}</span>
      <Link className="size-4" />
    </Button>
  )
}
