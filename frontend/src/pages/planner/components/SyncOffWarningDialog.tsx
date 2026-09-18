import { useTranslation } from 'react-i18next'
import { AlertTriangle } from 'lucide-react'

import {
  ConfirmActionDialog,
  type ActionDialogControl,
} from '@/components/feedback/ConfirmActionDialog'

export type SyncOffAction = 'save' | 'publish'

const SYNC_OFF_KEYS: Record<
  SyncOffAction,
  { title: string; description: string; cancel: string; confirm: string }
> = {
  save: {
    title: 'pages.plannerMD.save.syncOffWarning.title',
    description: 'pages.plannerMD.save.syncOffWarning.description',
    cancel: 'pages.plannerMD.save.syncOffWarning.cancel',
    confirm: 'pages.plannerMD.save.syncOffWarning.confirm',
  },
  publish: {
    title: 'pages.plannerMD.publish.syncOffWarning.title',
    description: 'pages.plannerMD.publish.syncOffWarning.description',
    cancel: 'pages.plannerMD.publish.syncOffWarning.cancel',
    confirm: 'pages.plannerMD.publish.syncOffWarning.confirm',
  },
}

interface SyncOffWarningDialogProps extends ActionDialogControl {
  action: SyncOffAction
  onConfirm: () => void
}

export function SyncOffWarningDialog({
  action,
  open,
  onOpenChange,
  onConfirm,
  isPending = false,
}: SyncOffWarningDialogProps) {
  const { t } = useTranslation('planner')
  const keys = SYNC_OFF_KEYS[action]

  return (
    <ConfirmActionDialog
      open={open}
      onOpenChange={onOpenChange}
      className="sm:max-w-md"
      icon={<AlertTriangle className="size-5 text-yellow-500" />}
      title={t(keys.title)}
      description={t(keys.description)}
      descriptionClassName="pt-2"
      footerClassName="gap-2 sm:gap-0"
      cancelLabel={t(keys.cancel)}
      confirmLabel={t(keys.confirm)}
      onConfirm={onConfirm}
      isPending={isPending}
    />
  )
}
