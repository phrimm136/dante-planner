import { cn } from '@/lib/utils'

interface EmptyStatePlaceholderProps {
  /** Already-translated text, also the accessible name when clickable */
  label: string
  /** Present → the placeholder is a button */
  onClick?: (() => void) | undefined
  readOnly?: boolean | undefined
  /** Parent-context sizing, e.g. "size-full", "h-full" */
  className?: string | undefined
}

const PLACEHOLDER_CLASSES =
  'flex items-center justify-center p-2 text-sm text-muted-foreground text-center border-2 border-dashed border-muted-foreground/50 rounded-lg'

/**
 * The dashed box standing in for an unfilled selection.
 *
 * It carries no width or height of its own: the box comes from the parent, through
 * `className`.
 */
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
