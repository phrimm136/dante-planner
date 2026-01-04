import { useTranslation } from 'react-i18next'

import { UsernameSection } from '@/components/settings/UsernameSection'

/**
 * Settings page for user preferences.
 *
 * Public access: Page loads for all users.
 * Content gating: Individual sections show sign-in prompts for unauthenticated users.
 */
export default function SettingsPage() {
  const { t } = useTranslation()

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-2xl font-bold mb-8">
        {t('settings.title', 'Settings')}
      </h1>

      {/* Username customization section */}
      <section className="rounded-lg border bg-card p-6">
        <UsernameSection />
      </section>

      {/* Future sections can be added here */}
    </div>
  )
}
