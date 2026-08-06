import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { ModerationReasonDialog } from './ModerationReasonDialog'

/** The shape every per-user moderation dialog accepts, so callers can table them. */
export interface ModerationDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  username: string
  /** `durationMinutes` is supplied only by the dialogs that collect a duration. */
  onConfirm: (reason: string, durationMinutes?: number) => void
  isPending: boolean
}

export function BanDialog({
  open,
  onOpenChange,
  username,
  onConfirm,
  isPending,
}: ModerationDialogProps) {
  const { t } = useTranslation(['moderation'])

  return (
    <ModerationReasonDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('dialogs.ban.title')}
      description={t('dialogs.ban.description', { username })}
      reasonLabel={t('dialogs.ban.reason')}
      reasonPlaceholder={t('dialogs.ban.reasonPlaceholder')}
      reasonInputId="ban-reason"
      cancelLabel={t('dialogs.ban.cancel')}
      confirmLabel={t('dialogs.ban.confirm')}
      destructive
      onConfirm={onConfirm}
      isPending={isPending}
    />
  )
}

const TIMEOUT_PRESETS = [
  { value: 30, label: '30 minutes' },
  { value: 60, label: '1 hour' },
  { value: 180, label: '3 hours' },
  { value: 360, label: '6 hours' },
  { value: 1440, label: '1 day' },
  { value: 10080, label: '7 days' },
  { value: 43200, label: '30 days' },
]

const DEFAULT_TIMEOUT_MINUTES = 30

export function TimeoutDialog({
  open,
  onOpenChange,
  username,
  onConfirm,
  isPending,
}: ModerationDialogProps) {
  const { t } = useTranslation(['moderation', 'common'])
  const [duration, setDuration] = useState<number>(DEFAULT_TIMEOUT_MINUTES)

  return (
    <ModerationReasonDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('dialogs.timeout.title')}
      description={t('dialogs.timeout.description', { username })}
      reasonLabel={t('dialogs.timeout.reason')}
      reasonPlaceholder={t('dialogs.timeout.reasonPlaceholder')}
      reasonInputId="timeout-reason"
      cancelLabel={t('common:cancel')}
      confirmLabel={t('dialogs.timeout.confirm')}
      destructive
      onConfirm={(reason) => onConfirm(reason, duration)}
      isPending={isPending}
    >
      <div className="space-y-2">
        <label className="text-sm font-medium">{t('dialogs.timeout.duration')}</label>
        <div className="grid grid-cols-2 gap-2">
          {TIMEOUT_PRESETS.map((preset) => (
            <button
              key={preset.value}
              type="button"
              onClick={() => setDuration(preset.value)}
              className={`px-4 py-2 text-sm rounded-md border transition-colors ${
                duration === preset.value
                  ? 'bg-primary text-primary-foreground border-primary'
                  : 'bg-background hover:bg-accent'
              }`}
            >
              {preset.label}
            </button>
          ))}
        </div>
      </div>
    </ModerationReasonDialog>
  )
}

export function UnbanDialog({
  open,
  onOpenChange,
  username,
  onConfirm,
  isPending,
}: ModerationDialogProps) {
  const { t } = useTranslation(['moderation', 'common'])

  return (
    <ModerationReasonDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('dialogs.unban.title')}
      description={t('dialogs.unban.description', { username })}
      reasonLabel={t('dialogs.unban.reason')}
      reasonPlaceholder={t('dialogs.unban.reasonPlaceholder')}
      reasonInputId="unban-reason"
      cancelLabel={t('common:cancel')}
      confirmLabel={t('dialogs.unban.confirm')}
      onConfirm={onConfirm}
      isPending={isPending}
    />
  )
}

export function ClearTimeoutDialog({
  open,
  onOpenChange,
  username,
  onConfirm,
  isPending,
}: ModerationDialogProps) {
  const { t } = useTranslation(['moderation', 'common'])

  return (
    <ModerationReasonDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('dialogs.clearTimeout.title')}
      description={t('dialogs.clearTimeout.description', { username })}
      reasonLabel={t('dialogs.clearTimeout.reason')}
      reasonPlaceholder={t('dialogs.clearTimeout.reasonPlaceholder')}
      reasonInputId="clear-timeout-reason"
      cancelLabel={t('common:cancel')}
      confirmLabel={t('dialogs.clearTimeout.confirm')}
      onConfirm={onConfirm}
      isPending={isPending}
    />
  )
}

interface CommentDeleteDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: (reason: string) => void
  isPending: boolean
}

export function CommentDeleteDialog({
  open,
  onOpenChange,
  onConfirm,
  isPending,
}: CommentDeleteDialogProps) {
  const { t } = useTranslation(['moderation', 'common'])

  return (
    <ModerationReasonDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('dialogs.deleteComment.title')}
      description={t('dialogs.deleteComment.description')}
      reasonLabel={t('dialogs.deleteComment.reason')}
      reasonPlaceholder={t('dialogs.deleteComment.reasonPlaceholder')}
      reasonInputId="delete-reason"
      cancelLabel={t('common:cancel')}
      confirmLabel={t('common:delete')}
      destructive
      onConfirm={onConfirm}
      isPending={isPending}
    />
  )
}
