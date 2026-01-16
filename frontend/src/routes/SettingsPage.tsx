import { UsernameSection } from '@/components/settings/UsernameSection'
import { SyncSection } from '@/components/settings/SyncSection'
import { PlannerExportImportSection } from '@/components/settings/PlannerExportImportSection'
import { NotificationSection } from '@/components/settings/NotificationSection'
import { AccountDeleteSection } from '@/components/settings/AccountDeleteSection'

/**
 * Settings page for user preferences.
 *
 * Public access: Page loads for all users.
 * Content gating: Individual sections show sign-in prompts for unauthenticated users.
 */
export default function SettingsPage() {
  return (
    <div className="container mx-auto p-8">
      {/* Username customization section */}
      <section className="rounded-lg border bg-card p-6">
        <UsernameSection />
      </section>

      {/* Sync settings section */}
      <section className="mt-8 rounded-lg border bg-card p-6">
        <SyncSection />
      </section>

      {/* Export/Import section */}
      <section className="mt-8 rounded-lg border bg-card p-6">
        <PlannerExportImportSection />
      </section>

      {/* Notification settings section */}
      <section className="mt-8 rounded-lg border bg-card p-6">
        <NotificationSection />
      </section>

      {/* Danger Zone - Account Deletion */}
      <section className="mt-8">
        <h2 className="text-lg font-semibold text-destructive mb-4">Danger Zone</h2>
        <div className="rounded-lg border border-destructive bg-destructive/10 p-6">
          <AccountDeleteSection />
        </div>
      </section>
    </div>
  )
}
