import { getClothes, replaceAllClothes } from './storage.js'
import { getMatches, replaceAllMatches } from './matchStorage.js'
import {
  getInspirations,
  replaceAllInspirations,
  normalizeInspiration
} from './inspirationStorage.js'
import { replaceAllWearLogs, wearLogsForExport } from './checkinStorage.js'
import { ensureBase64UnderLimit, base64ByteSize, MAX_IMAGE_BYTES } from './image.js'
import { normalizeClothV2, normalizeMatchV2 } from './models.js'
import { getImage, saveImage, clothImageRef } from './imageStore.js'
import {
  writeJsonToDoc,
  finishExportForApp,
  pickJsonFileApp,
  readAppLocalBackup,
  readBigFileUtf8
} from './appIo.js'

const EXPORT_NAME = 'wardrobe_export.json'
const BUNDLE_VERSION = 3

function getExportPath() {
  // #ifdef MP-WEIXIN
  return `${wx.env.USER_DATA_PATH}/${EXPORT_NAME}`
  // #endif
  // #ifndef MP-WEIXIN
  return `_doc/${EXPORT_NAME}`
  // #endif
}

function downloadOnH5(content, filename) {
  const blob = new Blob([content], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

/** 清理读入的文本（BOM、误选路径等） */
function sanitizeImportText(text) {
  let s = String(text || '').trim()
  if (s.charCodeAt(0) === 0xfeff) {
    s = s.slice(1).trim()
  }
  if (!s.startsWith('{') && !s.startsWith('[')) {
    if (s.includes('/storage/') || s.includes('_doc/') || s.includes('wardrobe_export')) {
      throw new Error('不是 JSON 文件，请选 wardrobe_export.json')
    }
  }
  return s
}

function diagnoseImportFormat(data) {
  if (data == null) return '内容为空'
  if (typeof data === 'string') return '不是 JSON 对象'
  if (Array.isArray(data)) return ''
  if (typeof data !== 'object') return '根节点类型错误'
  const ver = Number(data.version)
  if (ver !== BUNDLE_VERSION && !Array.isArray(data.clothes)) {
    return `版本=${data.version}，需要 3`
  }
  if (!Array.isArray(data.clothes)) {
    return '缺少 clothes 数组，请确认是完整导出包'
  }
  return '格式不符'
}

/** 解析 version 3 导出包（兼容 version 字符串、根数组） */
function parseImportBundle(data) {
  if (data == null) return null

  if (Array.isArray(data)) {
    return {
      clothes: data,
      matches: [],
      inspirations: [],
      wearLogs: [],
      images: {}
    }
  }

  if (typeof data !== 'object') return null

  const clothes = Array.isArray(data.clothes) ? data.clothes : null
  const version = Number(data.version)
  const looksV3 =
    version === BUNDLE_VERSION ||
    (clothes && (data.images == null || typeof data.images === 'object'))

  if (!looksV3 || !clothes) {
    return null
  }

  return {
    clothes,
    matches: Array.isArray(data.matches) ? data.matches : [],
    inspirations: Array.isArray(data.inspirations) ? data.inspirations : [],
    wearLogs: Array.isArray(data.wearLogs) ? data.wearLogs : [],
    images: data.images && typeof data.images === 'object' ? data.images : {}
  }
}

function normalizeImportClothes(rawList) {
  return rawList
    .map((item, i) => {
      if (!item.id) item.id = `import_${Date.now()}_${i}`
      return normalizeClothV2(item)
    })
    .filter(Boolean)
}

function normalizeImportMatches(rawList) {
  return rawList
    .map((item, i) => {
      if (!item.id) item.id = `match_import_${Date.now()}_${i}`
      return normalizeMatchV2(item)
    })
    .filter(Boolean)
}

function normalizeImportInspirations(rawList) {
  return rawList
    .map((item, i) => {
      if (!item.id) item.id = `insp_import_${Date.now()}_${i}`
      return normalizeInspiration(item)
    })
    .filter(Boolean)
}

function inspirationMetaForExport(item) {
  return {
    id: item.id,
    name: item.name,
    imageRef: item.imageRef,
    colorTags: item.colorTags
      ? {
          primary: [...(item.colorTags.primary || [])],
          secondary: [...(item.colorTags.secondary || [])],
          accent: [...(item.colorTags.accent || [])]
        }
      : { primary: [], secondary: [], accent: [] },
    style: item.style,
    occasion: item.occasion,
    season: item.season,
    note: item.note,
    links: (item.links || []).map((l) => ({ ...l })),
    createdAt: item.createdAt,
    source: item.source
  }
}

function clothMetaForExport(item) {
  return {
    id: item.id,
    name: item.name,
    colors: item.colors ? [...item.colors] : [item.color],
    colorHexMap: item.colorHexMap ? { ...item.colorHexMap } : undefined,
    color: item.color,
    colorPreset: item.colorPreset,
    colorHex: item.colorHex,
    season: item.season,
    type: item.type,
    tempMin: item.tempMin,
    tempMax: item.tempMax,
    status: item.status,
    discardedAt: item.discardedAt,
    purchaseDate: item.purchaseDate,
    purchasePrice: item.purchasePrice,
    material: item.material,
    note: item.note,
    sizes: { ...item.sizes },
    createdAt: item.createdAt,
    imageRef: item.imageRef || clothImageRef(item.id)
  }
}

async function applyImportImagesForRefs(items, getRef, imagesDict) {
  for (const item of items) {
    const ref = getRef(item)
    item.imageRef = ref
    let dataUrl = imagesDict[ref] || imagesDict[item.id] || ''
    if (dataUrl && !String(dataUrl).startsWith('data:image')) {
      dataUrl = `data:image/jpeg;base64,${dataUrl}`
    }
    if (dataUrl) {
      await saveImage(ref, dataUrl)
    }
  }
  return items
}

async function applyImportImages(clothes, imagesDict) {
  return applyImportImagesForRefs(
    clothes,
    (c) => c.imageRef || clothImageRef(c.id),
    imagesDict
  )
}

async function applyImportInspirationImages(inspirations, imagesDict) {
  return applyImportImagesForRefs(
    inspirations,
    (i) => i.imageRef || `insp_${i.id}`,
    imagesDict
  )
}

function decodeUtf8(buffer) {
  if (typeof buffer === 'string') return buffer
  const bytes = new Uint8Array(buffer)
  let s = ''
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i])
  try {
    return decodeURIComponent(escape(s))
  } catch {
    return s
  }
}

function normalizeJsonFilePath(filePath) {
  if (!filePath) return filePath
  let p = String(filePath)
  // #ifdef APP-PLUS
  if (p.startsWith('file://') && typeof plus !== 'undefined' && plus.io) {
    try {
      p = plus.io.convertLocalFileSystemURL(p)
    } catch {
      /* keep */
    }
  }
  // #endif
  return p
}

async function readJsonFile(filePath, h5File) {
  // #ifdef H5
  if (h5File) {
    if (typeof h5File.text === 'function') {
      return await h5File.text()
    }
    if (h5File instanceof Blob) {
      return await new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(reader.result)
        reader.onerror = reject
        reader.readAsText(h5File, 'UTF-8')
      })
    }
  }
  if (filePath && (filePath.startsWith('blob:') || filePath.startsWith('http'))) {
    const res = await fetch(filePath)
    return await res.text()
  }
  // #endif

  const localPath = normalizeJsonFilePath(filePath)

  // #ifdef APP-PLUS
  if (localPath) {
    try {
      return await readBigFileUtf8(localPath)
    } catch (e) {
      console.warn('[Cloth导入] readBigFileUtf8 失败，尝试 fs', e)
    }
  }
  // #endif

  const fs = uni.getFileSystemManager && uni.getFileSystemManager()
  if (!fs) throw new Error('当前环境无法读取文件')

  return new Promise((resolve, reject) => {
    fs.readFile({
      filePath: localPath,
      encoding: 'utf8',
      success: (res) => resolve(typeof res.data === 'string' ? res.data : ''),
      fail: () => {
        fs.readFile({
          filePath: localPath,
          success: (res2) => {
            try {
              const buf = res2.data
              resolve(typeof buf === 'string' ? buf : decodeUtf8(buf))
            } catch (e) {
              reject(e)
            }
          },
          fail: reject
        })
      }
    })
  })
}

function chooseJsonFileH5() {
  return new Promise((resolve, reject) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = '.json,application/json'
    input.style.display = 'none'
    document.body.appendChild(input)
    const cleanup = () => input.remove()
    input.onchange = () => {
      const file = input.files && input.files[0]
      cleanup()
      if (!file) {
        reject(new Error('cancel'))
        return
      }
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result)
      reader.onerror = () => reject(new Error('read fail'))
      reader.readAsText(file, 'UTF-8')
    }
    input.oncancel = () => {
      cleanup()
      reject(new Error('cancel'))
    }
    input.click()
  })
}

export async function exportJson() {
  const list = getClothes()
  const matches = getMatches()
  const inspirations = getInspirations()
  if (!list.length && !matches.length && !inspirations.length) {
    uni.showToast({ title: '暂无数据可导出', icon: 'none' })
    throw new Error('empty')
  }

  uni.showLoading({ title: '打包中...', mask: true })
  try {
    const images = {}
    const clothes = []

    for (const item of list) {
      const ref = item.imageRef || clothImageRef(item.id)
      const entry = clothMetaForExport(item)
      try {
        const fromStore = await getImage(ref)
        if (fromStore) {
          let b64 = fromStore
          try {
            b64 = await ensureBase64UnderLimit(fromStore)
          } catch {
            /* 过大则原样导出 */
          }
          if (base64ByteSize(b64) > MAX_IMAGE_BYTES) {
            console.warn('export image large:', item.name, base64ByteSize(b64))
          }
          images[ref] = b64
        }
      } catch (e) {
        console.warn('export image skip:', item.name, e)
      }
      clothes.push(entry)
    }

    const inspirationExport = []
    for (const item of inspirations) {
      const ref = item.imageRef || `insp_${item.id}`
      const entry = inspirationMetaForExport(item)
      try {
        const fromStore = await getImage(ref)
        if (fromStore) {
          let b64 = fromStore
          try {
            b64 = await ensureBase64UnderLimit(fromStore)
          } catch {
            /* 原样 */
          }
          images[ref] = b64
        }
      } catch (e) {
        console.warn('export insp image skip:', item.id, e)
      }
      inspirationExport.push(entry)
    }

    const bundle = {
      version: BUNDLE_VERSION,
      exportedAt: Date.now(),
      clothes,
      matches: matches.map((m) => ({
        id: m.id,
        name: m.name,
        clothIds: [...m.clothIds],
        note: m.note,
        createdAt: m.createdAt
      })),
      inspirations: inspirationExport,
      wearLogs: wearLogsForExport(),
      images
    }
    const json = JSON.stringify(bundle)
    if (!json || json.length < 10) {
      throw new Error('打包结果为空')
    }

    // #ifdef H5
    downloadOnH5(json, EXPORT_NAME)
    return {
      count: clothes.length,
      matchCount: matches.length,
      inspirationCount: inspirations.length,
      filePath: EXPORT_NAME
    }
    // #endif

    // #ifdef APP-PLUS
    // 导出已验收稳定，勿改（见 .cursor/rules/import-export.mdc）
    let writeResult
    try {
      writeResult = await writeJsonToDoc(EXPORT_NAME, json)
    } catch (writeErr) {
      console.error('writeJsonToDoc', writeErr)
      const msg = writeErr && writeErr.message ? String(writeErr.message) : ''
      throw new Error(msg.includes('空') ? msg : '写入文件失败，请重试')
    }

    const absPath =
      typeof writeResult === 'string' ? writeResult : writeResult && writeResult.absPath
    if (!absPath) {
      throw new Error('写入文件失败，未获得路径')
    }
    const fileSize =
      typeof writeResult === 'object' && writeResult && writeResult.size
        ? writeResult.size
        : json.length
    const fileSizeKb = Math.max(1, Math.round(fileSize / 1024))
    const exportPath = finishExportForApp(absPath)
    try {
      uni.setStorageSync('wardrobe_last_export_abs', exportPath)
    } catch {
      /* ignore */
    }

    uni.showModal({
      title: '导出成功',
      content: `文件位置：\n${exportPath}\n\n大小：约 ${fileSizeKb} KB`,
      showCancel: false
    })

    return {
      count: clothes.length,
      matchCount: matches.length,
      inspirationCount: inspirations.length,
      filePath: exportPath,
      fileSize
    }
    // #endif

    // #ifndef H5
    // #ifndef APP-PLUS
    const filePath = getExportPath()
    const fs = uni.getFileSystemManager()
    await new Promise((resolve, reject) => {
      fs.writeFile({ filePath, data: json, encoding: 'utf8', success: resolve, fail: reject })
    })
    await new Promise((resolve) => {
      uni.openDocument({
        filePath,
        showMenu: true,
        success: resolve,
        fail: () => {
          uni.showModal({
            title: '导出成功',
            content: `已保存 ${clothes.length} 件\n${filePath}`,
            showCancel: false
          })
          resolve()
        }
      })
    })
    return {
      count: clothes.length,
      matchCount: matches.length,
      inspirationCount: inspirations.length,
      filePath
    }
    // #endif
    // #endif
  } finally {
    uni.hideLoading()
  }
}

function setImportProgress(title) {
  try {
    uni.showLoading({ title: String(title || '导入中...'), mask: true })
  } catch {
    /* ignore */
  }
}

async function processImportText(text) {
  const raw = sanitizeImportText(text)
  const sizeKb = Math.max(1, Math.round(raw.length / 1024))
  setImportProgress(`解析 JSON（约 ${sizeKb} KB）...`)
  let parsed
  try {
    parsed = JSON.parse(raw)
  } catch (e) {
    console.error('[Cloth导入] JSON.parse 失败', e, '前120字=', raw.slice(0, 120))
    uni.showToast({ title: 'JSON 解析失败', icon: 'none' })
    throw new Error('parse error')
  }

  if (parsed === null) {
    console.error('[Cloth导入] 解析结果为 null，可能是大文件读取失败，前80字=', raw.slice(0, 80))
    uni.showToast({ title: '文件读取异常，请用「从备份文件夹导入」', icon: 'none', duration: 3000 })
    throw new Error('parse null')
  }

  const bundle = parseImportBundle(parsed)
  if (!bundle) {
    const reason = diagnoseImportFormat(parsed)
    console.warn('[Cloth导入] parseImportBundle fail', reason, parsed && Object.keys(parsed))
    uni.showToast({
      title: reason ? `导入失败：${reason}` : '请使用 version 3 导出包',
      icon: 'none',
      duration: 3000
    })
    throw new Error('invalid format')
  }

  let clothes = normalizeImportClothes(bundle.clothes)
  const matchList = normalizeImportMatches(bundle.matches)
  let inspirationList = normalizeImportInspirations(bundle.inspirations)

  if (!clothes.length && !matchList.length && !inspirationList.length) {
    uni.showToast({ title: '没有有效数据', icon: 'none' })
    throw new Error('no valid items')
  }

  if (clothes.length) {
    setImportProgress(`还原衣服图片 0/${clothes.length}`)
    clothes = await applyImportImages(clothes, bundle.images)
    replaceAllClothes(clothes)
  }
  replaceAllMatches(matchList)

  if (inspirationList.length) {
    setImportProgress(`还原灵感图片 0/${inspirationList.length}`)
    inspirationList = await applyImportInspirationImages(inspirationList, bundle.images)
    replaceAllInspirations(inspirationList)
  } else if (bundle.inspirations && bundle.inspirations.length === 0) {
    replaceAllInspirations([])
  }

  let wearLogCount = 0
  if (Array.isArray(bundle.wearLogs)) {
    setImportProgress('写入穿着记录...')
    wearLogCount = replaceAllWearLogs(bundle.wearLogs).length
  }

  setImportProgress('刷新列表...')
  const { hydrateClothesImages, hydrateInspirationsImages } = await import('./imageStore.js')
  await hydrateClothesImages(getClothes())
  await hydrateInspirationsImages(getInspirations())

  const result = {
    clothes: clothes.length ? clothes : getClothes(),
    matchCount: matchList.length,
    inspirationCount: inspirationList.length,
    wearLogCount
  }

  const parts = [
    `衣服 ${clothes.length || getClothes().length} 件`,
    `搭配 ${matchList.length} 组`,
    `灵感 ${inspirationList.length} 条`
  ]
  if (Array.isArray(bundle.wearLogs)) {
    parts.push(`穿着记录 ${wearLogCount} 条`)
  }
  uni.showModal({
    title: '导入成功',
    content: `已写入：${parts.join('，')}`,
    showCancel: false
  })

  return result
}

/** 直接粘贴 JSON 文本导入（App 粘贴页等） */
export async function importJsonFromText(text) {
  uni.showLoading({ title: '导入中...', mask: true })
  try {
    return await processImportText(text)
  } catch (e) {
    if (e && e.message !== 'cancel') {
      console.error('importJsonFromText', e)
      if (!['parse error', 'invalid format', 'no valid items'].includes(e.message)) {
        uni.showToast({ title: '导入失败', icon: 'none' })
      }
    }
    throw e
  } finally {
    uni.hideLoading()
  }
}

function pickJsonFileUni() {
  return new Promise((resolve, reject) => {
    const onSuccess = (res) => {
      const tf = res.tempFiles && res.tempFiles[0]
      const path =
        (res.tempFilePaths && res.tempFilePaths[0]) ||
        (tf && (tf.path || tf.filePath || tf.tempFilePath))
      if (path) resolve(path)
      else reject(new Error('无文件路径'))
    }

    const tryChoose = (opts, next) => {
      if (typeof uni.chooseFile !== 'function') {
        next && next()
        return
      }
      uni.chooseFile({
        count: 1,
        ...opts,
        success: onSuccess,
        fail: (err) => {
          if (next) next()
          else reject(err)
        }
      })
    }

    tryChoose({ type: 'all', extension: ['.json'] }, () => {
      tryChoose({ type: 'file' }, () => {
        tryChoose({}, () => reject(new Error('chooseFile unsupported')))
      })
    })
  })
}

// #ifdef APP-PLUS
function startAppImportPicker(runImport, reject) {
  uni.showActionSheet({
    itemList: ['从文件管理器选择', '导入应用内备份', '粘贴 JSON 内容'],
    success: (res) => {
      if (res.tapIndex === 0) {
        // 勿在打开文件选择器前 showLoading，遮罩会挡住系统文件管理器
        const openPicker = () =>
          pickJsonFileApp()
            .then((text) => {
              uni.showLoading({ title: '导入中...', mask: true })
              return runImport(text)
            })
            .catch((err) => {
              const msg = err && err.message ? String(err.message) : ''
              if (msg === 'cancel' || (err && err.errMsg && String(err.errMsg).includes('cancel'))) {
                reject(new Error('cancel'))
                return
              }
              console.error('pickJsonFileApp', err)
              const hint =
                msg && msg.length < 80 ? msg : '读取文件失败，请确认选中 wardrobe_export.json'
              uni.showModal({
                title: '选文件失败',
                content: `${hint}\n\n可改用「导入应用内备份」（需本机先导出一次）或「粘贴 JSON 内容」。`,
                confirmText: '去粘贴',
                success: (m) => {
                  if (m.confirm) {
                    uni.navigateTo({ url: '/pages/importPaste/importPaste' })
                  }
                  reject(err)
                }
              })
            })
        setTimeout(openPicker, 280)
      } else if (res.tapIndex === 1) {
        uni.showLoading({ title: '正在读取备份...', mask: true })
        readAppLocalBackup(EXPORT_NAME)
          .then((text) => {
            uni.hideLoading()
            return runImport(text)
          })
          .catch((err) => {
            uni.hideLoading()
            const msg = err && err.message ? String(err.message) : ''
            if (msg === 'cancel') {
              reject(new Error('cancel'))
              return
            }
            console.error('readAppLocalBackup', err)
            uni.showModal({
              title: '读取备份失败',
              content: `${msg || '未知错误'}\n\n请先在本机「导出」一次，或改用「从文件管理器选择」。`,
              showCancel: false
            })
            reject(err)
          })
      } else {
        uni.navigateTo({
          url: '/pages/importPaste/importPaste',
          fail: () => {
            uni.showToast({ title: '无法打开粘贴页', icon: 'none' })
            reject(new Error('nav fail'))
          }
        })
        reject(new Error('cancel'))
      }
    },
    fail: () => reject(new Error('cancel'))
  })
}
// #endif

export function importJson() {
  return new Promise((resolve, reject) => {
    const runImport = async (text) => {
      if (!text || !String(text).trim()) {
        console.error('[Cloth导入] 文本为空，中止')
        uni.showToast({ title: '读取内容为空，未导入', icon: 'none' })
        reject(new Error('empty text'))
        return
      }
      uni.showLoading({ title: '导入中...', mask: true })
      try {
        resolve(await processImportText(text))
      } catch (e) {
        if (e && e.message !== 'cancel') {
          console.error('importJson', e)
          if (!['parse error', 'parse null', 'invalid format', 'no valid items'].includes(e.message)) {
            uni.showToast({ title: '导入失败', icon: 'none' })
          }
        }
        reject(e)
      } finally {
        uni.hideLoading()
      }
    }

    const handleFile = async (filePath, h5File) => {
      if (!filePath && !h5File) {
        uni.showToast({ title: '未选择文件', icon: 'none' })
        reject(new Error('no file'))
        return
      }
      try {
        await runImport(await readJsonFile(filePath, h5File))
      } catch (e) {
        if (e && e.message !== 'cancel') {
          uni.showToast({ title: '读取文件失败', icon: 'none' })
        }
        reject(e)
      }
    }

    const onFail = (err) => {
      if (err && err.errMsg && String(err.errMsg).includes('cancel')) {
        reject(new Error('cancel'))
        return
      }
      uni.showToast({ title: '无法选择文件', icon: 'none' })
      reject(err || new Error('pick fail'))
    }

    // #ifdef H5
    chooseJsonFileH5()
      .then((text) => runImport(text))
      .catch((e) => {
        if (e && e.message !== 'cancel') {
          uni.chooseFile({
            count: 1,
            extension: ['.json'],
            success: (res) => {
              const tf = res.tempFiles && res.tempFiles[0]
              const file = tf && (tf.file || tf)
              const path =
                (res.tempFilePaths && res.tempFilePaths[0]) || (tf && tf.path)
              handleFile(path, file)
            },
            fail: onFail
          })
        } else reject(e)
      })
    return
    // #endif

    // #ifdef MP-WEIXIN
    uni.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['.json'],
      success: (res) => {
        const f = res.tempFiles && res.tempFiles[0]
        if (f && f.path) handleFile(f.path)
        else onFail(new Error('无文件'))
      },
      fail: onFail
    })
    return
    // #endif

    // #ifdef APP-PLUS
    startAppImportPicker(runImport, reject)
    return
    // #endif

    pickJsonFileUni()
      .then((path) => handleFile(path))
      .catch(onFail)
  })
}
