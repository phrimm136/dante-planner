import { getFormationBadgePath } from '@/shared/assets'
import { layerStyle } from '@/shared/cardLayout'
import { EGO_SELECTED_TAG } from '../lib/cardLayout'

export function EGOSelectedTag() {
  return <img src={getFormationBadgePath('selected')} alt="" style={layerStyle(EGO_SELECTED_TAG)} />
}
