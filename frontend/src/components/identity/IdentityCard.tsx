import { Link } from '@tanstack/react-router'
import type { Identity } from '@/hooks/useIdentityData'
import {
  getIdentityImagePath,
  getUptieFramePath,
  getSinnerBGPath,
  getSinnerIconPath,
} from '@/lib/identityUtils'

interface IdentityCardProps {
  identity: Identity
}

export function IdentityCard({ identity }: IdentityCardProps) {
  const { id, star, sinner } = identity

  return (
    <Link
      to={`/identity/${id}`}
      className="block relative w-40 h-56 shrink-0"
    >
      {/* Clipping container for identity image to fit within frame */}
      <div className="absolute inset-0 flex items-center justify-center overflow-hidden">
        {/* Layer 1: Identity Image (cropped to fit frame) */}
        <img
          src={getIdentityImagePath(id)}
          alt={identity.name}
          className=" w-[88%] h-[88%] object-cover"
        />
      </div>

      {/* Layer 2: Uptie Frame (transparent border overlay) */}
      <img
        src={getUptieFramePath(star)}
        alt={`${star} star frame`}
        className="absolute inset-0 w-full h-full object-contain pointer-events-none"
      />

      {/* Layer 3: Sinner BG (upper-right corner, not cropped) */}
      <img
        src={getSinnerBGPath(star)}
        alt="Sinner background"
        className="absolute -top-2 -right-2 w-14 h-14 object-contain pointer-events-none"
      />

      {/* Layer 4: Sinner Icon (upper-right corner, topmost) */}
      <img
        src={getSinnerIconPath(sinner)}
        alt={sinner}
        className="absolute -top-1 -right-1 w-12 h-12 object-contain pointer-events-none"
      />
    </Link>
  )
}
