import type { ReactNode } from 'react'

const LINK_PATTERN = /(https?:\/\/[^\s)]+|[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/g

interface LinkifyTextProps {
  text: string
}

/** Renders text with URLs and email addresses turned into links. */
export function LinkifyText({ text }: LinkifyTextProps) {
  return <>{linkifyText(text)}</>
}

/** For callers outside JSX; inside JSX use `<LinkifyText />`. */
export function linkifyText(text: string): ReactNode[] {
  const parts: ReactNode[] = []
  let lastIndex = 0

  for (const match of text.matchAll(LINK_PATTERN)) {
    const matchStr = match[0]
    const index = match.index
    if (index > lastIndex) {
      parts.push(text.slice(lastIndex, index))
    }
    const href = matchStr.includes('@') ? `mailto:${matchStr}` : matchStr
    parts.push(
      <a
        key={index}
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        className="text-primary underline"
      >
        {matchStr}
      </a>,
    )
    lastIndex = index + matchStr.length
  }

  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex))
  }

  return parts
}
