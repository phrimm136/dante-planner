import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { useAuthQuery } from '@/shared/auth'
import { UsernameSection } from './components/UsernameSection'
import { SyncSection } from './components/SyncSection'
import { PlannerExportImportSection } from '@/pages/planner'
import { NotificationSection } from './components/NotificationSection'
import { AccountDeleteSection } from './components/AccountDeleteSection'
import { LogoutEverywhereSection } from './components/LogoutEverywhereSection'
import { SettingsPageSkeleton } from './components/SettingsPageSkeleton'
import { SECTION_STYLES } from '@/lib/constants'

export default function SettingsPage() {
  return (
    <Suspense fallback={<SettingsPageSkeleton />}>
      <SettingsPageContent />
    </Suspense>
  )
}

function SettingsPageContent() {
  const { t } = useTranslation()
  const { data: user } = useAuthQuery()
  const isAuthenticated = !!user

  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      {isAuthenticated && (
        <section className="rounded-lg border bg-card p-6">
          <UsernameSection />
        </section>
      )}

      {isAuthenticated && (
        <section className="mt-8 rounded-lg border bg-card p-6">
          <SyncSection />
        </section>
      )}

      <section
        className={
          isAuthenticated ? 'mt-8 rounded-lg border bg-card p-6' : 'rounded-lg border bg-card p-6'
        }
      >
        <PlannerExportImportSection />
      </section>

      {isAuthenticated && (
        <section className="mt-8 rounded-lg border bg-card p-6">
          <NotificationSection />
        </section>
      )}

      {isAuthenticated && (
        <section className="mt-8 rounded-lg border bg-card p-6">
          <LogoutEverywhereSection />
        </section>
      )}

      {isAuthenticated && (
        <section className="mt-8">
          <h2 className="text-lg font-semibold text-destructive mb-4">
            {t('settings.dangerZone')}
          </h2>
          <div className="rounded-lg border border-destructive bg-destructive/10 p-6">
            <AccountDeleteSection />
          </div>
        </section>
      )}
    </div>
  )
}
