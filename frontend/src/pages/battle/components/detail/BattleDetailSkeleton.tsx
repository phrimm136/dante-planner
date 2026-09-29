import { DetailSectionSkeleton } from '@/components/feedback/DetailSectionSkeleton'
import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { EntityDetailLayout } from '@/components/layout/EntityDetailLayout'
import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { BATTLE_DETAIL_SPRITE_ASPECT_RATIO } from '../../lib/cardLayout'

function SkillCardBlock() {
  return (
    <div className="p-4">
      <div className="flex gap-1">
        <div className="w-24 h-24 rounded bg-muted" />
        <div className="flex-1">
          <TextSkeleton lines={3} width="full" />
        </div>
      </div>
      <TextSkeleton lines={3} width="full" />
    </div>
  )
}

export function BattleDetailSkeleton() {
  return (
    <div data-slot="page-skeleton">
      <EntityDetailLayout
        header={
          <div className="space-y-4">
            <TextSkeleton size="2xl" width="lg" />
            <div
              className="w-full rounded-lg bg-muted"
              style={{ aspectRatio: BATTLE_DETAIL_SPRITE_ASPECT_RATIO }}
            />
          </div>
        }
        metadata={
          <div className="space-y-4">
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
            <LabeledPanel>
              <TextSkeleton size="xs" lines={3} width="full" />
            </LabeledPanel>
          </div>
        }
        selector={
          <div className="bg-background/95 backdrop-blur-sm border rounded-lg p-4">
            <div className="flex items-center gap-2">
              <TextSkeleton size="lg" width="xs" />
              <div className="flex gap-1">
                <div className="w-16 h-10 rounded bg-muted" />
                <div className="w-16 h-10 rounded bg-muted" />
              </div>
            </div>
          </div>
        }
      >
        <DetailSectionSkeleton>
          <div className="border rounded divide-y">
            <SkillCardBlock />
            <SkillCardBlock />
            <SkillCardBlock />
          </div>
        </DetailSectionSkeleton>
        <DetailSectionSkeleton>
          <div className="border rounded p-4 space-y-3">
            <TextSkeleton size="base" width="sm" />
            <TextSkeleton lines={2} width="full" />
          </div>
        </DetailSectionSkeleton>
      </EntityDetailLayout>
    </div>
  )
}
