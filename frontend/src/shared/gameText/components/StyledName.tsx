import { useTranslation } from 'react-i18next'
import { getAttributeColors } from '@/shared/gameData'
import { getDisplayFontForLanguage } from '@/lib/utils'
import { Skeleton } from '@/components/ui/skeleton'

interface StyledSkillNameProps {
  name: string
  attributeType?: string
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

export function StyledSkillName({ name, attributeType }: StyledSkillNameProps) {
  const { i18n } = useTranslation()
  const { primary, dark } = getAttributeColors(attributeType)
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  return (
    <div className="w-fit">
      <div
        style={{
          color: TEXT_COLOR,
          marginBottom: '10px',
          width: '100%',
          backgroundImage: generateBackgroundGradient(dark),
        }}
      >
        <div
          style={{
            textShadow: '2px 2px 2px black',
            padding: '0.1em 3.75em 0 0.3em',
            textAlign: 'left',
            backgroundImage: generateStripeGradient(primary),
          }}
        >
          <span style={{ fontSize: '20px', ...displayStyle }}>{name}</span>
        </div>
      </div>
    </div>
  )
}

export function StyledNameSkeleton({ attributeType }: { attributeType?: string | undefined }) {
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
            padding: '0.3em 3.75em 0.3em 10px',
            backgroundImage: generateStripeGradient(primary),
          }}
        >
          <Skeleton className="h-5 w-20" style={{ backgroundColor: TEXT_COLOR }} />
        </div>
      </div>
    </div>
  )
}
