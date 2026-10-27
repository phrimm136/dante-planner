import { useState } from 'react'

interface TrackerDoneMarks {
  packs: Record<number, Set<string>>
  gifts: Set<string>
}

function createInitialDoneMarks(): TrackerDoneMarks {
  return { packs: {}, gifts: new Set() }
}

export function useTrackerDoneMarks() {
  const [doneMarks, setDoneMarks] = useState<TrackerDoneMarks>(createInitialDoneMarks)

  const toggleEgoGiftDoneMark = (encodedId: string) => {
    setDoneMarks((prev) => {
      const gifts = new Set(prev.gifts)

      if (gifts.has(encodedId)) {
        gifts.delete(encodedId)
      } else {
        gifts.add(encodedId)
      }

      return { ...prev, gifts }
    })
  }

  const togglePackDone = (floorIndex: number, themePackId: string, giftIds: string[]) => {
    setDoneMarks((prev) => {
      const floorMarks = new Set(prev.packs[floorIndex] || [])
      const wasDone = floorMarks.has(themePackId)

      if (wasDone) {
        floorMarks.delete(themePackId)
      } else {
        floorMarks.add(themePackId)
      }

      const gifts = new Set(prev.gifts)
      for (const id of giftIds) {
        if (wasDone) {
          gifts.delete(id)
        } else {
          gifts.add(id)
        }
      }

      return { packs: { ...prev.packs, [floorIndex]: floorMarks }, gifts }
    })
  }

  return {
    doneMarks: doneMarks.packs,
    egoGiftDoneMarks: doneMarks.gifts,
    toggleEgoGiftDoneMark,
    togglePackDone,
  }
}
