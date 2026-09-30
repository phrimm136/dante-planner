import { useTranslation } from 'react-i18next'
import { getAttributeColors } from '@/shared/gameData'
import { getDisplayFontForLanguage } from '@/lib/utils'
import { Skeleton } from '@/components/ui/skeleton'

export interface PlateGeometry {
  height: string
  fontSize: string
  paddingLeft: string
  paddingRight: string
  paddingBottom: string
}

interface StyledSkillNameProps {
  name: string
  attributeType?: string
  plate?: PlateGeometry
}

const TEXT_COLOR = '#eecea4'

function generateStripeGradient(color: string): string {
  return `linear-gradient(290deg,
    transparent 2.5em,
    ${color} 2.5em, ${color} 2.65em,
    transparent 2.65em, transparent 2.8em,
    ${color} 2.8em, ${color} 2.95em,
    transparent 2.95em, transparent 3.1em,
    ${color} 3.1em, ${color} 3.25em,
    transparent 3.25em, transparent 3.4em,
    ${color} 3.4em
  )`
}

function generateBackgroundGradient(darkColor: string): string {
  return `linear-gradient(255deg, transparent 1.5em, ${darkColor} 1.5em)`
}

export function StyledSkillName({ name, attributeType, plate }: StyledSkillNameProps) {
  const { i18n } = useTranslation()
  const { primary, dark } = getAttributeColors(attributeType)
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  return (
    <div className="w-fit">
      <div
        data-slot="name-plate"
        style={{
          color: `var(--name-text, ${TEXT_COLOR})`,
          ...(plate === undefined && { marginBottom: '10px' }),
          width: '100%',
          backgroundImage: generateBackgroundGradient(`var(--name-tail, ${dark})`),
        }}
      >
        <div
          style={{
            textShadow: '2px 2px 2px black',
            ...(plate === undefined
              ? { padding: '0.1em 3.75em 0 0.3em' }
              : {
                  height: plate.height,
                  paddingTop: '0.1em',
                  paddingLeft: plate.paddingLeft,
                  paddingRight: plate.paddingRight,
                  paddingBottom: plate.paddingBottom,
                }),
            textAlign: 'left',
            backgroundImage: generateStripeGradient(`var(--name-body, ${primary})`),
          }}
        >
          <span style={{ fontSize: plate?.fontSize ?? '20px', ...displayStyle }}>{name}</span>
        </div>
      </div>
    </div>
  )
}

export function StyledNameSkeleton({
  attributeType,
  plate,
}: {
  attributeType?: string | undefined
  plate?: PlateGeometry
}) {
  const { primary, dark } = getAttributeColors(attributeType)

  return (
    <div className="w-fit">
      <div
        style={{
          marginBottom: '5px',
          padding: '2px',
          width: '100%',
          backgroundImage: generateBackgroundGradient(dark),
        }}
      >
        <div
          style={{
            ...(plate === undefined
              ? { padding: '0.3em 3.75em 0.3em 10px' }
              : {
                  height: plate.height,
                  fontSize: plate.fontSize,
                  paddingTop: '0.3em',
                  paddingLeft: plate.paddingLeft,
                  paddingRight: plate.paddingRight,
                  paddingBottom: plate.paddingBottom,
                }),
            backgroundImage: generateStripeGradient(primary),
          }}
        >
          <Skeleton className="h-5 w-20" style={{ backgroundColor: TEXT_COLOR }} />
        </div>
      </div>
    </div>
  )
}
