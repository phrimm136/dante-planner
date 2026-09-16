import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { CharacterImageSection } from '@/components/layout/CharacterImageSection'
import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { DETAIL_IMAGE_ASPECT_RATIO } from '@/lib/constants'

/**
 * Identity detail: Header image + 3 stat panels + traits (left)
 * Skills + Passives + Sanity panels (right)
 */
export function IdentityDetailSkeleton() {
  return (
    <DetailPageSkeleton
      left={
        <div className="space-y-4">
          {/* Title area: the rank row and the name row, with no gap between them */}
          <div>
            {/* Rank icon row */}
            <div className="flex justify-end">
              <div className="h-6 w-16 rounded bg-muted" />
            </div>

            {/* Sinner icon + identity name */}
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 rounded-full bg-muted" />
              <TextSkeleton size="2xl" width="lg" />
            </div>
          </div>

          {/* Character image */}
          <CharacterImageSection aspectRatio={DETAIL_IMAGE_ASPECT_RATIO.IDENTITY} />

          {/* Status, Resistance, and Stagger panels */}
          <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
            <LabeledPanel>
              <TextSkeleton size="xs" lines={2} width="full" />
            </LabeledPanel>
            <LabeledPanel>
              <TextSkeleton size="xs" lines={2} width="full" />
            </LabeledPanel>
            <div className="col-span-2 md:col-span-1">
              <LabeledPanel>
                <TextSkeleton size="xs" lines={2} width="full" />
              </LabeledPanel>
            </div>
          </div>

          {/* Traits */}
          <LabeledPanel>
            <TextSkeleton size="base" width="full" />
          </LabeledPanel>

          {/* Battle keywords */}
          <LabeledPanel>
            <TextSkeleton size="xs" width="full" />
          </LabeledPanel>

          {/* Season and release date */}
          <div className="grid grid-cols-2 gap-2">
            <LabeledPanel>
              <TextSkeleton size="xs" width="sm" />
            </LabeledPanel>
            <LabeledPanel>
              <TextSkeleton size="xs" width="sm" />
            </LabeledPanel>
          </div>
        </div>
      }
      right={
        <div className="flex flex-col h-full">
          {/* Sticky tier and level selector */}
          <div className="sticky top-0 z-10 bg-background pb-4">
            <div className="bg-background/95 backdrop-blur-sm border rounded-lg p-4">
              <div className="flex flex-col sm:flex-row sm:items-center sm:gap-6">
                <div className="flex items-center gap-2">
                  <TextSkeleton size="lg" width="xs" />
                  <div className="flex gap-1">
                    <div className="w-10 h-10 rounded bg-muted" />
                    <div className="w-10 h-10 rounded bg-muted" />
                    <div className="w-10 h-10 rounded bg-muted" />
                    <div className="w-10 h-10 rounded bg-muted" />
                  </div>
                </div>
                <div className="flex items-center gap-3 flex-1">
                  <TextSkeleton size="lg" width="xs" />
                  <div className="flex-1 max-w-[200px]">
                    <div className="h-2 rounded-full bg-muted" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="space-y-6">
            {/* Skill tabs + skill card */}
            <div className="space-y-4">
              <div className="flex gap-2">
                <div className="flex-1 py-2 px-4 rounded bg-muted">
                  <TextSkeleton size="base" width="full" />
                </div>
                <div className="flex-1 py-2 px-4 rounded bg-muted">
                  <TextSkeleton size="base" width="full" />
                </div>
                <div className="flex-1 py-2 px-4 rounded bg-muted">
                  <TextSkeleton size="base" width="full" />
                </div>
                <div className="flex-1 py-2 px-4 rounded bg-muted">
                  <TextSkeleton size="base" width="full" />
                </div>
              </div>

              <div className="border rounded divide-y">
                <div className="p-4">
                  <div className="flex gap-1">
                    <div className="w-24 h-24 rounded bg-muted" />
                    <div className="flex-1">
                      <TextSkeleton lines={3} width="full" />
                    </div>
                  </div>
                  <TextSkeleton lines={3} width="full" />
                </div>
              </div>
            </div>

            {/* Battle and support passives */}
            <div className="border rounded p-4 space-y-4">
              <div className="space-y-3">
                <TextSkeleton size="base" width="sm" />
                <TextSkeleton lines={2} width="full" />
              </div>
              <div className="space-y-3">
                <TextSkeleton size="base" width="sm" />
                <TextSkeleton lines={2} width="full" />
              </div>
            </div>

            {/* Panic type and sanity conditions */}
            <div className="border rounded p-4 space-y-4">
              <TextSkeleton lines={3} width="full" />
            </div>
          </div>
        </div>
      }
    />
  )
}
