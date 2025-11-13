import { useEffect } from 'react'
import { useTheme } from '@/hooks/useTheme'

/**
 * Component to sync HTML dark class with theme state
 * Applies or removes 'dark' class on document root element
 */
export function ThemeSync() {
  const { theme } = useTheme()

  useEffect(() => {
    const root = document.documentElement

    if (theme === 'dark') {
      root.classList.add('dark')
    } else {
      root.classList.remove('dark')
    }
  }, [theme])

  return null
}
