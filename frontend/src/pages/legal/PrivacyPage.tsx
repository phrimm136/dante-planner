import { LegalPage } from './components/LegalPage'

const LAST_UPDATED = '2026-07-06'

const SECTIONS = [
  'intro',
  'dataCollected',
  'dataUse',
  'dataSharing',
  'dataTransfer',
  'cookies',
  'rights',
  'contact',
] as const

const BULLET_SECTIONS = ['dataCollected', 'dataUse', 'dataTransfer'] as const

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
