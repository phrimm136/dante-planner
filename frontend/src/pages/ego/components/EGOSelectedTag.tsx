import { getFormationBadgePath } from '@/shared/assets'
import { layerStyle } from '@/shared/cardLayout'
import { EGO_SELECTED_TAG } from '../lib/cardLayout'

/** The SELECTED plate a picked EGO draws over its portrait. */
export function EGOSelectedTag() {
  return <img src={getFormationBadgePath('selected')} alt="" style={layerStyle(EGO_SELECTED_TAG)} />
}
