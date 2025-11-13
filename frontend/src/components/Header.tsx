import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Languages, Sun, Settings, User } from 'lucide-react'

/**
 * Header component with three-section layout:
 * - Left: Clickable title linking to homepage
 * - Center: Navigation menu (In-Game Info, Planner, Community)
 * - Right: Settings buttons (Language, Theme, Settings, Sign In)
 *
 * Note: Background and border styling provided by GlobalLayout wrapper.
 */
export function Header() {
  const { t, i18n } = useTranslation()

  const changeLanguage = (lng: string) => {
    i18n.changeLanguage(lng)
  }

  return (
    <header className="px-6 py-4">
      <div className="flex items-center justify-between gap-4">
        {/* Left Section: Title/Logo */}
        <div className="shrink-0">
          <Link
            to="/"
            className="text-2xl font-bold text-foreground no-underline hover:text-primary transition-colors"
          >
            Dante's Planner
          </Link>
        </div>

        {/* Center Section: Navigation Links */}
        <nav className="flex items-center gap-6">
          <Button asChild variant="ghost">
            <Link to="/info">{t('header.nav.info')}</Link>
          </Button>
          <Button asChild variant="ghost">
            <Link to="/planner">{t('header.nav.planner')}</Link>
          </Button>
          <Button asChild variant="ghost">
            <Link to="/community">{t('header.nav.community')}</Link>
          </Button>
        </nav>

        {/* Right Section: Settings Buttons */}
        <div className="shrink-0 flex items-center gap-2">
          {/* Language Selector Dropdown */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                aria-label={t('header.settings.language')}
              >
                <Languages />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuRadioGroup
                value={i18n.language}
                onValueChange={changeLanguage}
              >
                <DropdownMenuRadioItem value="EN">
                  English
                </DropdownMenuRadioItem>
                <DropdownMenuRadioItem value="JP">
                  日本語
                </DropdownMenuRadioItem>
                <DropdownMenuRadioItem value="KR">
                  한국어
                </DropdownMenuRadioItem>
                <DropdownMenuRadioItem value="CN">
                  中文
                </DropdownMenuRadioItem>
              </DropdownMenuRadioGroup>
            </DropdownMenuContent>
          </DropdownMenu>

          {/* TODO: Implement theme toggle with theme context */}
          <Button
            variant="ghost"
            size="icon"
            aria-label={t('header.settings.theme')}
          >
            <Sun />
          </Button>

          {/* TODO: Link to settings page when created */}
          <Button
            variant="ghost"
            size="icon"
            aria-label={t('header.settings.settings')}
          >
            <Settings />
          </Button>

          {/* TODO: Implement authentication with OAuth 2.0 */}
          <Button
            variant="ghost"
            size="icon"
            aria-label={t('header.settings.signIn')}
          >
            <User />
          </Button>
        </div>
      </div>
    </header>
  )
}
