import { use, type ComponentType } from 'react'
import type { AsyncRouteComponent } from '@tanstack/react-router'

type Props = Record<string, unknown>

type LazyState =
  | { kind: 'idle' }
  | { kind: 'loading'; promise: Promise<void> }
  | { kind: 'loaded'; component: ComponentType<Props> }
  | { kind: 'failed'; error: unknown }

type LazyRouteComponent<T, TKey extends keyof T> = T[TKey] extends (props: infer TProps) => any
  ? AsyncRouteComponent<TProps>
  : never

export function lazyRoute<T extends Record<string, any>, TKey extends keyof T = 'default'>(
  importer: () => Promise<T>,
  exportName?: TKey,
): LazyRouteComponent<T, TKey> {
  const key = exportName ?? 'default'
  let state: LazyState = { kind: 'idle' }

  function preload(): Promise<void> {
    if (state.kind === 'loading') return state.promise
    if (state.kind === 'loaded') return Promise.resolve()
    const promise = importer().then(
      (module) => {
        state = { kind: 'loaded', component: module[key] as ComponentType<Props> }
      },
      (error: unknown) => {
        state = { kind: 'failed', error }
      },
    )
    state = { kind: 'loading', promise }
    return promise
  }

  function LazyRoute(props: Props) {
    switch (state.kind) {
      case 'loaded': {
        const Component = state.component
        return <Component {...props} />
      }
      case 'failed':
        throw state.error
      default:
        use(preload())
        return null
    }
  }

  return Object.assign(LazyRoute, { preload }) as unknown as LazyRouteComponent<T, TKey>
}
