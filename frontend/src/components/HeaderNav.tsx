import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Menu, ChevronDown } from 'lucide-react'

import { useAuthQueryNonBlocking } from '@/hooks/useAuthQuery'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import { cn } from '@/lib/utils'

/**
 * Navigation structure with categories and items
 */
const NAV_STRUCTURE = {
  database: {
    labelKey: 'header.nav.categories.database',
    items: [
      { key: 'identity', path: '/identity', labelKey: 'header.nav.identity' },
      { key: 'ego', path: '/ego', labelKey: 'header.nav.ego' },
      { key: 'egoGift', path: '/ego-gift', labelKey: 'header.nav.egoGift' },
    ],
  },
  planner: {
    labelKey: 'header.nav.categories.planner',
    items: [
      { key: 'mirrorDungeon', path: '/planner/md', labelKey: 'header.nav.mirrorDungeon' },
      { key: 'extraction', path: '/planner/extraction', labelKey: 'header.nav.extraction' },
      { key: 'deckBuilder', path: '/planner/deck', labelKey: 'header.nav.deckBuilder' },
    ],
  },
  moderator: {
    labelKey: 'header.nav.categories.moderator',
    items: [
      { key: 'moderation', path: '/moderation', labelKey: 'header.nav.moderator' },
    ],
    requiresRole: ['MODERATOR', 'ADMIN'] as const,
  },
} as const

type CategoryKey = keyof typeof NAV_STRUCTURE

/**
 * Desktop navigation with hover dropdowns
 */
function DesktopNav() {
  const { t } = useTranslation()
  const { data: user } = useAuthQueryNonBlocking()
  const [openCategory, setOpenCategory] = useState<CategoryKey | null>(null)

  return (
    <nav className="hidden lg:flex items-center gap-1 ml-8">
      {(Object.entries(NAV_STRUCTURE) as [CategoryKey, typeof NAV_STRUCTURE[CategoryKey]][]).map(
        ([categoryKey, category]) => {
          // Filter moderator section based on role
          if ('requiresRole' in category && (!user || !category.requiresRole.includes(user.role as 'MODERATOR' | 'ADMIN'))) {
            return null
          }

          const isOpen = openCategory === categoryKey

          return (
          <div
            key={categoryKey}
            className="relative group"
            onMouseLeave={() => { setOpenCategory(null); }}
          >
            {/* Category trigger */}
            <Button
              variant="ghost"
              className="flex items-center gap-1"
              onClick={() => { setOpenCategory(isOpen ? null : categoryKey); }}
              onMouseEnter={() => { setOpenCategory(categoryKey); }}
            >
              {t(category.labelKey)}
              <ChevronDown className={cn(
                "size-4 transition-transform",
                isOpen && "rotate-180"
              )} />
            </Button>

            {/* Dropdown menu */}
            <div className={cn(
              "absolute left-0 top-full pt-1 transition-all duration-200 z-50",
              isOpen ? "opacity-100 visible" : "opacity-0 invisible"
            )}>
              <div className="bg-popover border rounded-md shadow-md py-1 min-w-[160px]">
                {category.items.map((item) => (
                  <Link
                    key={item.key}
                    to={item.path}
                    onClick={() => { setOpenCategory(null); }}
                    className="block px-4 py-2 text-sm hover:bg-accent hover:text-accent-foreground transition-colors"
                  >
                    {t(item.labelKey)}
                  </Link>
                ))}
              </div>
            </div>
          </div>
          )
        }
      )}
    </nav>
  )
}

/**
 * Mobile navigation with sheet overlay
 */
function MobileNav() {
  const { t } = useTranslation()
  const { data: user } = useAuthQueryNonBlocking()
  const [open, setOpen] = useState(false)

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="lg:hidden"
          aria-label={t('header.nav.menu')}
        >
          <Menu className="size-5" />
        </Button>
      </SheetTrigger>
      <SheetContent side="top" className="pt-12">
        <SheetHeader className="sr-only">
          <SheetTitle>{t('header.nav.menu')}</SheetTitle>
        </SheetHeader>

        <nav className="flex flex-col gap-4 px-2">
          {(Object.entries(NAV_STRUCTURE) as [CategoryKey, typeof NAV_STRUCTURE[CategoryKey]][]).map(
            ([categoryKey, category]) => {
              // Filter moderator section based on role
              if ('requiresRole' in category && (!user || !category.requiresRole.includes(user.role as 'MODERATOR' | 'ADMIN'))) {
                return null
              }

              return (
              <div key={categoryKey} className="space-y-2">
                {/* Category label */}
                <div className="text-sm font-semibold text-muted-foreground px-2">
                  {t(category.labelKey)}
                </div>

                {/* Items (always expanded, indented) */}
                <div className="flex flex-col gap-1 pl-4">
                  {category.items.map((item) => (
                    <Link
                      key={item.key}
                      to={item.path}
                      onClick={() => { setOpen(false); }}
                      className={cn(
                        "block px-3 py-2 rounded-md text-sm",
                        "hover:bg-accent hover:text-accent-foreground transition-colors"
                      )}
                    >
                      {t(item.labelKey)}
                    </Link>
                  ))}
                </div>
              </div>
              )
            }
          )}
        </nav>
      </SheetContent>
    </Sheet>
  )
}

/**
 * Header navigation namespace
 * - HeaderNav.Desktop: Inline dropdown menus with hover (lg+)
 * - HeaderNav.Mobile: Hamburger button that opens sheet overlay (<lg)
 */
export const HeaderNav = {
  Desktop: DesktopNav,
  Mobile: MobileNav,
}
