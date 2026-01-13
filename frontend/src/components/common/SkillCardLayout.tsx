interface SkillCardLayoutProps {
  imageComposite: React.ReactNode
  infoPanel: React.ReactNode
  description: React.ReactNode
}

/**
 * SkillCardLayout - Reusable skill card layout structure
 *
 * Layout:
 * - Top section: Image composite + Info panel (horizontal)
 * - Bottom section: Skill description
 */
export function SkillCardLayout({
  imageComposite,
  infoPanel,
  description,
}: SkillCardLayoutProps) {
  return (
    <div className="p-4">
      {/* Top section: Image + Info */}
      <div className="flex gap-1">
        {imageComposite}
        {infoPanel}
      </div>

      {/* Bottom section: Skill description */}
      {description}
    </div>
  )
}
