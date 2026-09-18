interface DetailRightPanelProps {
  selector: React.ReactNode
  children: React.ReactNode
}

export function DetailRightPanel({ selector, children }: DetailRightPanelProps) {
  return (
    <div className="flex flex-col h-full">
      <div className="sticky top-0 z-10 bg-background pb-4">{selector}</div>
      <div className="space-y-6">{children}</div>
    </div>
  )
}
