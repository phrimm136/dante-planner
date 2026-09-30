import { useTranslation } from 'react-i18next'

import { FONT_LICENSES, type FontLicense } from '@/lib/constants'

const NAMESPACE = 'pages.licenses'

const LINK_CLASS = 'text-primary hover:underline break-all'

function FontLicenseEntry({ font }: { font: FontLicense }) {
  const { t } = useTranslation()

  return (
    <article data-testid="font-license" className="border-b border-border pb-6">
      <h2 className="text-xl font-semibold mb-3">{font.family}</h2>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm text-muted-foreground">
        <dt>{t(`${NAMESPACE}.labels.author`)}</dt>
        <dd>{font.author}</dd>
        {font.sourceUrl && (
          <>
            <dt>{t(`${NAMESPACE}.labels.source`)}</dt>
            <dd>
              <a
                href={font.sourceUrl}
                target="_blank"
                rel="noopener noreferrer"
                className={LINK_CLASS}
              >
                {font.sourceUrl}
              </a>
            </dd>
          </>
        )}
        <dt>{t(`${NAMESPACE}.labels.licence`)}</dt>
        <dd>
          {font.licenceUrl ? (
            <a
              href={font.licenceUrl}
              target="_blank"
              rel="noopener noreferrer"
              className={LINK_CLASS}
            >
              {t(`${NAMESPACE}.licences.${font.licence}`)}
            </a>
          ) : (
            t(`${NAMESPACE}.licences.${font.licence}`)
          )}
          {font.licenceFileUrl && (
            <>
              {' · '}
              <a
                href={font.licenceFileUrl}
                target="_blank"
                rel="noopener noreferrer"
                className={LINK_CLASS}
              >
                {t(`${NAMESPACE}.labels.licenceText`)}
              </a>
            </>
          )}
        </dd>
      </dl>
      {font.copyright && (
        <p className="mt-3 text-xs text-muted-foreground whitespace-pre-line">
          {font.copyright.join('\n')}
        </p>
      )}
      {font.quote && (
        <blockquote className="mt-3 border-l-2 border-border pl-3 text-xs text-muted-foreground whitespace-pre-line">
          {font.quote}
        </blockquote>
      )}
      {font.hasNote && (
        <p className="mt-3 text-sm text-foreground">{t(`${NAMESPACE}.notes.${font.id}`)}</p>
      )}
    </article>
  )
}

export default function LicensesPage() {
  const { t } = useTranslation()

  return (
    <div className="container mx-auto px-4 py-8 max-w-3xl">
      <h1 className="text-3xl font-bold mb-2">{t(`${NAMESPACE}.title`)}</h1>
      <p className="text-muted-foreground mb-8">{t(`${NAMESPACE}.intro`)}</p>
      <div className="space-y-6">
        {FONT_LICENSES.map((font) => (
          <FontLicenseEntry key={font.id} font={font} />
        ))}
      </div>
    </div>
  )
}
