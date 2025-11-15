import { useIdentityData } from '@/hooks/useIdentityData'
import { IdentityCard } from './IdentityCard'
import { parseSinnerName } from '@/lib/identityUtils'

interface IdentityListProps {
  selectedSinners: Set<string>
}

export function IdentityList({ selectedSinners }: IdentityListProps) {
  const identities = useIdentityData()

  // Filter identities based on selected sinners
  // If no sinners selected, show all identities
  const filteredIdentities =
    selectedSinners.size === 0
      ? identities
      : identities.filter((identity) => {
          const sinnerName = parseSinnerName(identity.sinner)
          return selectedSinners.has(sinnerName)
        })

  return (
    <div className="bg-muted border border-border rounded-md p-6">
      {/* Responsive grid layout with padding for sinner icons/bg */}
      <div className="pt-4">
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8 gap-4 justify-items-center">
          {filteredIdentities.map((identity) => (
            <IdentityCard key={identity.id} identity={identity} />
          ))}
        </div>
      </div>
    </div>
  )
}
