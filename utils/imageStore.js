/**
 * V3 图片存储：元数据只存 imageRef，单张压缩图（800px / ~400KB）
 * H5 → IndexedDB；App → _doc/cloth_images/
 */
import { cacheGet, cacheSet, cacheDelete, bumpImageLoadTick, clothImageRef } from './imageCache.js'

const DB_NAME = 'cloth_images_v3'
const STORE_NAME = 'images'
const IMG_DIR = '_doc/cloth_images/'

let dbPromise = null

export { getImageLoadTick } from './imageCache.js'
export { clothImageRef } from './imageCache.js'

function sanitizeRef(ref) {
  return String(ref).replace(/[^a-zA-Z0-9_-]/g, '_')
}

function isH5() {
  // #ifdef H5
  return typeof indexedDB !== 'undefined'
  // #endif
  // #ifndef H5
  return false
  // #endif
}

function getFs() {
  return uni.getFileSystemManager && uni.getFileSystemManager()
}

function openDb() {
  if (!isH5()) return Promise.reject(new Error('no idb'))
  if (dbPromise) return dbPromise
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1)
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains(STORE_NAME)) {
        req.result.createObjectStore(STORE_NAME)
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
  return dbPromise
}

async function idbGet(ref) {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readonly')
    const req = tx.objectStore(STORE_NAME).get(ref)
    req.onsuccess = () => resolve(req.result || '')
    req.onerror = () => reject(req.error)
  })
}

async function idbSet(ref, dataUrl) {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    tx.objectStore(STORE_NAME).put(dataUrl, ref)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

function filePathForRef(ref) {
  return `${IMG_DIR}${sanitizeRef(ref)}.jpg`
}

function ensureImageDir(fs) {
  return new Promise((resolve) => {
    fs.access({
      path: IMG_DIR,
      success: () => resolve(),
      fail: () => {
        fs.mkdir({
          dirPath: IMG_DIR,
          recursive: true,
          complete: () => resolve(),
          fail: () => resolve()
        })
      }
    })
  })
}

async function fileSet(ref, dataUrl) {
  const fs = getFs()
  if (!fs) throw new Error('no fs')
  await ensureImageDir(fs)
  const filePath = filePathForRef(ref)
  const base64 = String(dataUrl).includes(',') ? dataUrl.split(',')[1] : dataUrl
  await new Promise((resolve, reject) => {
    fs.writeFile({
      filePath,
      data: base64,
      encoding: 'base64',
      success: resolve,
      fail: reject
    })
  })
}

async function fileGet(ref) {
  const fs = getFs()
  if (!fs) return ''
  return new Promise((resolve) => {
    fs.readFile({
      filePath: filePathForRef(ref),
      encoding: 'base64',
      success: (res) => resolve(`data:image/jpeg;base64,${res.data}`),
      fail: () => resolve('')
    })
  })
}

async function persistSet(ref, dataUrl) {
  if (isH5()) await idbSet(ref, dataUrl)
  else if (getFs()) await fileSet(ref, dataUrl)
  else uni.setStorageSync(`wardrobe_img_${ref}`, dataUrl)
}

async function persistGet(ref) {
  if (isH5()) {
    try {
      return await idbGet(ref)
    } catch {
      return ''
    }
  }
  if (getFs()) return await fileGet(ref)
  try {
    return uni.getStorageSync(`wardrobe_img_${ref}`) || ''
  } catch {
    return ''
  }
}

/** 写入压缩图（与录入时同一规格） */
export async function saveImage(ref, dataUrl) {
  if (!ref || !dataUrl) return
  const { ensureBase64UnderLimit } = await import('./image.js')
  const limited = await ensureBase64UnderLimit(dataUrl)
  cacheSet(ref, limited)
  await persistSet(ref, limited)
}

export async function getImage(ref) {
  if (!ref) return ''
  const cached = cacheGet(ref)
  if (cached) return cached
  const data = await persistGet(ref)
  if (data) cacheSet(ref, data)
  return data || ''
}

export async function deleteImage(ref) {
  cacheDelete(ref)
  if (isH5()) {
    try {
      const db = await openDb()
      await new Promise((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, 'readwrite')
        tx.objectStore(STORE_NAME).delete(ref)
        tx.oncomplete = () => resolve()
        tx.onerror = () => reject(tx.error)
      })
    } catch {
      /* ignore */
    }
    return
  }
  const fs = getFs()
  if (fs) fs.unlink({ filePath: filePathForRef(ref) })
  else uni.removeStorageSync(`wardrobe_img_${ref}`)
}

/** 保存衣物照片，返回 imageRef */
export async function saveClothImage(clothId, dataUrl) {
  const imageRef = clothImageRef(clothId)
  await saveImage(imageRef, dataUrl)
  return { imageRef }
}

export function inspirationImageRef(id) {
  return `insp_${id}`
}

/** 保存灵感图，返回 imageRef */
export async function saveInspirationImage(inspId, dataUrl) {
  const imageRef = inspirationImageRef(inspId)
  await saveImage(imageRef, dataUrl)
  return { imageRef }
}

export async function hydrateInspirationsImages(list) {
  const refs = new Set()
  for (const item of list || []) {
    if (item.imageRef) refs.add(item.imageRef)
  }
  await Promise.all([...refs].map((r) => getImage(r).catch(() => '')))
  bumpImageLoadTick()
}

export function stripClothForStorage(item) {
  const c = { ...item }
  delete c.imageBase64
  delete c.imagePath
  delete c.thumbRef
  return c
}

export async function hydrateClothesImages(list) {
  const refs = new Set()
  for (const item of list || []) {
    if (item.imageRef) refs.add(item.imageRef)
  }
  await Promise.all([...refs].map((r) => getImage(r).catch(() => '')))
  bumpImageLoadTick()
}
