import { Suspense, lazy, useEffect } from 'react'
import { Header } from './Header'
import { Footer } from '../Footer'
import { LanguageSync } from '../LanguageSync'
import { SyncChoiceDialog, useFirstLoginStore } from '@/pages/settings'
import { useDragToScroll } from '@/components/hooks/useDragToScroll'
import { useAuthQueryNonBlocking } from '@/shared/auth'
import { useUserSettingsQuery } from '@/pages/settings'

/**
 * `useAppSse` reaches the planner storage, validation and EGO Gift graphs.
 * Behind `lazy` those land in a chunk fetched after the first paint instead of
 * in the entry chunk of every route.
 */
const AppSse = lazy(async () => {
  const { useAppSse } = await import('@/pages/planner')
  return {
    default: function AppSse() {
      useAppSse()
      return null
    },
  }
})

interface GlobalLayoutProps {
  children: React.ReactNode
}

export function GlobalLayout({ children }: GlobalLayoutProps) {
  useDragToScroll()

  const { data: user } = useAuthQueryNonBlocking()
  const { data: settings } = useUserSettingsQuery()
  const showSyncChoiceDialog = useFirstLoginStore((s) => s.showSyncChoiceDialog)
  const openSyncChoiceDialog = useFirstLoginStore((s) => s.openSyncChoiceDialog)
  const closeSyncChoiceDialog = useFirstLoginStore((s) => s.closeSyncChoiceDialog)

  // Check on mount if user needs to configure sync preference
  useEffect(() => {
    if (user && settings && settings.syncEnabled == null) {
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
      <Suspense fallback={null}>
        <AppSse />
      </Suspense>
      <LanguageSync />
      <div className="bg-card border-b border-border">
        <Header />
      </div>
      <main className="flex-1 bg-background">{children}</main>
      <div className="bg-card border-t border-border">
        <Footer />
      </div>

      {/* First-login sync choice dialog (GDPR compliant) */}
      <SyncChoiceDialog open={showSyncChoiceDialog} onChoice={handleSyncChoice} />
    </div>
  )
}
