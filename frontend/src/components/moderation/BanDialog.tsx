import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

interface BanDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  username: string
  onConfirm: (reason: string) => void
  isPending: boolean
}

export function BanDialog({ open, onOpenChange, username, onConfirm, isPending }: BanDialogProps) {
  const { t } = useTranslation(['moderation'])
  const [reason, setReason] = useState('')

  const handleConfirm = () => {
    if (!reason.trim()) return
    onConfirm(reason)
    setReason('')
  }

  const handleCancel = () => {
    onOpenChange(false)
    setReason('')
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('dialogs.ban.title')}</DialogTitle>
          <DialogDescription>
            {t('dialogs.ban.description', { username })}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <label htmlFor="ban-reason" className="text-sm font-medium">
            {t('dialogs.ban.reason')}
          </label>
          <textarea
            id="ban-reason"
            value={reason}
            onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setReason(e.target.value)}
            placeholder={t('dialogs.ban.reasonPlaceholder')}
            maxLength={500}
            rows={4}
            className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none overflow-y-auto"
          />
          <p className="text-xs text-muted-foreground text-right">
            {reason.length}/500
          </p>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleCancel} disabled={isPending}>
            {t('dialogs.ban.cancel')}
          </Button>
          <Button variant="destructive" onClick={handleConfirm} disabled={isPending || !reason.trim()}>
            {t('dialogs.ban.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

interface TimeoutDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  username: string
  onConfirm: (durationMinutes: number, reason: string) => void
  isPending: boolean
}

export function TimeoutDialog({ open, onOpenChange, username, onConfirm, isPending }: TimeoutDialogProps) {
  const { t } = useTranslation(['moderation', 'common'])
  const [duration, setDuration] = useState<number>(30)
  const [reason, setReason] = useState('')

  const presetDurations = [
    { value: 30, label: '30 minutes' },
    { value: 60, label: '1 hour' },
    { value: 180, label: '3 hours' },
    { value: 360, label: '6 hours' },
    { value: 1440, label: '1 day' },
    { value: 10080, label: '7 days' },
    { value: 43200, label: '30 days' },
  ]

  const handleConfirm = () => {
    if (!reason.trim()) return
    onConfirm(duration, reason)
    setReason('')
  }

  const handleCancel = () => {
    onOpenChange(false)
    setReason('')
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('dialogs.timeout.title')}</DialogTitle>
          <DialogDescription>
            {t('dialogs.timeout.description', { username })}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <label className="text-sm font-medium">{t('dialogs.timeout.duration')}</label>
            <div className="grid grid-cols-2 gap-2">
              {presetDurations.map(preset => (
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

          <div className="space-y-2">
            <label htmlFor="timeout-reason" className="text-sm font-medium">
              {t('dialogs.timeout.reason')}
            </label>
            <textarea
              id="timeout-reason"
              value={reason}
              onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setReason(e.target.value)}
              placeholder={t('dialogs.timeout.reasonPlaceholder')}
              maxLength={500}
              rows={4}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none overflow-y-auto"
            />
            <p className="text-xs text-muted-foreground text-right">
              {reason.length}/500
            </p>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleCancel} disabled={isPending}>
            {t('common:cancel')}
          </Button>
          <Button variant="destructive" onClick={handleConfirm} disabled={isPending || !reason.trim()}>
            {t('dialogs.timeout.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

interface ConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: string
  onConfirm: () => void
  isPending: boolean
  confirmText: string
}

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  onConfirm,
  isPending,
  confirmText,
}: ConfirmDialogProps) {
  const { t } = useTranslation(['moderation'])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            {t('dialogs.cancel')}
          </Button>
          <Button onClick={onConfirm} disabled={isPending}>
            {confirmText}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

interface UnbanDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  username: string
  onConfirm: (reason: string) => void
  isPending: boolean
}

export function UnbanDialog({ open, onOpenChange, username, onConfirm, isPending }: UnbanDialogProps) {
  const { t } = useTranslation(['moderation', 'common'])
  const [reason, setReason] = useState('')

  const handleConfirm = () => {
    if (!reason.trim()) return
    onConfirm(reason)
    setReason('')
  }

  const handleCancel = () => {
    onOpenChange(false)
    setReason('')
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('dialogs.unban.title')}</DialogTitle>
          <DialogDescription>
            {t('dialogs.unban.description', { username })}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <label htmlFor="unban-reason" className="text-sm font-medium">
            {t('dialogs.unban.reason')}
          </label>
          <textarea
            id="unban-reason"
            value={reason}
            onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setReason(e.target.value)}
            placeholder={t('dialogs.unban.reasonPlaceholder')}
            maxLength={500}
            rows={4}
            className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none overflow-y-auto"
          />
          <p className="text-xs text-muted-foreground text-right">
            {reason.length}/500
          </p>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleCancel} disabled={isPending}>
            {t('common:cancel')}
          </Button>
          <Button onClick={handleConfirm} disabled={isPending || !reason.trim()}>
            {t('dialogs.unban.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

interface ClearTimeoutDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  username: string
  onConfirm: (reason: string) => void
  isPending: boolean
}

export function ClearTimeoutDialog({ open, onOpenChange, username, onConfirm, isPending }: ClearTimeoutDialogProps) {
  const { t } = useTranslation(['moderation', 'common'])
  const [reason, setReason] = useState('')

  const handleConfirm = () => {
    if (!reason.trim()) return
    onConfirm(reason)
    setReason('')
  }

  const handleCancel = () => {
    onOpenChange(false)
    setReason('')
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('dialogs.clearTimeout.title')}</DialogTitle>
          <DialogDescription>
            {t('dialogs.clearTimeout.description', { username })}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <label htmlFor="clear-timeout-reason" className="text-sm font-medium">
            {t('dialogs.clearTimeout.reason')}
          </label>
          <textarea
            id="clear-timeout-reason"
            value={reason}
            onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setReason(e.target.value)}
            placeholder={t('dialogs.clearTimeout.reasonPlaceholder')}
            maxLength={500}
            rows={4}
            className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none overflow-y-auto"
          />
          <p className="text-xs text-muted-foreground text-right">
            {reason.length}/500
          </p>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleCancel} disabled={isPending}>
            {t('common:cancel')}
          </Button>
          <Button onClick={handleConfirm} disabled={isPending || !reason.trim()}>
            {t('dialogs.clearTimeout.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

interface CommentDeleteDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: (reason: string) => void
  isPending: boolean
}

export function CommentDeleteDialog({ open, onOpenChange, onConfirm, isPending }: CommentDeleteDialogProps) {
  const { t } = useTranslation(['moderation', 'common'])
  const [reason, setReason] = useState('')

  const handleConfirm = () => {
    if (!reason.trim()) return
    onConfirm(reason)
    setReason('')
  }

  const handleCancel = () => {
    onOpenChange(false)
    setReason('')
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('dialogs.deleteComment.title')}</DialogTitle>
          <DialogDescription>
            {t('dialogs.deleteComment.description')}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <label htmlFor="delete-reason" className="text-sm font-medium">
            {t('dialogs.deleteComment.reason')}
          </label>
          <textarea
            id="delete-reason"
            value={reason}
            onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setReason(e.target.value)}
            placeholder={t('dialogs.deleteComment.reasonPlaceholder')}
            maxLength={500}
            rows={4}
            className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none overflow-y-auto"
          />
          <p className="text-xs text-muted-foreground text-right">
            {reason.length}/500
          </p>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleCancel} disabled={isPending}>
            {t('common:cancel')}
          </Button>
          <Button variant="destructive" onClick={handleConfirm} disabled={isPending || !reason.trim()}>
            {t('common:delete')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
