import { useTranslation } from 'react-i18next'

export function Footer() {
  const { t } = useTranslation()

  return (
    <footer className="px-6 py-4">
      <p className="text-sm text-muted-foreground text-center">
        {t('footer.copyright')}
      </p>
    </footer>
  )
}
