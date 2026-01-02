import { getAttributeColors } from '@/lib/colorUtils'

interface StyledSkillNameProps {
  /** Skill or passive name to display */
  name: string
  /** Attribute type for color (e.g., "CRIMSON", "AZURE") - case-insensitive */
  attributeType?: string
}

/** Text color for all styled skill names (cream/gold) */
const TEXT_COLOR = '#eecea4'

/**
 * Generates CSS gradient for decorative diagonal stripes
 * Creates 4 stripes at the right edge of the component
 * @param color - Stripe color (hex)
 * @returns CSS linear-gradient value
 */
function generateStripeGradient(color: string): string {
  // Stripe pattern: 4 stripes starting at 2.5em, each 0.15em wide with 0.15em gaps
  // Angle: 290deg (diagonal from bottom-right)
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

/**
 * Generates CSS gradient for dark background
 * Creates angled background that starts after a transparent corner
 * @param darkColor - Background color (hex)
 * @returns CSS linear-gradient value
 */
function generateBackgroundGradient(darkColor: string): string {
  // Angle: 255deg creates diagonal cut at the left edge
  return `linear-gradient(255deg, transparent 1.5em, ${darkColor} 1.5em)`
}

/**
 * StyledSkillName - Wiki-style gradient banner for skill/passive names
 *
 * Features:
 * - Attribute-based coloring (CRIMSON, AZURE, etc.)
 * - Dark gradient background with angled left edge
 * - Decorative diagonal stripes on right side
 * - Text shadow for readability
 * - Cream/gold text color
 *
 * Pattern: Standalone display component with inline styles for dynamic gradients
 */
export function StyledSkillName({ name, attributeType }: StyledSkillNameProps) {
  const { primary, dark } = getAttributeColors(attributeType)

  return (
    <div style={{ width: 'fit-content' }}>
      {/* Outer container: dark gradient background */}
      <div
        style={{
          color: TEXT_COLOR,
          marginBottom: '5px',
          padding: '2px',
          width: '100%',
          backgroundImage: generateBackgroundGradient(dark),
        }}
      >
        {/* Inner container: stripe decoration + text */}
        <div
          style={{
            textShadow: '2px 2px 2px black',
            padding: '0.3em 10px',
            textAlign: 'left',
            backgroundImage: generateStripeGradient(primary),
          }}
        >
          <span style={{ marginRight: '3em' }}>{name}</span>
        </div>
      </div>
    </div>
  )
}
