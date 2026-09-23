interface SkillCardLayoutProps {
  skillId: string
  imageComposite: React.ReactNode
  infoPanel: React.ReactNode
  description: React.ReactNode
}

export function SkillCardLayout({
  skillId,
  imageComposite,
  infoPanel,
  description,
}: SkillCardLayoutProps) {
  return (
    <div className="p-4" data-skill-id={skillId}>
      <div className="flex gap-1">
        {imageComposite}
        {infoPanel}
      </div>

      {description}
    </div>
  )
}
