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
import { useUpdateUserSettingsMutation } from '@/shared/userSettings'

export interface SyncChoiceDialogProps {
  open: boolean
  onChoice: (syncEnabled: boolean) => void
}

export function SyncChoiceDialog({ open, onChoice }: SyncChoiceDialogProps) {
  const { t } = useTranslation('common')
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
            {t('settings.sync.choiceDialog.title', 'Choose Your Sync Preference')}
          </DialogTitle>
          <DialogDescription className="space-y-2">
            <span className="block">
              {t(
                'settings.sync.choiceDialog.description',
                'Your planners are stored locally on this device by default. You can optionally enable cloud sync to access them across devices. Only manually saved plans will sync.',
              )}
            </span>
            <span className="block text-xs">
              {t(
                'settings.sync.choiceDialog.privacy',
                'Cloud sync stores your data on our servers. You can change this setting anytime.',
              )}
            </span>
            <span className="block text-xs">
              {t(
                'settings.sync.choiceDialog.exportHint',
                'You can also export your planners from the Settings page to backup or transfer them.',
              )}
            </span>
            <span className="block text-xs font-medium">
              {t(
                'settings.sync.choiceDialog.sharedDeviceWarning',
                'Locally stored plans remain on this device and are visible to anyone who uses this browser, even after you sign out.',
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
            {t('settings.sync.choiceDialog.keepLocal', 'Keep Local Only')}
          </Button>
          <Button onClick={() => void handleChoice(true)} disabled={updateSettings.isPending}>
            {t('settings.sync.choiceDialog.enableSync', 'Enable Cloud Sync')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
