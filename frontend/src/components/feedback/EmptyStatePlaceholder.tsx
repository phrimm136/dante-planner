import { cn } from '@/lib/utils'

interface EmptyStatePlaceholderProps {
  label: string
  onClick?: (() => void) | undefined
  readOnly?: boolean | undefined
  className?: string | undefined
}

const PLACEHOLDER_CLASSES =
  'flex items-center justify-center p-2 text-sm text-muted-foreground text-center border-2 border-dashed border-muted-foreground/50 rounded-lg'

export function EmptyStatePlaceholder({
  label,
  onClick,
  readOnly = false,
  className,
}: EmptyStatePlaceholderProps) {
  if (onClick) {
    return (
      <button
        type="button"
        onClick={readOnly ? undefined : onClick}
        disabled={readOnly}
        aria-label={label}
        className={cn(PLACEHOLDER_CLASSES, !readOnly && 'selectable', className)}
      >
        {label}
      </button>
    )
  }

  return <div className={cn(PLACEHOLDER_CLASSES, className)}>{label}</div>
}
