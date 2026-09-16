import { getFormationBadgePath } from '@/shared/assets'
import { layerStyle } from '@/shared/cardLayout'
import { getDisplayFontForNumeric } from '@/lib/utils'
import {
  formationOrderStyle,
  formationSlotLayers,
  type FormationSlotState,
} from '../lib/cardLayout'

interface FormationBadgeProps {
  state: FormationSlotState
  /** The slot's place in the deployment order, as `tmp_participateOrder` draws it */
  order: number
}

/**
 * The formation slot's participation banner, drawn over an identity card.
 *
 * `[Image]ParticipateSlotUI` is the slot's last sibling, so it draws above the portrait, the
 * frame and the clicked frame — an `IdentityCard` overlay rather than one of its own layers.
 * Neither state carries label text: `PersonalityUILabel.GetLabelText` returns the empty
 * string for `Participated` and `Baton`, and the wording is ink in the banner sprite.
 */
export function FormationBadge({ state, order }: FormationBadgeProps) {
  const layers = formationSlotLayers(state)

  return (
    <div className="absolute inset-0 pointer-events-none">
      <img src={getFormationBadgePath(layers.sprite)} alt="" style={layerStyle(layers.banner)} />

      <div
        data-testid="formation-order"
        style={{
          ...formationOrderStyle(layers.order, layers.orderInk),
          fontFamily: getDisplayFontForNumeric(),
        }}
      >
        {order}
      </div>
    </div>
  )
}
