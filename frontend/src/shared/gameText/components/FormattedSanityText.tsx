import { applyTextStyles } from '../lib/unityRichText'

interface FormattedSanityTextProps {
  text: string
  className?: string
}

export function FormattedSanityText({ text, className }: FormattedSanityTextProps) {
  if (!text) return null

  return <span className={className}>{applyTextStyles(text)}</span>
}
