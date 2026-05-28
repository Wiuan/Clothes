import { clothImageRef } from './imageCache.js'

export const BUNDLE_VERSION = 3
export const BUNDLE_FORMAT_ZIP = 'zip'

/** imageRef → zip 内 images/ 下的文件名（仅 ASCII，避免解压乱码） */
export function refToZipImageName(ref) {
  const safe = String(ref || '').replace(/[^a-zA-Z0-9_-]/g, '_')
  return `${safe}.jpg`
}

export function zipImagePath(ref) {
  return `images/${refToZipImageName(ref)}`
}

export function collectImageRefs(clothes, inspirations) {
  const refs = new Set()
  for (const item of clothes || []) {
    if (item?.imageRef) refs.add(item.imageRef)
    else if (item?.id) refs.add(clothImageRef(item.id))
  }
  for (const item of inspirations || []) {
    if (item?.imageRef) refs.add(item.imageRef)
    else if (item?.id) refs.add(`insp_${item.id}`)
  }
  return [...refs]
}

export function buildManifestPayload({
  clothes,
  matches,
  inspirations,
  wearLogs,
  format = BUNDLE_FORMAT_ZIP
}) {
  return {
    version: BUNDLE_VERSION,
    format,
    exportedAt: Date.now(),
    clothes,
    matches,
    inspirations,
    wearLogs
  }
}

/** 批间让出主线程，减轻 App 端 UI 假死 */
export function yieldToUi(ms = 0) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export function calcPercent(done, total) {
  if (!total) return 100
  return Math.min(100, Math.max(0, Math.round((done / total) * 100)))
}
