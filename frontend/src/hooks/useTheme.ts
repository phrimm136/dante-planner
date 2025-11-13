import { useContext } from 'react'
import { ThemeContext } from '@/contexts/ThemeContext'

/**
 * Hook to access theme context
 * @returns Theme context with current theme and toggle function
 * @throws Error if used outside ThemeProvider
 */
export function useTheme() {
  const context = useContext(ThemeContext)

  if (context === undefined) {
    throw new Error('useTheme must be used within a ThemeProvider')
  }

  return context
}
