/**
 * Planner Report Mutation Hook
 *
 * Handles reporting community planners.
 * One-time action - users can only report each planner once.
 * 409 Conflict returned if already reported.
 * Invalidates planner list cache on success.
 *
 * Pattern: usePlannerVote.ts (one-time action + ConflictError handling)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiClient, ConflictError } from '@/lib/api'
import { ReportResponseSchema } from '@/schemas/PlannerListSchemas'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'

import type { ReportResponse } from '@/types/PlannerListTypes'

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for reporting community planners
 *
 * Users can only report each planner once. Attempting to report again
 * will result in a 409 Conflict error.
 *
 * @example
 * ```tsx
 * function PlannerCard({ planner }) {
 *   const report = usePlannerReport();
 *
 *   const handleReport = () => {
 *     if (planner.hasReported) {
 *       console.error('Already reported this planner');
 *       return;
 *     }
 *     report.mutate(planner.id);
 *   };
 *
 *   return (
 *     <button
 *       onClick={handleReport}
 *       disabled={report.isPending || planner.hasReported}
 *     >
 *       Report
 *     </button>
 *   );
 * }
 * ```
 */
export function usePlannerReport() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (plannerId: string): Promise<ReportResponse> => {
      const data = await ApiClient.post(`/api/planner/md/${plannerId}/report`)
      const result = ReportResponseSchema.safeParse(data)

      if (!result.success) {
        console.error('Report response validation failed:', result.error)
        throw new Error('Invalid report response from server')
      }

      return result.data
    },
    onSuccess: () => {
      // Invalidate all planner list queries to refresh report state
      void queryClient.invalidateQueries({ queryKey: gesellschaftQueryKeys.all })
    },
    onError: (error) => {
      if (error instanceof ConflictError) {
        // 409 Conflict: User already reported this planner
        console.error('Report already exists - users can only report each planner once')
      } else {
        console.error('Report failed:', error)
      }
    },
  })
}
