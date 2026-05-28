import { cacheGet, clothImageRef } from './imageCache.js'

/**
 * 单张图片压缩规格（列表/详情/导出共用）
 * 宽 800px、质量约 82%、单张约 400KB 内
 */
export const MAX_IMAGE_BYTES = 400 * 1024
export const COMPRESS_MAX_WIDTH = 800
export const COMPRESS_QUALITY = 0.82

function mimeFromPath(filePath) {
  const ext = (filePath.split('.').pop() || 'jpg').toLowerCase().split('?')[0]
  if (ext === 'png') return 'image/png'
  if (ext === 'webp') return 'image/webp'
  return 'image/jpeg'
}

export function base64ByteSize(dataUrl) {
  if (!dataUrl) return 0
  const base64 = String(dataUrl).includes(',') ? dataUrl.split(',')[1] : dataUrl
  return Math.floor(base64.length * 3 / 4)
}

/** App 端路径规范化（file:// → 本地路径） */
function normalizeLocalPath(filePath) {
  if (!filePath) return filePath
  let p = String(filePath)
  // #ifdef APP-PLUS
  if (p.startsWith('file://') && typeof plus !== 'undefined' && plus.io) {
    try {
      p = plus.io.convertLocalFileSystemURL(p)
    } catch {
      /* 保持原路径 */
    }
  }
  // #endif
  return p
}

// #ifdef APP-PLUS
function pathToDataUrlViaPlus(filePath, mime) {
  return new Promise((resolve, reject) => {
    if (typeof plus === 'undefined' || !plus.io) {
      reject(new Error('plus.io 不可用'))
      return
    }
    const p = String(filePath || '')
    if (p.startsWith('content://')) {
      reject(new Error('无法读取该图片路径'))
      return
    }
    plus.io.resolveLocalFileSystemURL(
      filePath,
      (entry) => {
        entry.file(
          (file) => {
            const reader = new plus.io.FileReader()
            reader.onloadend = (evt) => {
              const result = evt.target && evt.target.result
              if (!result) {
                reject(new Error('读取为空'))
                return
              }
              if (String(result).startsWith('data:')) {
                resolve(result)
              } else {
                resolve(`data:${mime};base64,${result}`)
              }
            }
            reader.onerror = () => reject(new Error('FileReader 失败'))
            reader.readAsDataURL(file)
          },
          (e) => reject(e || new Error('entry.file 失败'))
        )
      },
      (e) => reject(e || new Error('resolveLocalFileSystemURL 失败'))
    )
  })
}
// #endif

function pathToDataUrl(filePath, mime = 'image/jpeg') {
  return new Promise((resolve, reject) => {
    if (!filePath) {
      reject(new Error('无效路径'))
      return
    }
    if (filePath.startsWith('data:')) {
      resolve(filePath)
      return
    }

    const path = normalizeLocalPath(filePath)
    const fs = uni.getFileSystemManager && uni.getFileSystemManager()

    const tryPlus = () => {
      // #ifdef APP-PLUS
      return pathToDataUrlViaPlus(path, mime)
      // #endif
      // #ifndef APP-PLUS
      return Promise.reject(new Error('无法读取图片'))
      // #endif
    }

    if (!fs) {
      tryPlus().then(resolve).catch(reject)
      return
    }

    fs.readFile({
      filePath: path,
      encoding: 'base64',
      success: (res) => resolve(`data:${mime};base64,${res.data}`),
      fail: () => {
        tryPlus().then(resolve).catch(reject)
      }
    })
  })
}

function compressImageUni(src, qualityPercent) {
  return new Promise((resolve) => {
    const q = qualityPercent != null ? qualityPercent : Math.round(COMPRESS_QUALITY * 100)
    uni.compressImage({
      src,
      quality: q,
      success: (res) => resolve(res.tempFilePath || src),
      fail: () => resolve(src)
    })
  })
}

/** H5：canvas 压缩为 jpeg base64 */
function compressImageH5(src) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => {
      const maxW = COMPRESS_MAX_WIDTH
      let w = img.width
      let h = img.height
      if (w > maxW) {
        h = Math.round((h * maxW) / w)
        w = maxW
      }
      const canvas = document.createElement('canvas')
      canvas.width = w
      canvas.height = h
      const ctx = canvas.getContext('2d')
      ctx.drawImage(img, 0, 0, w, h)

      let quality = COMPRESS_QUALITY
      let dataUrl = canvas.toDataURL('image/jpeg', quality)
      while (base64ByteSize(dataUrl) > MAX_IMAGE_BYTES && quality > 0.5) {
        quality -= 0.06
        dataUrl = canvas.toDataURL('image/jpeg', quality)
      }
      if (base64ByteSize(dataUrl) > MAX_IMAGE_BYTES) {
        reject(new Error('图片过大，请换一张'))
        return
      }
      resolve(dataUrl)
    }
    img.onerror = () => reject(new Error('图片加载失败'))
    img.src = src
  })
}

/** 将 dataUrl 压到上限以内（优先缩小尺寸再降质量） */
export function ensureBase64UnderLimit(dataUrl) {
  if (!dataUrl || !dataUrl.startsWith('data:image')) {
    return Promise.resolve(dataUrl)
  }
  if (base64ByteSize(dataUrl) <= MAX_IMAGE_BYTES) {
    return Promise.resolve(dataUrl)
  }

  // #ifdef H5
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      let maxW = COMPRESS_MAX_WIDTH
      let quality = COMPRESS_QUALITY
      const draw = () => {
        let w = img.width
        let h = img.height
        if (w > maxW) {
          h = Math.round((h * maxW) / w)
          w = maxW
        }
        const canvas = document.createElement('canvas')
        canvas.width = w
        canvas.height = h
        canvas.getContext('2d').drawImage(img, 0, 0, w, h)
        return canvas.toDataURL('image/jpeg', quality)
      }
      let out = draw()
      while (base64ByteSize(out) > MAX_IMAGE_BYTES && quality > 0.5) {
        quality -= 0.06
        out = draw()
      }
      while (base64ByteSize(out) > MAX_IMAGE_BYTES && maxW > 480) {
        maxW -= 80
        out = draw()
      }
      if (base64ByteSize(out) > MAX_IMAGE_BYTES) {
        reject(new Error('图片过大'))
      } else {
        resolve(out)
      }
    }
    img.onerror = reject
    img.src = dataUrl
  })
  // #endif

  // #ifndef H5
  if (base64ByteSize(dataUrl) > MAX_IMAGE_BYTES * 1.5) {
    return Promise.reject(new Error('图片过大，请换一张较小的照片'))
  }
  return Promise.resolve(dataUrl)
  // #endif
}

/**
 * 选择/拍照后：压缩并转为 base64
 */
export async function pickImageAsBase64(tempFilePath) {
  // #ifdef H5
  const dataUrl = await compressImageH5(tempFilePath)
  return ensureBase64UnderLimit(dataUrl)
  // #endif

  // #ifndef H5
  const mime = mimeFromPath(tempFilePath)
  let path = normalizeLocalPath(tempFilePath)
  const qualities = [82, 70, 58, 45, 35]
  let lastError = null

  for (const q of qualities) {
    try {
      const compressed = await compressImageUni(path, q)
      const dataUrl = await pathToDataUrl(compressed, mime)
      if (base64ByteSize(dataUrl) <= MAX_IMAGE_BYTES) {
        return ensureBase64UnderLimit(dataUrl)
      }
      path = compressed
    } catch (e) {
      lastError = e
    }
  }

  try {
    const dataUrl = await pathToDataUrl(path, mime)
    return ensureBase64UnderLimit(dataUrl)
  } catch (e) {
    console.error('pickImageAsBase64', e, lastError, tempFilePath)
    throw lastError || e || new Error('图片读取失败')
  }
  // #endif
}

export function getInspirationImageSrc(item) {
  if (!item) return ''
  const ref = item.imageRef || `insp_${item.id}`
  return cacheGet(ref) || ''
}

/** 从图片库缓存读取（列表/详情同一张压缩图） */
export function getClothImageSrc(item) {
  if (!item) return ''
  const ref = item.imageRef || clothImageRef(item.id)
  return cacheGet(ref) || ''
}
