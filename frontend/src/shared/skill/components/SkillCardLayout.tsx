interface SkillCardLayoutProps {
  imageComposite: React.ReactNode
  infoPanel: React.ReactNode
  description: React.ReactNode
}

export function SkillCardLayout({ imageComposite, infoPanel, description }: SkillCardLayoutProps) {
  return (
    <div className="p-4">
      <div className="flex gap-1">
        {imageComposite}
        {infoPanel}
      </div>

      {description}
    </div>
  )
}
