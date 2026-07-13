import { DiscordIcon } from '@/components/ui/DiscordIcon'
import { DISCORD_BLURPLE, DISCORD_INVITE_URL } from '@/lib/constants'

export function SideLinkSection() {
  return (
    <section className="flex flex-col gap-4">
      {/* Spacer matching announcement header row */}
      <div className="h-7" aria-hidden />
      <div className="flex flex-1 flex-col gap-2">
        {/* Discord invite */}
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

        {/* Ko-fi placeholder */}
        <div className="flex flex-1 items-center justify-center rounded-md border border-dashed border-border" />
      </div>
    </section>
  )
}
