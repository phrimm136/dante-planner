import { useTranslation } from 'react-i18next'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { TrendingUp, TrendingDown, AlertTriangle } from 'lucide-react'

interface VotingAnalyticsProps {
  upvotes: number
  downvotes: number
  publishedAt?: string
  hasWarningFlags?: boolean
}

/**
 * VotingAnalytics - Display vote statistics for moderator review
 *
 * Shows upvotes, downvotes, net votes, vote velocity, and warning flags.
 * Used in RecommendedPlannerList for moderation review.
 *
 * @param upvotes - Total upvotes
 * @param downvotes - Total downvotes
 * @param publishedAt - ISO timestamp of publish date (for velocity calculation)
 * @param hasWarningFlags - Whether suspicious voting patterns detected
 */
export function VotingAnalytics({
  upvotes,
  downvotes,
  publishedAt,
  hasWarningFlags = false,
}: VotingAnalyticsProps) {
  const { t } = useTranslation('planner')

  const netVotes = upvotes - downvotes
  const totalVotes = upvotes + downvotes

  // Calculate vote velocity (votes per hour)
  let voteVelocity = 0
  if (publishedAt) {
    const publishDate = new Date(publishedAt)
    const now = new Date()
    const hoursElapsed = (now.getTime() - publishDate.getTime()) / (1000 * 60 * 60)
    if (hoursElapsed > 0) {
      voteVelocity = totalVotes / hoursElapsed
    }
  }

  return (
    <Card className="w-full">
      <CardHeader>
        <CardTitle className="text-sm font-medium flex items-center gap-2">
          {t('moderation.votingStats')}
          {hasWarningFlags && (
            <Badge variant="destructive" className="gap-1">
              <AlertTriangle className="h-3 w-3" />
              {t('moderation.suspiciousFlags')}
            </Badge>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="flex flex-col">
            <span className="text-xs text-muted-foreground">
              {t('moderation.totalVotes')}
            </span>
            <span className="text-lg font-semibold">{totalVotes}</span>
          </div>

          <div className="flex flex-col">
            <span className="text-xs text-muted-foreground flex items-center gap-1">
              <TrendingUp className="h-3 w-3 text-green-600" />
              Upvotes
            </span>
            <span className="text-lg font-semibold text-green-600">
              {upvotes}
            </span>
          </div>

          <div className="flex flex-col">
            <span className="text-xs text-muted-foreground flex items-center gap-1">
              <TrendingDown className="h-3 w-3 text-red-600" />
              Downvotes
            </span>
            <span className="text-lg font-semibold text-red-600">
              {downvotes}
            </span>
          </div>

          <div className="flex flex-col">
            <span className="text-xs text-muted-foreground">
              {t('moderation.netVotes')}
            </span>
            <span
              className={`text-lg font-semibold ${
                netVotes > 0
                  ? 'text-green-600'
                  : netVotes < 0
                    ? 'text-red-600'
                    : ''
              }`}
            >
              {netVotes > 0 ? '+' : ''}
              {netVotes}
            </span>
          </div>

          {publishedAt && (
            <div className="flex flex-col col-span-2 lg:col-span-4">
              <span className="text-xs text-muted-foreground">
                {t('moderation.voteVelocity')}
              </span>
              <span className="text-lg font-semibold">
                {voteVelocity.toFixed(2)} votes/hour
              </span>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
