import { DiscordIcon } from '@/components/ui/DiscordIcon'
import { DISCORD_BLURPLE, DISCORD_INVITE_URL, SECTION_STYLES } from '@/lib/constants'

export function SideLinkSection() {
  return (
    <section className={SECTION_STYLES.LAYOUT.column}>
      <div className="h-7" aria-hidden />
      <div className="flex flex-1 flex-col gap-2">
        <a
          href={DISCORD_INVITE_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="flex flex-1 items-center justify-center gap-3 rounded-md text-white transition-opacity hover:opacity-90"
          style={{ backgroundColor: DISCORD_BLURPLE }}
        >
          <DiscordIcon size={24} className="shrink-0" />
          <span className="text-sm font-semibold">Discord (new!)</span>
        </a>

        <div className="flex flex-1 items-center justify-center rounded-md border border-dashed border-border" />
      </div>
    </section>
  )
}
