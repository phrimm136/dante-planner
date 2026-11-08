import { TextSkeleton } from '@/components/feedback/TextSkeleton'

export function SkillCardSkeleton() {
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
