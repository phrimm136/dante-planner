import { useTranslation } from 'react-i18next'
import { Link } from 'lucide-react'
import { showError, showSuccess } from '@/lib/errorPresentation'

import { Button } from '@/components/ui/button'

const isClient = typeof window !== 'undefined'

interface CopyUrlButtonProps {
  url?: string
}

export function CopyUrlButton({ url }: CopyUrlButtonProps) {
  const { t } = useTranslation(['planner', 'common'])

  const getBaseUrl = () => {
    if (!isClient) return ''
    const { origin, pathname } = window.location
    return origin + pathname
  }

  const handleCopy = async () => {
    const urlToCopy = url ?? getBaseUrl()

    try {
      await navigator.clipboard.writeText(urlToCopy)
      showSuccess('planner:pages.detail.urlCopied')
    } catch (error) {
      console.error('Failed to copy URL:', error)
      showError(error)
    }
  }

  const displayUrl = url ?? getBaseUrl()

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
