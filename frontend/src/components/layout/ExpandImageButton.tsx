import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { TransformWrapper, TransformComponent } from 'react-zoom-pan-pinch'

import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { getButtonClosePath, getButtonExpandImagePath } from '@/shared/assets'
import { LIGHTBOX } from '@/lib/constants'
import { OverlayButton } from './OverlayButton'

interface ExpandImageButtonProps {
  src: string
  alt: string
}

/**
 * Expand button plus the in-page lightbox it opens: the page dims, the image
 * renders centered at its natural width capped to the viewport, with
 * pinch/wheel/double-click zoom and drag panning.
 */
export function ExpandImageButton({ src, alt }: ExpandImageButtonProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)

  return (
    <>
      <OverlayButton
        onClick={() => {
          setOpen(true)
        }}
        iconSrc={getButtonExpandImagePath()}
        iconAlt={t('a11y.expandImage')}
      />
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent
          className="h-dvh w-screen max-w-none sm:max-w-none rounded-none border-none bg-black p-0 shadow-none data-[state=open]:animate-none"
          aria-describedby={undefined}
          showCloseButton={false}
        >
          <DialogTitle className="sr-only">{alt}</DialogTitle>
          <TransformWrapper
            centerOnInit
            minScale={1}
            maxScale={LIGHTBOX.MAX_ZOOM_SCALE}
            doubleClick={{ mode: 'toggle' }}
          >
            <TransformComponent wrapperClass="!w-full !h-full">
              <img
                src={src}
                alt={alt}
                className="h-auto"
                style={{
                  maxWidth: `${String(LIGHTBOX.MAX_WIDTH_VW)}vw`,
                  maxHeight: `${String(LIGHTBOX.MAX_HEIGHT_DVH)}dvh`,
                }}
              />
            </TransformComponent>
          </TransformWrapper>
          <div className="absolute top-4 right-4 z-10">
            <OverlayButton
              onClick={() => {
                setOpen(false)
              }}
              iconSrc={getButtonClosePath()}
              iconAlt={t('close')}
            />
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
