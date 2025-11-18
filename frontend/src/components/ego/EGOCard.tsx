import { Link } from '@tanstack/react-router'
import type { EGO } from '@/types/EGOTypes'

interface EGOCardProps {
  ego: EGO
}

export function EGOCard({ ego }: EGOCardProps) {
  const { id, name, rank } = ego

  return (
    <Link
      to={`/ego/${id}`}
      className="block relative w-40 h-56 shrink-0 bg-card border border-border rounded-md"
    >
      {/* Mock placeholder content */}
      <div className="absolute inset-0 flex flex-col items-center justify-center p-4 text-center">
        <div className="text-sm font-bold mb-2">{name}</div>
        <div className="text-xs text-muted-foreground">Rank: {rank}</div>
        <div className="text-xs text-muted-foreground mt-2">ID: {id}</div>
      </div>
    </Link>
  )
}
