import type { projectEditorState } from '../../lib/editorStateCodec'
import type { PLANNER_CONTENT_KEYS } from './plannerContentKeys'
import type { PlannerContentState, PlannerSessionState } from '../plannerState'

type Equal<X, Y> =
  (<T>() => T extends X ? 1 : 2) extends <T>() => T extends Y ? 1 : 2 ? true : false

type Expect<T extends true> = T

type ProjectionInput = Parameters<typeof projectEditorState>[0]

export type PlannerStateGuard = [
  Expect<Equal<keyof PlannerContentState & keyof PlannerSessionState, never>>,
  Expect<Equal<(typeof PLANNER_CONTENT_KEYS)[number], keyof PlannerContentState>>,
  Expect<Equal<ProjectionInput, PlannerContentState>>,
  Expect<Equal<PlannerSessionState extends ProjectionInput ? true : false, false>>,
]
