import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { DiscordIcon } from '@/components/icons/DiscordIcon'

export function Footer() {
  const { t } = useTranslation()

  return (
    <footer className="px-6 py-8 border-t border-border">
      <div className="max-w-4xl mx-auto space-y-6">
        {/* Disclaimer */}
        <p className="text-sm text-muted-foreground text-center">
          {t('footer.disclaimer')}
        </p>

        {/* CN Translation Credit */}
        <p className="text-sm text-muted-foreground text-center">
          {t('footer.cnCredits')}{' '}
          <a
            href="https://github.com/LocalizeLimbusCompany/LocalizeLimbusCompany"
            target="_blank"
            rel="noopener noreferrer"
            className="text-primary hover:underline"
          >
            {t('footer.cnCreditsLink')}
          </a>
        </p>

        {/* Links */}
        <div className="flex flex-wrap items-center justify-center gap-4 text-sm">
          <Link to="/privacy" className="text-muted-foreground hover:text-foreground transition-colors">
            {t('footer.links.privacy')}
          </Link>
          <span className="text-muted-foreground/50">·</span>
          <Link to="/terms" className="text-muted-foreground hover:text-foreground transition-colors">
            {t('footer.links.terms')}
          </Link>
          <span className="text-muted-foreground/50">·</span>
          <a
            href="mailto:contact@dante-planner.com"
            className="text-muted-foreground hover:text-foreground transition-colors"
          >
            {t('footer.links.contact')}
          </a>
          <span className="text-muted-foreground/50">·</span>
          <a
            href="https://discord.gg/W22jR6fD"
            target="_blank"
            rel="noopener noreferrer"
            className="text-muted-foreground hover:text-foreground transition-colors inline-flex items-center gap-1"
          >
            <DiscordIcon size={16} />
            Discord
          </a>
        </div>

        {/* Copyright */}
        <p className="text-xs text-muted-foreground/70 text-center">
          {t('footer.copyright')}
        </p>
      </div>
    </footer>
  )
}
