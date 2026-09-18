import { Suspense, lazy, useEffect } from 'react'
import { Header } from './Header'
import { Footer } from '../Footer'
import { LanguageSync } from '../LanguageSync'
import { SyncChoiceDialog } from '@/pages/settings'
import { useDragToScroll } from '@/components/hooks/useDragToScroll'
import { useAuthQueryNonBlocking } from '@/shared/auth'
import { useUserSettingsQuery, useFirstLoginStore } from '@/shared/userSettings'

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

  useEffect(() => {
    if (user && settings && !settings.syncChoiceMade) {
      openSyncChoiceDialog()
    }
  }, [user, settings, openSyncChoiceDialog])

  const handleSyncChoice = () => {
    closeSyncChoiceDialog()
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

      <SyncChoiceDialog open={showSyncChoiceDialog} onChoice={handleSyncChoice} />
    </div>
  )
}
