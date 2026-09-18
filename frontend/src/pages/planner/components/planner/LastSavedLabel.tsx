import { useTranslation } from 'react-i18next'
import { useStore } from 'zustand'
import { SECTION_STYLES } from '@/lib/constants'
import { formatRelativeTime } from '@/lib/formatDate'
import type { SaveStatusStore } from '../../stores/saveStatus'

interface LastSavedLabelProps {
  status: SaveStatusStore
}

export function LastSavedLabel({ status }: LastSavedLabelProps) {
  const { t, i18n } = useTranslation('planner')
  const lastSavedAt = useStore(status, (s) => s.lastSavedAt)

  if (!lastSavedAt) return null

  const parsedDate = new Date(lastSavedAt)
  if (isNaN(parsedDate.getTime())) return null

  const text = t('sync.lastSaved', {
    time: formatRelativeTime(lastSavedAt, i18n.language),
  })

  return <span className={SECTION_STYLES.TEXT.caption}>{text}</span>
}
