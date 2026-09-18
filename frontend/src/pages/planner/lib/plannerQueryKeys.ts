export const plannerQueryKeys = {
  all: ['planners'] as const,
  list: () => [...plannerQueryKeys.all, 'list'] as const,
  detail: (id: string) => [...plannerQueryKeys.all, 'detail', id] as const,
}
