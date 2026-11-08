import i18n from '@/lib/i18n'

export async function loadIdentityName({ params }: { params: { id: string } }) {
  const module = await import(`@static/i18n/${i18n.language}/identity/${params.id}.json`)
  const name = (module.default as { name?: string }).name?.replace(/\n/g, ' ') ?? params.id
  return { name }
}

export async function loadEgoName({ params }: { params: { id: string } }) {
  const module = await import(`@static/i18n/${i18n.language}/ego/${params.id}.json`)
  const name = (module.default as { name?: string }).name?.replace(/\n/g, ' ') ?? params.id
  return { name }
}

export async function loadEgoGiftName({ params }: { params: { id: string } }) {
  const module = await import(`@static/i18n/${i18n.language}/egoGift/${params.id}.json`)
  const name = (module.default as { name?: string }).name ?? params.id
  return { name }
}

export async function loadAbEventTitle({ params }: { params: { id: string } }) {
  try {
    const module = await import(`@static/i18n/${i18n.language}/abEvent/${params.id}.json`)
    const data = module.default as { desc?: string }
    const raw = (data.desc ?? '').replace(/\n/g, ' ')
    const snippet = raw.length > 20 ? `${raw.slice(0, 20)}...` : raw
    return { title: snippet || params.id }
  } catch {
    return { title: params.id }
  }
}
