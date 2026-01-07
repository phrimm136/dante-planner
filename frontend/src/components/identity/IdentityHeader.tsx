import { useEffect, useState } from 'react'
import { getRarityIconPath, getIdentityDetailImagePath } from '@/lib/assetPaths'

type ImageVariant = 'normal' | 'gacksung'

interface IdentityHeaderProps {
  identityId: string
  name: string
  rank: number
  uptie: number
}

export function IdentityHeader({ identityId, name, rank, uptie }: IdentityHeaderProps) {
  // Gacksung image only available for rank > 1 AND uptie >= 3
  const canShowGacksung = rank > 1 && uptie >= 3
  const [imageVariant, setImageVariant] = useState<ImageVariant>(canShowGacksung ? 'gacksung' : 'normal')

  // Sync image variant with gacksung availability
  // - uptie >= 3: automatically show gacksung
  // - uptie < 3: automatically show normal
  useEffect(() => {
    setImageVariant(canShowGacksung ? 'gacksung' : 'normal')
  }, [canShowGacksung])

  const handleSwapImage = () => {
    setImageVariant((prev: ImageVariant) => (prev === 'gacksung' ? 'normal' : 'gacksung'))
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
          src={getRarityIconPath(rank)}
          alt={`${rank} star`}
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
          {/* Swap button - disabled when gacksung image not available */}
          <button
            onClick={handleSwapImage}
            disabled={!canShowGacksung}
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
