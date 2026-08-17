import { SECTION_STYLES } from '@/lib/constants'
interface LoadingStateProps {
  message?: string
}

/**
 * LoadingState - Reusable loading state component
 *
 * Displays a centered loading message with consistent styling
 */
export function LoadingState({ message = 'Loading...' }: LoadingStateProps) {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <div className="flex items-center justify-center h-64">
        <div className={SECTION_STYLES.TEXT.muted}>{message}</div>
      </div>
    </div>
  )
}
