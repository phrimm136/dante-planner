import { getButtonBasePath, getButtonOnHoverPath } from '@/shared/assets'

interface OverlayButtonProps {
  onClick?: (() => void) | undefined
  disabled?: boolean | undefined
  iconSrc: string
  iconAlt: string
}

/**
 * Game-styled button for overlaying on a character image: textured base,
 * hover/press highlight layer, and an icon on top.
 */
export function OverlayButton({ onClick, disabled, iconSrc, iconAlt }: OverlayButtonProps) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="group relative w-12 h-12 disabled:opacity-50 disabled:cursor-not-allowed"
      style={{
        backgroundImage: `url(${getButtonBasePath()})`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      }}
    >
      <img
        src={getButtonOnHoverPath()}
        alt=""
        className="absolute inset-0 w-full h-full object-cover opacity-0 group-hover:opacity-100 group-active:opacity-100 group-disabled:group-hover:opacity-0 group-disabled:group-active:opacity-0 transition-opacity pointer-events-none scale-115"
      />
      <img src={iconSrc} alt={iconAlt} className="relative w-full h-full object-contain p-2" />
    </button>
  )
}
