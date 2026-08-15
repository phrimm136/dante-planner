import { Link } from '@tanstack/react-router'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

/** Detail routes a keyword backlink can point at. */
type KeywordBacklinkRoute = '/identity/$id' | '/ego/$id' | '/ego-gift/$id'

interface KeywordBacklinkListProps {
  /** Section label key in the `database` namespace */
  labelKey: string
  /** Entity ids to link, in display order */
  ids: string[]
  /** Localized names keyed by entity id */
  names: Record<string, string>
  /** Detail route the entries link to */
  to: KeywordBacklinkRoute
  /** Entry text; the localized name alone when omitted */
  formatLabel?: (id: string, name: string) => ReactNode
}

/**
 * Labeled row of comma-separated entity links for one keyword backlink kind.
 * Renders `-` when the keyword has no entities of that kind, and falls back to
 * the raw id for an entity the active language has no name for.
 */
export function KeywordBacklinkList({
  labelKey,
  ids,
  names,
  to,
  formatLabel,
}: KeywordBacklinkListProps) {
  const { t } = useTranslation('database')

  return (
    <div className="space-y-1.5">
      <div className="text-xs text-muted-foreground font-medium uppercase tracking-wide">
        {t(labelKey)}
      </div>
      {ids.length === 0 ? (
        <div className="text-sm text-muted-foreground/60">-</div>
      ) : (
        <div className="text-sm">
          {ids.map((entityId, idx) => {
            const name = names[entityId] ?? entityId
            return (
              <span key={entityId}>
                {idx > 0 && ', '}
                <Link to={to} params={{ id: entityId }} className="hover:underline text-foreground">
                  {formatLabel ? formatLabel(entityId, name) : name}
                </Link>
              </span>
            )
          })}
        </div>
      )}
    </div>
  )
}
