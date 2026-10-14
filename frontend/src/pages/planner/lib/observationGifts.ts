export function maxObservableGifts(costs: readonly { egogiftCount: number }[]): number {
  return Math.max(0, ...costs.map((cost) => cost.egogiftCount))
}
