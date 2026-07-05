import { useQuery } from '@tanstack/react-query'

interface ExampleData {
  message: string
  timestamp: number
}

// Fetch example data from API (intercepted by MSW in tests)
async function fetchExampleData(): Promise<ExampleData> {
  const response = await fetch('/api/example')

  if (!response.ok) {
    throw new Error('Failed to fetch example data')
  }

  return response.json()
}

export function useExampleQuery() {
  return useQuery({
    queryKey: ['example'],
    queryFn: fetchExampleData,
  })
}
