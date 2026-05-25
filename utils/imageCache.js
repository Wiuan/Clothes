const memCache = new Map()
let loadTick = 0

export function getImageLoadTick() {
  return loadTick
}

export function bumpImageLoadTick() {
  loadTick++
}

export function cacheGet(ref) {
  if (!ref) return ''
  return memCache.get(ref) || ''
}

export function cacheSet(ref, dataUrl) {
  if (ref && dataUrl) memCache.set(ref, dataUrl)
}

export function cacheDelete(ref) {
  memCache.delete(ref)
}

export function clothImageRef(clothId) {
  return `img_${clothId}`
}
