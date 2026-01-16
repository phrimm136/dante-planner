import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useUpdateUserSettingsMutation } from '@/hooks/useUserSettings'

/**
 * Props for SyncChoiceDialog
 */
export interface SyncChoiceDialogProps {
  /** Whether the dialog is open */
  open: boolean
  /** Callback when user makes a choice */
  onChoice: (syncEnabled: boolean) => void
}

/**
 * First-login sync choice dialog (GDPR compliant)
 *
 * Non-dismissible dialog that forces users to explicitly choose their sync preference.
 * Shown when syncEnabled === null (first login).
 *
 * Features:
 * - No X button, no click-outside close, no Escape key dismissal
 * - Two clear options: Keep Local Only or Enable Cloud Sync
 * - Privacy-focused messaging explaining data handling
 *
 * @example
 * ```tsx
 * <SyncChoiceDialog
 *   open={settings?.syncEnabled === null}
 *   onChoice={(enabled) => {
 *     if (enabled) triggerPendingSync()
 *   }}
 * />
 * ```
 */
export function SyncChoiceDialog({ open, onChoice }: SyncChoiceDialogProps) {
  const { t } = useTranslation(['settings', 'common'])
  const updateSettings = useUpdateUserSettingsMutation()

  const handleChoice = async (syncEnabled: boolean) => {
    try {
      await updateSettings.mutateAsync({ syncEnabled })
      onChoice(syncEnabled)
    } catch (error) {
      console.error('Failed to update sync settings:', error)
    }
  }

  const preventDismissal = (e: Event) => {
    e.preventDefault()
  }

  return (
    <Dialog open={open}>
      <DialogContent
        showCloseButton={false}
        onEscapeKeyDown={preventDismissal}
        onInteractOutside={preventDismissal}
      >
        <DialogHeader>
          <DialogTitle>
            {t('sync.choiceDialog.title', 'Choose Your Sync Preference')}
          </DialogTitle>
          <DialogDescription className="space-y-2">
            <span className="block">
              {t(
                'sync.choiceDialog.description',
                'Your planners are stored locally on this device by default. You can optionally enable cloud sync to access them across devices.'
              )}
            </span>
            <span className="block text-xs">
              {t(
                'sync.choiceDialog.privacy',
                'Cloud sync stores your data on our servers. You can change this setting anytime.'
              )}
            </span>
            <span className="block text-xs">
              {t(
                'sync.choiceDialog.exportHint',
                'You can also export your planners from the Settings page to backup or transfer them.'
              )}
            </span>
          </DialogDescription>
        </DialogHeader>

        <DialogFooter className="flex-col gap-2 sm:flex-row">
          <Button
            variant="outline"
            onClick={() => void handleChoice(false)}
            disabled={updateSettings.isPending}
          >
            {t('sync.choiceDialog.keepLocal', 'Keep Local Only')}
          </Button>
          <Button
            onClick={() => void handleChoice(true)}
            disabled={updateSettings.isPending}
          >
            {t('sync.choiceDialog.enableSync', 'Enable Cloud Sync')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export default SyncChoiceDialog
