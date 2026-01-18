/**
 * NewCommentsBar
 *
 * Banner shown when new comments are available via SSE.
 * Appears between comment list and comment writer.
 * Persists until user clicks to refresh.
 */

import { useTranslation } from 'react-i18next'
import { RefreshCw } from 'lucide-react'

import { Button } from '@/components/ui/button'

interface NewCommentsBarProps {
  /** Number of new comments available */
  count: number
  /** Callback when user clicks to refresh */
  onRefresh: () => void
}

export function NewCommentsBar({ count, onRefresh }: NewCommentsBarProps) {
  const { t } = useTranslation(['planner'])

  if (count === 0) return null

  return (
    <Button
      variant="outline"
      size="sm"
      className="w-full flex items-center justify-center gap-2 my-4"
      onClick={onRefresh}
    >
      <RefreshCw className="size-4" />
      {t('pages.plannerMD.comments.newComments', '{{count}} new comment(s)', { count })}
    </Button>
  )
}
