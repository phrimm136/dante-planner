export function Bad({
  isDefense,
  level,
  title,
}: {
  isDefense: boolean
  level: number
  title?: string
}) {
  return (
    <div>
      <button>Close</button>
      <img alt="Slash" />
      <img alt={isDefense ? 'Defense' : 'Attack'} />
      <span>{`Lv. ${level}`}</span>
      <span>{title ?? 'Untitled'}</span>
      <span>{(isDefense ? 'Locked' : undefined) ?? level}</span>
    </div>
  )
}
