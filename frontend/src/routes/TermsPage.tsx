import { useTranslation } from 'react-i18next'

const LAST_UPDATED = '2026-01-19'
const CONTACT_EMAIL = 'contact@dante-planner.com'

export default function TermsPage() {
  const { t } = useTranslation()

  const sections = [
    'acceptance',
    'description',
    'userConduct',
    'content',
    'disclaimer',
    'changes',
    'contact',
  ] as const

  return (
    <div className="container mx-auto px-4 py-8 max-w-3xl">
      <h1 className="text-3xl font-bold mb-2">{t('pages.terms.title')}</h1>
      <p className="text-sm text-muted-foreground mb-8">
        {t('pages.terms.lastUpdated', { date: LAST_UPDATED })}
      </p>

      <div className="space-y-8">
        {sections.map((section) => (
          <section key={section}>
            <h2 className="text-xl font-semibold mb-3">
              {t(`pages.terms.sections.${section}.title`)}
            </h2>
            <p className="text-muted-foreground mb-2">
              {t(`pages.terms.sections.${section}.content`)}
              {section === 'contact' && (
                <>
                  {' '}
                  <a
                    href={`mailto:${CONTACT_EMAIL}`}
                    className="text-primary hover:underline"
                  >
                    {CONTACT_EMAIL}
                  </a>
                </>
              )}
            </p>
            {(section === 'userConduct') && (
              <ul className="list-disc list-inside text-muted-foreground space-y-1 ml-2">
                {(t(`pages.terms.sections.${section}.items`, { returnObjects: true }) as string[]).map(
                  (item, index) => (
                    <li key={index}>{item}</li>
                  )
                )}
              </ul>
            )}
          </section>
        ))}
      </div>
    </div>
  )
}
