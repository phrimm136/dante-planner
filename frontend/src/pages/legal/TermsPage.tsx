import { LegalPage } from './components/LegalPage'

const LAST_UPDATED = '2026-01-19'

const SECTIONS = [
  'acceptance',
  'description',
  'userConduct',
  'content',
  'disclaimer',
  'changes',
  'contact',
] as const

const BULLET_SECTIONS = ['userConduct'] as const

export default function TermsPage() {
  return (
    <LegalPage
      namespace="pages.terms"
      lastUpdated={LAST_UPDATED}
      sections={SECTIONS}
      bulletSections={BULLET_SECTIONS}
    />
  )
}
