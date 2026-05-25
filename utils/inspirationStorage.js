import { STORAGE_INSPIRATIONS } from './models.js'
import { SEASONS } from './constants.js'

export function normalizeInspiration(raw) {
  if (!raw || typeof raw !== 'object' || !raw.id) return null

  const item = { ...raw }
  item.name = item.name ? String(item.name).trim() : ''
  item.imageRef = item.imageRef || `insp_${item.id}`
  item.note = item.note ? String(item.note).trim() : ''
  item.occasion = item.occasion ? String(item.occasion).trim() : ''
  item.season = SEASONS.includes(item.season) ? item.season : ''
  item.style = item.style ? String(item.style).trim() : ''
  item.createdAt = typeof item.createdAt === 'number' ? item.createdAt : Date.now()

  const tags = raw.colorTags && typeof raw.colorTags === 'object' ? raw.colorTags : {}
  item.colorTags = {
    primary: Array.isArray(tags.primary) ? [...tags.primary] : [],
    secondary: Array.isArray(tags.secondary) ? [...tags.secondary] : [],
    accent: Array.isArray(tags.accent) ? [...tags.accent] : []
  }

  item.links = Array.isArray(raw.links)
    ? raw.links
        .filter((l) => l && l.clothId)
        .map((l) => ({
          clothId: l.clothId,
          relation: l.relation === 'want_to_buy' ? 'want_to_buy' : 'have_similar',
          note: l.note ? String(l.note).trim() : ''
        }))
    : []

  return item
}

export function getInspirations() {
  try {
    const raw = uni.getStorageSync(STORAGE_INSPIRATIONS)
    if (!Array.isArray(raw)) return []
    return raw.map(normalizeInspiration).filter(Boolean)
  } catch {
    return []
  }
}

export function saveInspirations(list) {
  uni.setStorageSync(STORAGE_INSPIRATIONS, Array.isArray(list) ? list : [])
}

export function getInspirationById(id) {
  return getInspirations().find((i) => i.id === id) || null
}

export function addInspiration(item) {
  const n = normalizeInspiration(item)
  if (!n) return
  const list = getInspirations()
  list.unshift(n)
  saveInspirations(list)
}

export function updateInspiration(item) {
  const n = normalizeInspiration(item)
  if (!n) return
  saveInspirations(getInspirations().map((i) => (i.id === n.id ? n : i)))
}

export function removeInspiration(id) {
  saveInspirations(getInspirations().filter((i) => i.id !== id))
}

export function replaceAllInspirations(list) {
  const normalized = (Array.isArray(list) ? list : [])
    .map(normalizeInspiration)
    .filter(Boolean)
  saveInspirations(normalized)
  return normalized
}

/** 删除衣物时移除灵感关联 */
export function removeClothFromInspirations(clothId) {
  const list = getInspirations()
    .map((i) => ({
      ...i,
      links: i.links.filter((l) => l.clothId !== clothId)
    }))
  saveInspirations(list)
}

export function filterInspirations(list, filters = {}) {
  const { style, season, primaryColor, wantToBuyOnly } = filters
  return (list || []).filter((item) => {
    if (style && style !== '全部' && item.style !== style) return false
    if (season && season !== '全部' && item.season !== season) return false
    if (primaryColor && primaryColor !== '全部') {
      const prim = item.colorTags?.primary || []
      if (!prim.includes(primaryColor)) return false
    }
    if (wantToBuyOnly && !item.links?.some((l) => l.relation === 'want_to_buy')) {
      return false
    }
    return true
  })
}

export function getAllPrimaryColors(list) {
  const set = new Set()
  for (const item of list || []) {
    for (const c of item.colorTags?.primary || []) {
      if (c) set.add(c)
    }
  }
  return [...set]
}
