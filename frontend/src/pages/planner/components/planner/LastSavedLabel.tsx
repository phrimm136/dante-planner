import { useTranslation } from 'react-i18next'
import { SECTION_STYLES } from '@/lib/constants'
import { formatRelativeTime } from '@/lib/formatDate'

interface LastSavedLabelProps {
  /** ISO 8601 timestamp of the last successful save, or null when there is none. */
  lastSavedAt: string | null
  /** Render as a suffix inside an existing line instead of its own muted span. */
  inline?: boolean
}

/**
 * "Saved 2 minutes ago", or nothing when there is no usable timestamp.
 */
export function LastSavedLabel({ lastSavedAt, inline = false }: LastSavedLabelProps) {
  const { t, i18n } = useTranslation('planner')

  if (!lastSavedAt) return null

  const parsedDate = new Date(lastSavedAt)
  if (isNaN(parsedDate.getTime())) return null

  const text = t('sync.lastSaved', {
    time: formatRelativeTime(lastSavedAt, i18n.language),
  })

  if (inline) return <>{` - ${text}`}</>

  return <span className={SECTION_STYLES.TEXT.caption}>{text}</span>
}
