import { Link } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import { Languages, Sun, Settings, User } from 'lucide-react'

/**
 * Header component with three-section layout:
 * - Left: Clickable title linking to homepage
 * - Center: Navigation menu (In-Game Info, Planner, Community)
 * - Right: Settings buttons (Language, Theme, Settings, Sign In)
 *
 * Note: Background and border styling provided by GlobalLayout wrapper.
 * Settings buttons are visual placeholders - functionality to be implemented.
 */
export function Header() {
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
            <Link to="/info">In-Game Info</Link>
          </Button>
          <Button asChild variant="ghost">
            <Link to="/planner">Planner</Link>
          </Button>
          <Button asChild variant="ghost">
            <Link to="/community">Community</Link>
          </Button>
        </nav>

        {/* Right Section: Settings Buttons */}
        <div className="shrink-0 flex items-center gap-2">
          {/* TODO: Implement language selection with i18n library */}
          <Button
            variant="ghost"
            size="icon"
            aria-label="Select language"
          >
            <Languages />
          </Button>

          {/* TODO: Implement theme toggle with theme context */}
          <Button
            variant="ghost"
            size="icon"
            aria-label="Toggle theme"
          >
            <Sun />
          </Button>

          {/* TODO: Link to settings page when created */}
          <Button
            variant="ghost"
            size="icon"
            aria-label="Settings"
          >
            <Settings />
          </Button>

          {/* TODO: Implement authentication with OAuth 2.0 */}
          <Button
            variant="ghost"
            size="icon"
            aria-label="Sign in"
          >
            <User />
          </Button>
        </div>
      </div>
    </header>
  )
}
