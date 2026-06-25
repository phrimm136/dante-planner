import { LegalPage } from './components/LegalPage'

const LAST_UPDATED = '2026-01-19'

const SECTIONS = [
  'intro',
  'dataCollected',
  'dataUse',
  'dataSharing',
  'cookies',
  'rights',
  'contact',
] as const

const BULLET_SECTIONS = ['dataCollected', 'dataUse'] as const

export default function PrivacyPage() {
  return (
    <LegalPage
      namespace="pages.privacy"
      lastUpdated={LAST_UPDATED}
      sections={SECTIONS}
      bulletSections={BULLET_SECTIONS}
    />
  )
}
