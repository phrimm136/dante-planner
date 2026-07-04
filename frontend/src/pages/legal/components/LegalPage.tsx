import { useTranslation } from 'react-i18next'

import { CONTACT_EMAIL } from '@/lib/constants'

interface LegalPageProps {
  namespace: string
  lastUpdated: string
  sections: readonly string[]
  bulletSections: readonly string[]
}

export function LegalPage({ namespace, lastUpdated, sections, bulletSections }: LegalPageProps) {
  const { t } = useTranslation()

  return (
    <div className="container mx-auto px-4 py-8 max-w-3xl">
      <h1 className="text-3xl font-bold mb-2">{t(`${namespace}.title`)}</h1>
      <p className="text-sm text-muted-foreground mb-8">
        {t(`${namespace}.lastUpdated`, { date: lastUpdated })}
      </p>

      <div className="space-y-8">
        {sections.map((section) => (
          <section key={section}>
            <h2 className="text-xl font-semibold mb-3">
              {t(`${namespace}.sections.${section}.title`)}
            </h2>
            <p className="text-muted-foreground mb-2">
              {t(`${namespace}.sections.${section}.content`)}
              {section === 'contact' && (
                <>
                  {' '}
                  <a href={`mailto:${CONTACT_EMAIL}`} className="text-primary hover:underline">
                    {CONTACT_EMAIL}
                  </a>
                </>
              )}
            </p>
            {bulletSections.includes(section) && (
              <ul className="list-disc list-inside text-muted-foreground space-y-1 ml-2">
                {(
                  t(`${namespace}.sections.${section}.items`, { returnObjects: true }) as string[]
                ).map((item, index) => (
                  <li key={index}>{item}</li>
                ))}
              </ul>
            )}
          </section>
        ))}
      </div>
    </div>
  )
}
