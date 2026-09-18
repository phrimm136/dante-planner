import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

type TextSize = 'xs' | 'sm' | 'base' | 'lg' | 'xl' | '2xl'
type TextWidth = 'xs' | 'sm' | 'md' | 'lg' | 'full'

/** The line height of the text class the stub stands in for. Full literals for the scanner. */
const LINE_HEIGHT: Record<TextSize, string> = {
  xs: 'h-4',
  sm: 'h-5',
  base: 'h-6',
  lg: 'h-7',
  xl: 'h-7',
  '2xl': 'h-8',
}

const LINE_WIDTH: Record<TextWidth, string> = {
  xs: 'w-16',
  sm: 'w-24',
  md: 'w-32',
  lg: 'w-48',
  full: 'w-full',
}

interface TextSkeletonProps {
  lines?: number
  size?: TextSize
  width?: TextWidth
  className?: string
}

export function TextSkeleton({
  lines = 1,
  size = 'sm',
  width = 'md',
  className,
}: TextSkeletonProps) {
  return (
    <div className={cn('flex flex-col gap-1', className)}>
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} className={cn(LINE_HEIGHT[size], LINE_WIDTH[width])} />
      ))}
    </div>
  )
}
