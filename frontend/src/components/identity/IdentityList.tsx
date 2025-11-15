import { useIdentityData } from '@/hooks/useIdentityData'
import { IdentityCard } from './IdentityCard'
import { parseBracketNotation } from '@/lib/identityUtils'

interface IdentityListProps {
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
}

export function IdentityList({ selectedSinners, selectedKeywords }: IdentityListProps) {
  const identities = useIdentityData()

  // Filter identities based on selected sinners and keywords
  // If no filters selected, show all identities
  // Sinner filter: must match selected sinner (AND logic)
  // Keyword filter: must have ALL selected keywords (AND logic)
  // Both filters: must match sinner AND have all keywords (AND between filters)
  const filteredIdentities = identities.filter((identity) => {
    // Sinner filter
    if (selectedSinners.size > 0) {
      const sinnerName = parseBracketNotation(identity.sinner)
      if (!selectedSinners.has(sinnerName)) {
        return false
      }
    }

    // Keyword filter - identity must have ALL selected keywords
    if (selectedKeywords.size > 0) {
      const identityKeywords = identity.keywords.map((keyword) =>
        parseBracketNotation(keyword)
      )
      const hasAllKeywords = Array.from(selectedKeywords).every((selectedKeyword) =>
        identityKeywords.includes(selectedKeyword)
      )
      if (!hasAllKeywords) {
        return false
      }
    }

    return true
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
