import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { useAuthQuery } from '@/hooks/useAuthQuery'
import { UsernameSection } from '@/components/settings/UsernameSection'
import { SyncSection } from '@/components/settings/SyncSection'
import { PlannerExportImportSection } from '@/components/settings/PlannerExportImportSection'
import { NotificationSection } from '@/components/settings/NotificationSection'
import { AccountDeleteSection } from '@/components/settings/AccountDeleteSection'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Settings page for user preferences.
 *
 * Public access: Page loads for all users.
 * Content visibility: Auth-only sections hidden from guests.
 */
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
    <div className="container mx-auto p-8">
      {/* Username customization section - auth only */}
      {isAuthenticated && (
        <section className="rounded-lg border bg-card p-6">
          <UsernameSection />
        </section>
      )}

      {/* Sync settings section - auth only */}
      {isAuthenticated && (
        <section className="mt-8 rounded-lg border bg-card p-6">
          <SyncSection />
        </section>
      )}

      {/* Export/Import section - available to all */}
      <section className={isAuthenticated ? 'mt-8 rounded-lg border bg-card p-6' : 'rounded-lg border bg-card p-6'}>
        <PlannerExportImportSection />
      </section>

      {/* Notification settings section - auth only */}
      {isAuthenticated && (
        <section className="mt-8 rounded-lg border bg-card p-6">
          <NotificationSection />
        </section>
      )}

      {/* Danger Zone - Account Deletion - auth only */}
      {isAuthenticated && (
        <section className="mt-8">
          <h2 className="text-lg font-semibold text-destructive mb-4">{t('settings.dangerZone')}</h2>
          <div className="rounded-lg border border-destructive bg-destructive/10 p-6">
            <AccountDeleteSection />
          </div>
        </section>
      )}
    </div>
  )
}

function SettingsPageSkeleton() {
  return (
    <div className="container mx-auto p-8">
      <section className="rounded-lg border bg-card p-6">
        <Skeleton className="h-6 w-32 mb-4" />
        <Skeleton className="h-4 w-48" />
      </section>
    </div>
  )
}
