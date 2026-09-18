import { Profiler, type ComponentType, type ProfilerOnRenderCallback, type ReactNode } from 'react'

const counts = new Map<string, number>()

export function recordRender(label: string): void {
  counts.set(label, (counts.get(label) ?? 0) + 1)
}

export function getRenderCount(label: string): number {
  return counts.get(label) ?? 0
}

export function resetRenderCounts(): void {
  counts.clear()
}

export function snapshotRenderCounts(): Record<string, number> {
  return Object.fromEntries(counts)
}

export function rendersSince(baseline: Record<string, number>): Record<string, number> {
  const labels = new Set([...counts.keys(), ...Object.keys(baseline)])
  const delta: Record<string, number> = {}
  for (const label of labels) {
    delta[label] = (counts.get(label) ?? 0) - (baseline[label] ?? 0)
  }
  return delta
}

/**
 * Wraps a component so every call of its body is tallied under `label`.
 *
 * Pair with `vi.mock` + `importOriginal` to measure a real component without
 * editing it. A parent that hands the wrapper an unchanged element bails out
 * before the wrapper runs, so the tally counts genuine re-render work.
 */
export function countRenders<P extends object>(
  label: string,
  Component: ComponentType<P>,
): ComponentType<P> {
  function RenderCounted(props: P) {
    recordRender(label)
    return <Component {...props} />
  }
  RenderCounted.displayName = `RenderCounted(${label})`
  return RenderCounted
}

interface RenderProbeProps {
  label: string
  children: ReactNode
}

export function RenderProbe({ label, children }: RenderProbeProps) {
  const onRender: ProfilerOnRenderCallback = () => {
    recordRender(label)
  }

  return (
    <Profiler id={label} onRender={onRender}>
      {children}
    </Profiler>
  )
}
