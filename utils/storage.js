import { STORAGE_KEY } from './constants.js'
import { normalizeClothV2 } from './models.js'
import { stripClothForStorage, deleteImage } from './imageStore.js'
import { clothImageRef } from './imageCache.js'
import { removeClothFromInspirations } from './inspirationStorage.js'

function normalizeItem(item) {
  return normalizeClothV2(item)
}

export function getClothes() {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY)
    if (!Array.isArray(raw)) return []
    return raw.map(normalizeItem).filter(Boolean)
  } catch {
    return []
  }
}

export function saveClothes(list) {
  const stripped = (Array.isArray(list) ? list : []).map((item) => {
    const n = stripClothForStorage(item)
    if (!n.imageRef && n.id) {
      n.imageRef = clothImageRef(n.id)
    }
    return n
  })
  uni.setStorageSync(STORAGE_KEY, stripped)
}

export function getClothById(id) {
  return getClothes().find((item) => item.id === id) || null
}

export function addCloth(item) {
  const normalized = normalizeItem(item)
  if (!normalized) return
  const list = getClothes()
  list.unshift(normalized)
  saveClothes(list)
}

export function updateCloth(item) {
  const normalized = normalizeItem(item)
  if (!normalized) return
  const list = getClothes().map((c) => (c.id === normalized.id ? normalized : c))
  saveClothes(list)
}

export function removeCloth(id) {
  deleteImage(clothImageRef(id))
  removeClothFromInspirations(id)
  const list = getClothes().filter((c) => c.id !== id)
  saveClothes(list)
}

export function batchRemoveClothes(ids) {
  const idSet = new Set(ids)
  for (const id of idSet) {
    deleteImage(clothImageRef(id))
  }
  const list = getClothes().filter((c) => !idSet.has(c.id))
  saveClothes(list)
}

export function setClothStatus(id, status) {
  const list = getClothes().map((c) => {
    if (c.id !== id) return c
    const next = { ...c, status }
    if (status === 'discarded') {
      next.discardedAt = Date.now()
    } else {
      next.discardedAt = null
      next.status = 'active'
    }
    return normalizeItem(next) || c
  })
  saveClothes(list)
}

export function batchSetClothStatus(ids, status) {
  const idSet = new Set(ids)
  const now = Date.now()
  const list = getClothes().map((c) => {
    if (!idSet.has(c.id)) return c
    const next = { ...c, status }
    if (status === 'discarded') {
      next.discardedAt = now
    } else {
      next.discardedAt = null
      next.status = 'active'
    }
    return normalizeItem(next) || c
  })
  saveClothes(list)
}

export function replaceAllClothes(list) {
  const normalized = (Array.isArray(list) ? list : [])
    .map(normalizeItem)
    .filter(Boolean)
  saveClothes(normalized)
  return normalized
}

/** 批量更新：updater 返回新对象或 null 表示跳过 */
export function batchUpdateClothes(ids, updater) {
  const idSet = new Set(ids)
  const list = getClothes().map((c) => {
    if (!idSet.has(c.id)) return c
    const next = updater({ ...c })
    if (!next) return c
    return normalizeItem(next) || c
  })
  saveClothes(list)
  return list.filter((c) => idSet.has(c.id))
}
