import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { SECTION_STYLES } from '@/lib/constants'

/** The settings page's own shape, shown while its chunk and the auth read resolve. */
export function SettingsPageSkeleton() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <section className="rounded-lg border bg-card p-6">
        <TextSkeleton className="mb-4" size="base" width="md" />
        <TextSkeleton size="xs" width="lg" />
      </section>
    </div>
  )
}
