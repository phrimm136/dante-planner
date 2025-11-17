import { useState } from 'react'
import { getRarityIconPath, getIdentityDetailImagePath } from '@/lib/identityUtils'
import type { ImageVariant } from '@/types/IdentityTypes'

interface IdentityHeaderProps {
  identityId: string
  name: string
  grade: number
}

export function IdentityHeader({ identityId, name, grade }: IdentityHeaderProps) {
  const is1Star = grade === 1
  const [imageVariant, setImageVariant] = useState<ImageVariant>(is1Star ? 'normal' : 'gacksung')

  const handleSwapImage = () => {
    setImageVariant((prev) => (prev === 'gacksung' ? 'normal' : 'gacksung'))
  }

  const handleExpandImage = () => {
    const imagePath = getIdentityDetailImagePath(identityId, imageVariant)
    window.open(imagePath, '_blank')
  }

  const currentImagePath = getIdentityDetailImagePath(identityId, imageVariant)

  return (
    <div className="space-y-4">
      {/* Grade and Name inline */}
      <div className="flex items-center gap-3">
        <img
          src={getRarityIconPath(grade)}
          alt={`${grade} star`}
          className="w-8 h-8 object-contain"
        />
        <h1 className="text-2xl font-bold">{name}</h1>
      </div>

      {/* Character Image with overlay buttons */}
      <div className="relative bg-muted rounded-lg overflow-hidden">
        <img
          src={currentImagePath}
          alt={name}
          className="w-full h-auto object-contain"
          onError={(e) => {
            // Fallback to normal variant if gacksung fails
            if (imageVariant === 'gacksung') {
              const img = e.target as HTMLImageElement
              img.src = getIdentityDetailImagePath(identityId, 'normal')
              setImageVariant('normal')
            }
          }}
        />

        {/* Stacked buttons */}
        <div className="absolute top-4 left-4 flex flex-col gap-2">
          {/* Swap button */}
          <button
            onClick={handleSwapImage}
            disabled={is1Star}
            className="relative w-12 h-12 disabled:opacity-50 disabled:cursor-not-allowed"
            style={{
              backgroundImage: 'url(/images/UI/common/button.webp)',
              backgroundSize: 'cover',
              backgroundPosition: 'center',
            }}
          >
            <img
              src="/images/UI/common/buttonSwapImage.webp"
              alt="Swap image"
              className="w-full h-full object-contain p-2"
            />
          </button>

          {/* Expand button */}
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
