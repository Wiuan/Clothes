import { STORAGE_MATCHES } from './models.js'
import { normalizeMatchV2 } from './models.js'

export function getMatches() {
  try {
    const raw = uni.getStorageSync(STORAGE_MATCHES)
    if (!Array.isArray(raw)) return []
    return raw.map(normalizeMatchV2).filter(Boolean)
  } catch {
    return []
  }
}

export function saveMatches(list) {
  uni.setStorageSync(STORAGE_MATCHES, list)
}

export function getMatchById(id) {
  return getMatches().find((m) => m.id === id) || null
}

export function addMatch(item) {
  const normalized = normalizeMatchV2(item)
  if (!normalized) return
  const list = getMatches()
  list.unshift(normalized)
  saveMatches(list)
}

export function updateMatch(item) {
  const normalized = normalizeMatchV2(item)
  if (!normalized) return
  const list = getMatches().map((m) => (m.id === normalized.id ? normalized : m))
  saveMatches(list)
}

export function removeMatch(id) {
  saveMatches(getMatches().filter((m) => m.id !== id))
}

export function replaceAllMatches(list) {
  const normalized = (Array.isArray(list) ? list : [])
    .map(normalizeMatchV2)
    .filter(Boolean)
  saveMatches(normalized)
  return normalized
}

/** 删除衣物时，从搭配中移除该 id */
export function removeClothFromMatches(clothId) {
  const list = getMatches()
    .map((m) => ({
      ...m,
      clothIds: m.clothIds.filter((id) => id !== clothId)
    }))
    .filter((m) => m.clothIds.length > 0)
  saveMatches(list)
}
