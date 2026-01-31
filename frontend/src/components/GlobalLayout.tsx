import { useEffect } from 'react'
import { Header } from './Header'
import { Footer } from './Footer'
import { LanguageSync } from './LanguageSync'
import { SyncChoiceDialog } from '@/components/dialogs/SyncChoiceDialog'
import { useFirstLoginStore } from '@/stores/useFirstLoginStore'
import { useSseConnection } from '@/hooks/useSseConnection'
import { useAuthQueryNonBlocking } from '@/hooks/useAuthQuery'
import { useUserSettingsQuery } from '@/hooks/useUserSettings'

interface GlobalLayoutProps {
  children: React.ReactNode
}

export function GlobalLayout({ children }: GlobalLayoutProps) {
  // Manage SSE connection based on auth + sync settings
  useSseConnection()

  const { data: user } = useAuthQueryNonBlocking()
  const { data: settings } = useUserSettingsQuery()
  const showSyncChoiceDialog = useFirstLoginStore((s) => s.showSyncChoiceDialog)
  const openSyncChoiceDialog = useFirstLoginStore((s) => s.openSyncChoiceDialog)
  const closeSyncChoiceDialog = useFirstLoginStore((s) => s.closeSyncChoiceDialog)

  // Check on mount if user needs to configure sync preference
  useEffect(() => {
    if (user && settings?.syncEnabled === null) {
      openSyncChoiceDialog()
    }
  }, [user, settings, openSyncChoiceDialog])

  const handleSyncChoice = (syncEnabled: boolean) => {
    closeSyncChoiceDialog()
    // If sync enabled, pending sync will be triggered by the component that needs it
    if (syncEnabled) {
      console.log('Cloud sync enabled by user')
    }
  }

  return (
    <div className="min-h-screen flex flex-col">
      <LanguageSync />
      <div className="bg-card border-b border-border">
        <Header />
      </div>
      <main className="flex-1 bg-background">{children}</main>
      <div className="bg-card border-t border-border">
        <Footer />
      </div>

      {/* First-login sync choice dialog (GDPR compliant) */}
      <SyncChoiceDialog
        open={showSyncChoiceDialog}
        onChoice={handleSyncChoice}
      />
    </div>
  )
}
