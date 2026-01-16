import { Header } from './Header'
import { Footer } from './Footer'
import { LanguageSync } from './LanguageSync'
import { SyncChoiceDialog } from '@/components/dialogs/SyncChoiceDialog'
import { useFirstLoginStore } from '@/stores/useFirstLoginStore'
import { useSseConnection } from '@/hooks/useSseConnection'

interface GlobalLayoutProps {
  children: React.ReactNode
}

export function GlobalLayout({ children }: GlobalLayoutProps) {
  // Manage SSE connection based on auth + sync settings
  useSseConnection()

  const showSyncChoiceDialog = useFirstLoginStore((s) => s.showSyncChoiceDialog)
  const closeSyncChoiceDialog = useFirstLoginStore((s) => s.closeSyncChoiceDialog)

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
