import { useQuery } from '@tanstack/react-query'
import { z } from 'zod'

import { VERSION_CHECK_INTERVAL_MS, VERSION_FILE_PATH } from '@/lib/constants'
import { validateData } from '@/lib/validation'

const VersionFileSchema = z.object({ buildId: z.string().min(1) }).strict()

const versionQueryKeys = {
  buildId: () => ['buildId'] as const,
}

async function fetchBuildId(): Promise<string> {
  const response = await fetch(VERSION_FILE_PATH, { cache: 'no-store' })
  if (!response.ok) throw new Error(`${VERSION_FILE_PATH} answered ${response.status}`)
  return validateData(await response.json(), VersionFileSchema, VERSION_FILE_PATH).buildId
}

export function useUpdateAvailable(): string | null {
  const { data } = useQuery({
    queryKey: versionQueryKeys.buildId(),
    queryFn: fetchBuildId,
    enabled: !import.meta.env.DEV,
    refetchInterval: VERSION_CHECK_INTERVAL_MS,
    refetchOnWindowFocus: true,
    retry: false,
  })
  return data !== undefined && data !== __BUILD_ID__ ? data : null
}
