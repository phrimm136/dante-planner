import { getEGORankIconPath, getEGODetailImagePath } from '@/lib/identityUtils'
import type { EGORank } from '@/types/EGOTypes'

interface EGOHeaderProps {
  egoId: string
  name: string
  rank: EGORank
}

export function EGOHeader({ egoId, name, rank }: EGOHeaderProps) {
  const handleExpandImage = () => {
    const imagePath = getEGODetailImagePath(egoId)
    window.open(imagePath, '_blank')
  }

  return (
    <div className="space-y-4">
      {/* Rank and Name inline */}
      <div className="flex items-center gap-3">
        <img
          src={getEGORankIconPath(rank)}
          alt={`${rank} rank`}
          className="w-8 h-8 object-contain"
        />
        <h1 className="text-2xl font-bold">{name}</h1>
      </div>

      {/* Character Image with expand button */}
      <div className="relative bg-muted rounded-lg overflow-hidden">
        <img
          src={getEGODetailImagePath(egoId)}
          alt={name}
          className="w-full h-auto object-contain"
        />

        {/* Expand button */}
        <div className="absolute top-4 left-4">
          <button
            onClick={handleExpandImage}
            className="relative w-12 h-12"
            style={{
              backgroundImage: 'url(/images/UI/common/button.webp)',
              backgroundSize: 'cover',
              backgroundPosition: 'center',
            }}
          >
            <img
              src="/images/UI/common/buttonExpandImage.webp"
              alt="Expand image"
              className="w-full h-full object-contain p-2"
            />
          </button>
        </div>
      </div>
    </div>
  )
}
