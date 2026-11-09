import { useEffect, useRef } from 'react'

import { ApiClient } from '@/lib/api'

export function useRecordPlannerView(plannerId: string | null): void {
  const recordedIdRef = useRef<string | null>(null)

  useEffect(() => {
    if (plannerId === null || recordedIdRef.current === plannerId) return
    recordedIdRef.current = plannerId
    void ApiClient.post(`/api/planner/md/published/${plannerId}/viewcount`).catch(() => undefined)
  }, [plannerId])
}
