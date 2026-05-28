/**
 * App 端文件导入/导出（Android 原生读写）
 */
import {
  APP_EXPORT_DOWNLOAD_FILE,
  APP_EXPORT_DOWNLOAD_ZIP_FILE,
  LAST_EXPORT_PATH_KEY
} from './constants.js'

export { LAST_EXPORT_PATH_KEY }

const PICK_REQUEST_CODE = 90262
const IMPORT_TMP = '_doc/_import_tmp.json'
const IMPORT_TMP_ZIP = '_doc/_import_tmp.zip'
const WRITE_CHUNK = 50000

function getAppFsManager() {
  // #ifdef APP-PLUS
  return null
  // #endif
  // #ifndef APP-PLUS
  return typeof uni.getFileSystemManager === 'function' ? uni.getFileSystemManager() : null
  // #endif
}

/** 本地路径 → uni.downloadFile 可用的 file:// URL（支持 _doc/ 相对路径或绝对路径） */
function pathToLocalDownloadUrl(relOrAbs) {
  let abs = String(relOrAbs || '').trim()
  if (!abs) throw new Error('路径为空')
  if (abs.startsWith('_') && typeof plus !== 'undefined' && plus.io) {
    abs = plus.io.convertLocalFileSystemURL(abs)
  }
  if (abs.startsWith('file://')) return abs
  if (abs.startsWith('/')) return `file://${abs}`
  return abs
}

/** uni.downloadFile 复制本地文件（非 http 下载；url 为 file:// 或 convert 后的绝对路径） */
function uniDownloadFileLocal(relOrAbs) {
  const url = pathToLocalDownloadUrl(relOrAbs)
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url,
      success: (res) => {
        console.warn('[Cloth导出] downloadFile', url, res)
        const path = res.tempFilePath
        if (path && (res.statusCode === 200 || res.statusCode === 0)) {
          resolve(path)
          return
        }
        reject(new Error(`downloadFile 失败 status=${res.statusCode}`))
      },
      fail: (err) => reject(err || new Error('downloadFile 调用失败'))
    })
  })
}

function absPathFromAny(filePath) {
  let p = String(filePath || '').trim()
  if (!p) return ''
  if (p.startsWith('file://') && typeof plus !== 'undefined' && plus.io) {
    try {
      p = plus.io.convertLocalFileSystemURL(p)
    } catch {
      p = p.replace(/^file:\/\//i, '')
    }
  }
  if (p.startsWith('/') || /^[a-zA-Z]:/.test(p)) return p
  if (p.startsWith('_') && typeof plus !== 'undefined' && plus.io) {
    try {
      return plus.io.convertLocalFileSystemURL(p)
    } catch {
      return p
    }
  }
  return p
}

function fsPathsForRead(filePath) {
  const raw = String(filePath || '').trim()
  const list = []
  const seen = new Set()
  const add = (p) => {
    const s = String(p || '').trim()
    if (!s || seen.has(s)) return
    seen.add(s)
    list.push(s)
  }
  add(raw)
  if (raw.startsWith('file://')) {
    add(raw.replace(/^file:\/\//i, ''))
  }
  const abs = absPathFromAny(raw)
  if (abs && abs.includes('/doc/')) {
    const name = abs.split('/doc/').pop()
    if (name) add(`_doc/${name}`)
  }
  return list
}

/**
 * 大文件读取：优先 getFileSystemManager.readFile（与同事 demo 一致），失败再走 Android 原生
 */
export function readBigFileUtf8(filePath) {
  const fs = getAppFsManager()
  const paths = fsPathsForRead(filePath)
  const absPath = absPathFromAny(filePath)

  const readNative = () => {
    // #ifdef APP-PLUS
    if (absPath && absPath.startsWith('/')) {
      return readTextFileAndroid(absPath)
    }
    // #endif
    throw new Error('无法解析为可读绝对路径')
  }

  if (!fs) {
    return Promise.resolve(readNative())
  }

  let idx = 0
  const tryNext = () =>
    new Promise((resolve, reject) => {
      if (idx >= paths.length) {
        try {
          resolve(readNative())
        } catch (e) {
          reject(e)
        }
        return
      }
      const p = paths[idx++]
      fs.readFile({
        filePath: p,
        encoding: 'utf-8',
        success: (res) => {
          const text = typeof res.data === 'string' ? res.data : ''
          if (text.length >= 10) {
            console.warn('[Cloth导入] fs.readFile 成功', p, '字符', text.length)
            resolve(text)
            return
          }
          tryNext().then(resolve).catch(reject)
        },
        fail: () => {
          tryNext().then(resolve).catch(reject)
        }
      })
    })

  return tryNext()
}

/**
 * 大文件分块写入 _doc：writeFile + appendFile；失败再用 Java BufferedWriter
 */
export async function writeBigFileUtf8(relPath, content) {
  const text = String(content || '')
  if (!text.length) {
    throw new Error('内容为空')
  }

  const rel = String(relPath || '').trim()
  const fs = getAppFsManager()

  if (fs && rel.startsWith('_')) {
    try {
      await ensureDocDir()
      const first = text.slice(0, WRITE_CHUNK)
      await new Promise((resolve, reject) => {
        fs.writeFile({
          filePath: rel,
          data: first,
          encoding: 'utf8',
          success: resolve,
          fail: reject
        })
      })
      for (let i = WRITE_CHUNK; i < text.length; i += WRITE_CHUNK) {
        const slice = text.slice(i, i + WRITE_CHUNK)
        await new Promise((resolve, reject) => {
          fs.appendFile({
            filePath: rel,
            data: slice,
            encoding: 'utf8',
            success: resolve,
            fail: reject
          })
        })
      }
      await verifyDocFile(rel, 10)
      const abs =
        typeof plus !== 'undefined' && plus.io ? plus.io.convertLocalFileSystemURL(rel) : rel
      console.warn('[Cloth导出] fs 分块写入', rel, abs)
      // #ifdef APP-PLUS
      if (isAndroid() && abs && abs.startsWith('/')) {
        verifyAndroidFile(abs, 10)
      }
      // #endif
      return abs
    } catch (e) {
      console.warn('[Cloth导出] fs 分块写入失败，改 Java', e)
    }
  }

  // #ifdef APP-PLUS
  const abs = absPathFromAny(rel)
  if (isAndroid() && abs && abs.startsWith('/')) {
    writeTextToAbsPath(abs, text)
    return abs
  }
  // #endif
  throw new Error('无法写入文件')
}

// #ifdef APP-PLUS
function isAndroid() {
  return typeof plus !== 'undefined' && plus.os && plus.os.name === 'Android'
}

function androidInvoke(obj, method, ...args) {
  return plus.android.invoke(obj, method, ...args)
}

/** InputStream → UTF-8 字符串 */
function readStreamAsUtf8(inputStream) {
  const ByteArrayOutputStream = plus.android.importClass('java.io.ByteArrayOutputStream')
  const baos = plus.android.newObject('java.io.ByteArrayOutputStream')
  const bufLen = 8192
  const buffer = plus.android.newObject('byte[]', bufLen)
  let len = Number(androidInvoke(inputStream, 'read', buffer, 0, bufLen))
  while (len !== -1) {
    if (len > 0) {
      androidInvoke(baos, 'write', buffer, 0, len)
    }
    len = Number(androidInvoke(inputStream, 'read', buffer, 0, bufLen))
  }
  androidInvoke(inputStream, 'close')
  const bytes = androidInvoke(baos, 'toByteArray')
  const Charset = plus.android.importClass('java.nio.charset.Charset')
  const utf8 = androidInvoke(Charset, 'forName', 'UTF-8')
  const StringCls = plus.android.importClass('java.lang.String')
  const str = plus.android.newObject('java.lang.String', bytes, utf8)
  const result = String(androidInvoke(str, 'toString'))
  if (!result || result === 'null') {
    throw new Error('UTF-8 解码失败（文件可能过大）')
  }
  return result
}

function readStreamChunk(bis, buffer, bufLen) {
  let len = Number(androidInvoke(bis, 'read', buffer, 0, bufLen))
  if (Number.isFinite(len) && len >= 0) return len
  len = Number(androidInvoke(bis, 'read', buffer))
  return Number.isFinite(len) ? len : -1
}

/** NIO 通道复制（避免 read 死循环卡死 UI） */
function copyStreamNio(inputStream, absPath) {
  const Channels = plus.android.importClass('java.nio.channels.Channels')
  const FileOutputStream = plus.android.importClass('java.io.FileOutputStream')
  const Long = plus.android.importClass('java.lang.Long')

  const parentPath = absPath.replace(/\/[^/]+$/, '')
  if (parentPath) ensureAndroidDir(parentPath)

  const inCh = androidInvoke(Channels, 'newChannel', inputStream)
  const fos = plus.android.newObject('java.io.FileOutputStream', absPath)
  const outCh = androidInvoke(fos, 'getChannel')
  const maxVal = typeof Long.MAX_VALUE !== 'undefined' ? Long.MAX_VALUE : 999999999
  androidInvoke(outCh, 'transferFrom', inCh, 0, maxVal)
  androidInvoke(inCh, 'close')
  androidInvoke(outCh, 'close')
  androidInvoke(fos, 'close')
}

/** 逐块复制（带停滞检测，防止 len 恒为 0 时死循环） */
function copyStreamChunkedSafe(inputStream, absPath) {
  const BufferedInputStream = plus.android.importClass('java.io.BufferedInputStream')
  const BufferedOutputStream = plus.android.importClass('java.io.BufferedOutputStream')
  const FileOutputStream = plus.android.importClass('java.io.FileOutputStream')

  const parentPath = absPath.replace(/\/[^/]+$/, '')
  if (parentPath) ensureAndroidDir(parentPath)

  const bis = plus.android.newObject('java.io.BufferedInputStream', inputStream)
  const fos = plus.android.newObject('java.io.FileOutputStream', absPath)
  const bos = plus.android.newObject('java.io.BufferedOutputStream', fos)
  const bufLen = 16384
  const buffer = plus.android.newObject('byte[]', bufLen)

  let zeroStreak = 0
  let total = 0
  const MAX_BYTES = 80 * 1024 * 1024

  while (true) {
    const len = readStreamChunk(bis, buffer, bufLen)
    if (len === -1) break
    if (len === 0) {
      zeroStreak++
      if (zeroStreak > 300) {
        throw new Error('读取停滞（content URI 无数据）')
      }
      continue
    }
    zeroStreak = 0
    androidInvoke(bos, 'write', buffer, 0, len)
    total += len
    if (total > MAX_BYTES) throw new Error('文件过大')
  }
  androidInvoke(bis, 'close')
  androidInvoke(bos, 'flush')
  androidInvoke(bos, 'close')
}

function copyStreamToAbsPath(inputStream, absPath) {
  try {
    copyStreamNio(inputStream, absPath)
  } catch (e) {
    console.warn('[Cloth导入] NIO 复制失败，改逐块', e)
    copyStreamChunkedSafe(inputStream, absPath)
  }
}

function getUriString(uri) {
  try {
    return String(androidInvoke(uri, 'toString') || uri)
  } catch {
    return String(uri)
  }
}

/** 外置存储根目录（基座上 getExternalStorageDirectory 可能返回 null） */
function getAndroidExternalStorageRoot() {
  const fallback = '/storage/emulated/0'
  try {
    const Environment = plus.android.importClass('android.os.Environment')
    const ext = androidInvoke(Environment, 'getExternalStorageDirectory')
    if (ext) {
      const base = androidInvoke(ext, 'getAbsolutePath')
      const s = base != null ? String(base) : ''
      if (s && s !== 'null' && s.startsWith('/')) {
        return s.replace(/\/+$/, '')
      }
    }
  } catch (e) {
    console.warn('[Cloth导入] getExternalStorageDirectory 异常，用默认根路径', e)
  }
  return fallback
}

function primaryRelToAbsPath(rel) {
  const root = getAndroidExternalStorageRoot()
  return `${root}/${rel}`.replace(/\/+/g, '/')
}

/**
 * 从 URI 字符串解析 primary: 相对路径 → 绝对路径（不依赖 Java API，支持中文目录）
 * 例：.../document/primary%3AA%E5%90%B4%E6%97%96%E8%90%B1%2Fwardrobe_export.json
 */
function resolvePrimaryPathFromUriStr(uriStr) {
  if (!uriStr) return null
  const m = uriStr.match(/\/document\/([^?#]+)/i)
  if (!m) return null
  let docId = ''
  try {
    docId = decodeURIComponent(m[1])
  } catch {
    docId = m[1].replace(/%3A/gi, ':').replace(/%2F/gi, '/')
  }
  if (!docId.startsWith('primary:')) return null
  const rel = docId.slice('primary:'.length)
  return primaryRelToAbsPath(rel)
}

/** primary 相对路径：依次尝试多个常见存储根（防止根路径为 null） */
async function readJsonByPrimaryRel(rel, source) {
  const roots = [
    getAndroidExternalStorageRoot(),
    '/storage/emulated/0',
    '/sdcard',
    '/storage/self/primary'
  ]
  const tried = new Set()
  let lastErr = null
  for (const root of roots) {
    if (!root || tried.has(root)) continue
    tried.add(root)
    const abs = `${root}/${rel}`.replace(/\/+/g, '/')
    try {
      return await readJsonByAbsPath(abs, `${source}(${root})`)
    } catch (e) {
      lastErr = e
      console.warn('[Cloth导入] 尝试根路径失败', abs, e && e.message)
    }
  }
  throw lastErr || new Error(`无法读取：${rel}`)
}

/** 与「备份文件夹导入」相同的读法 */
function readJsonByAbsPath(absPath, source) {
  const size = getAndroidFileSize(absPath)
  console.warn('[Cloth导入]', source, absPath, '大小', size, 'B')
  if (!size || size < 10) {
    return Promise.reject(new Error(`文件不存在或过小(${size || 0}B)：${absPath}`))
  }
  return readBigFileUtf8(absPath).then((text) => validateImportJsonText(text))
}

/** 将文件选择器返回的 URI 尽量还原为可读绝对路径 */
function tryResolveUriToAbsPath(uri) {
  const uriStr = getUriString(uri)
  console.warn('[Cloth导入] pick URI', uriStr)

  const fromStr = resolvePrimaryPathFromUriStr(uriStr)
  if (fromStr) {
    console.warn('[Cloth导入] URI字符串→路径', fromStr)
    return fromStr
  }

  const rawIdx = uriStr.indexOf('/raw:')
  if (rawIdx >= 0) {
    const raw = decodeURIComponent(uriStr.slice(rawIdx + 5).split('?')[0])
    if (raw.startsWith('/')) return raw
  }

  if (uriStr.startsWith('file://')) {
    const p = decodeURIComponent(uriStr.replace(/^file:\/\//, ''))
    if (p.startsWith('/')) return p
  }
  if (uriStr.startsWith('/')) return uriStr

  try {
    const main = plus.android.runtimeMainActivity()
    const DocumentsContract = plus.android.importClass('android.provider.DocumentsContract')
    if (androidInvoke(DocumentsContract, 'isDocumentUri', main, uri)) {
      const docId = String(androidInvoke(DocumentsContract, 'getDocumentId', uri) || '')
      console.warn('[Cloth导入] documentId', docId)
      const Environment = plus.android.importClass('android.os.Environment')
      if (docId.startsWith('primary:')) {
        const rel = docId.slice('primary:'.length)
        return primaryRelToAbsPath(rel)
      }
      if (docId.startsWith('raw:')) {
        const p = docId.slice('raw:'.length)
        if (p.startsWith('/')) return p
      }
    }
  } catch (e) {
    console.warn('[Cloth导入] DocumentsContract 解析失败', e)
  }

  try {
    const main = plus.android.runtimeMainActivity()
    const resolver = androidInvoke(main, 'getContentResolver')
    const cursor = androidInvoke(resolver, 'query', uri, ['_data'], null, null, null)
    if (cursor && androidInvoke(cursor, 'moveToFirst')) {
      const idx = Number(androidInvoke(cursor, 'getColumnIndex', '_data'))
      if (idx >= 0) {
        const path = androidInvoke(cursor, 'getString', idx)
        androidInvoke(cursor, 'close')
        if (path) return String(path)
      }
      androidInvoke(cursor, 'close')
    }
  } catch (e) {
    console.warn('[Cloth导入] query _data 失败', e)
  }

  return null
}

/** 从 content:// URI 用 BufferedReader 读 UTF-8 文本（适合 Downloads msf:，避免字节流复制 0B） */
function readUriTextViaBufferedReader(uri) {
  const main = plus.android.runtimeMainActivity()
  const resolver = androidInvoke(main, 'getContentResolver')
  const inputStream = androidInvoke(resolver, 'openInputStream', uri)
  if (!inputStream) {
    throw new Error('无法打开 content URI 输入流')
  }

  const InputStreamReader = plus.android.importClass('java.io.InputStreamReader')
  const BufferedReader = plus.android.importClass('java.io.BufferedReader')
  const isr = plus.android.newObject('java.io.InputStreamReader', inputStream, 'UTF-8')
  const br = plus.android.newObject('java.io.BufferedReader', isr)
  const StringBuilder = plus.android.importClass('java.lang.StringBuilder')
  const sb = plus.android.newObject('java.lang.StringBuilder')

  let line = androidInvoke(br, 'readLine')
  let lines = 0
  while (line != null) {
    lines++
    if (lines > 1) {
      androidInvoke(sb, 'append', '\n')
    }
    androidInvoke(sb, 'append', String(line))
    line = androidInvoke(br, 'readLine')
  }
  androidInvoke(br, 'close')
  try {
    androidInvoke(inputStream, 'close')
  } catch {
    /* ignore */
  }

  const result = String(androidInvoke(sb, 'toString'))
  console.warn('[Cloth导入] URI BufferedReader', '行数', lines, '字符', result.length)
  if (!result || result.length < 10) {
    throw new Error(`URI 文本过短(${result ? result.length : 0})`)
  }
  return result
}

/** 查询 content URI 的 _data 等列，尝试得到真实路径 */
function resolveUriDataColumnPath(uri) {
  const main = plus.android.runtimeMainActivity()
  const resolver = androidInvoke(main, 'getContentResolver')
  const columns = ['_data', 'local_file', 'local_filename']
  for (const col of columns) {
    try {
      const cursor = androidInvoke(resolver, 'query', uri, [col], null, null, null)
      if (!cursor || !androidInvoke(cursor, 'moveToFirst')) {
        if (cursor) androidInvoke(cursor, 'close')
        continue
      }
      const idx = Number(androidInvoke(cursor, 'getColumnIndex', col))
      if (idx >= 0) {
        const path = androidInvoke(cursor, 'getString', idx)
        androidInvoke(cursor, 'close')
        const s = path != null ? String(path) : ''
        if (s.startsWith('/')) {
          console.warn('[Cloth导入] query', col, '→', s)
          return s
        }
      }
      androidInvoke(cursor, 'close')
    } catch (e) {
      console.warn('[Cloth导入] query', col, e)
    }
  }
  return null
}

/** msf:212316 → 查 downloads 表或常见 Download 目录 */
function resolveMsfDownloadsAbsPath(uriStr) {
  const m = uriStr.match(/msf%3A(\d+)|msf:(\d+)/i)
  if (!m) return null
  const id = parseInt(m[1] || m[2], 10)
  if (!id) return null

  try {
    const Uri = plus.android.importClass('android.net.Uri')
    const ContentUris = plus.android.importClass('android.content.ContentUris')
    const baseUri = Uri.parse('content://downloads/all_downloads')
    const downloadUri = androidInvoke(ContentUris, 'withAppendedId', baseUri, id)
    const main = plus.android.runtimeMainActivity()
    const resolver = androidInvoke(main, 'getContentResolver')
    const cursor = androidInvoke(resolver, 'query', downloadUri, ['_data', 'local_uri'], null, null, null)
    if (cursor && androidInvoke(cursor, 'moveToFirst')) {
      for (const col of ['_data', 'local_uri']) {
        const idx = Number(androidInvoke(cursor, 'getColumnIndex', col))
        if (idx >= 0) {
          const path = androidInvoke(cursor, 'getString', idx)
          if (path) {
            const s = String(path)
            androidInvoke(cursor, 'close')
            if (s.startsWith('/')) return s
            if (s.startsWith('file://')) return s.replace(/^file:\/\//, '')
          }
        }
      }
      androidInvoke(cursor, 'close')
    }
  } catch (e) {
    console.warn('[Cloth导入] resolveMsfDownloads', e)
  }

  const names = ['wardrobe_export.json', 'wardrobe_export (1).json']
  const dirs = [
    '/storage/emulated/0/Download/Weixin/',
    '/storage/emulated/0/Download/WeiXin/',
    '/storage/emulated/0/Download/'
  ]
  for (const dir of dirs) {
    for (const name of names) {
      const abs = `${dir}${name}`
      const size = getAndroidFileSize(abs)
      if (size >= 10) {
        console.warn('[Cloth导入] msf 回落路径', abs, size, 'B')
        return abs
      }
    }
  }
  return null
}

/** 读取任意 content URI 的 JSON（msf / 非 primary 通用） */
async function readJsonFromContentUri(uri, uriStr) {
  try {
    return validateImportJsonText(readUriTextViaBufferedReader(uri))
  } catch (e1) {
    console.warn('[Cloth导入] BufferedReader 读 URI 失败', e1)
  }

  const dataPath = resolveUriDataColumnPath(uri)
  if (dataPath) {
    try {
      return await readJsonByAbsPath(dataPath, '选择器→_data')
    } catch (e2) {
      console.warn('[Cloth导入] _data 路径读取失败', e2)
    }
  }

  const msfPath = resolveMsfDownloadsAbsPath(uriStr)
  if (msfPath) {
    try {
      return await readJsonByAbsPath(msfPath, '选择器→msf回落')
    } catch (e3) {
      console.warn('[Cloth导入] msf 回落路径失败', e3)
    }
  }

  const absTmp = plus.io.convertLocalFileSystemURL(IMPORT_TMP)
  const size = copyUriToAbsPath(uri, absTmp)
  if (size >= 10) {
    return await readJsonByAbsPath(absTmp, '选择器→临时文件')
  }
  throw new Error('URI 流复制为 0 字节')
}

/** content URI → 本地临时文件（多种方式，避免 0B） */
function copyUriToAbsPath(uri, absPath) {
  const main = plus.android.runtimeMainActivity()
  const resolver = androidInvoke(main, 'getContentResolver')
  const parentPath = absPath.replace(/\/[^/]+$/, '')
  if (parentPath) ensureAndroidDir(parentPath)

  const checkSize = () => {
    const size = getAndroidFileSize(absPath)
    return size >= 10 ? size : 0
  }

  // 1) Android 10+ FileUtils.copy
  try {
    const FileUtils = plus.android.importClass('android.os.FileUtils')
    const File = plus.android.importClass('java.io.File')
    const inputStream = androidInvoke(resolver, 'openInputStream', uri)
    if (inputStream && FileUtils) {
      const dest = plus.android.newObject('java.io.File', absPath)
      androidInvoke(FileUtils, 'copy', inputStream, dest)
      androidInvoke(inputStream, 'close')
      const size = checkSize()
      if (size) {
        console.warn('[Cloth导入] FileUtils.copy 成功', size, 'B')
        return size
      }
    }
  } catch (e) {
    console.warn('[Cloth导入] FileUtils.copy 失败', e)
  }

  // 2) PFD + FileInputStream(FileDescriptor)（比 createInputStream 更稳）
  let pfd = null
  try {
    pfd = androidInvoke(resolver, 'openFileDescriptor', uri, 'r')
    if (pfd) {
      const fd = androidInvoke(pfd, 'getFileDescriptor')
      const FileInputStream = plus.android.importClass('java.io.FileInputStream')
      const fis = plus.android.newObject('java.io.FileInputStream', fd)
      copyStreamToAbsPath(fis, absPath)
      const size = checkSize()
      if (size) {
        console.warn('[Cloth导入] PFD+FD 复制成功', size, 'B')
        return size
      }
      console.warn('[Cloth导入] PFD+FD 复制后过小')
    }
  } catch (e) {
    console.warn('[Cloth导入] PFD+FD 失败', e)
  } finally {
    if (pfd) {
      try {
        androidInvoke(pfd, 'close')
      } catch {
        /* ignore */
      }
    }
  }

  const inputStream = androidInvoke(resolver, 'openInputStream', uri)
  if (!inputStream) {
    throw new Error('无法打开所选文件流')
  }
  copyStreamToAbsPath(inputStream, absPath)
  const size = checkSize()
  if (!size) {
    throw new Error('URI 流复制为 0 字节')
  }
  return size
}

/** Android：分块写入到绝对路径（与 _doc 写入相同逻辑） */
function writeTextToAbsPath(absPath, text) {
  const content = String(text)
  if (!content.length) {
    throw new Error('导出内容为空')
  }

  const FileWriter = plus.android.importClass('java.io.FileWriter')
  const BufferedWriter = plus.android.importClass('java.io.BufferedWriter')
  const fw = plus.android.newObject('java.io.FileWriter', absPath, false)
  const bw = plus.android.newObject('java.io.BufferedWriter', fw)

  for (let i = 0; i < content.length; i += WRITE_CHUNK) {
    androidInvoke(bw, 'write', content.slice(i, i + WRITE_CHUNK))
  }
  androidInvoke(bw, 'flush')
  androidInvoke(bw, 'close')

  const File = plus.android.importClass('java.io.File')
  const f = plus.android.newObject('java.io.File', absPath)
  const size = Number(androidInvoke(f, 'length'))
  if (!size || size < 10) {
    throw new Error(`写入后文件过小(${size || 0}B)`)
  }
  return absPath
}

function writeTextFileAndroid(relPath, text) {
  const absPath = plus.io.convertLocalFileSystemURL(relPath)
  return writeTextToAbsPath(absPath, text)
}

/** 格式化 I/O 错误信息 */
function formatIoErr(e) {
  if (!e) return '未知错误'
  if (typeof e === 'string') return e
  const msg = e.message || e.msg || e.errMsg
  if (msg) return String(msg)
  try {
    return JSON.stringify(e)
  } catch {
    return String(e)
  }
}

const ANDROID_B64_NO_WRAP = 2

/** 清洗 base64，兼容 Android Base64.decode */
export function sanitizeBase64ForAndroid(b64) {
  let content = String(b64 || '')
  const commaIdx = content.indexOf(',')
  if (commaIdx > 0 && commaIdx < 100 && /^data:/i.test(content.slice(0, commaIdx))) {
    content = content.slice(commaIdx + 1)
  }
  content = content.replace(/[\r\n\s]/g, '')
  const pad = content.length % 4
  if (pad === 2) content += '=='
  else if (pad === 3) content += '='
  return content
}

/** Java 写 JSZip 原生 base64 字符串 */
function writeBase64StringToAbsPathJava(absPath, b64) {
  const content = sanitizeBase64ForAndroid(b64)
  if (content.length < 16) throw new Error('ZIP base64 为空')
  const Base64 = plus.android.importClass('android.util.Base64')
  const BufferedOutputStream = plus.android.importClass('java.io.BufferedOutputStream')
  const parentPath = absPath.replace(/\/[^/]+$/, '')
  if (parentPath) ensureAndroidDir(parentPath)
  const fos = plus.android.newObject('java.io.FileOutputStream', absPath)
  const bos = plus.android.newObject('java.io.BufferedOutputStream', fos)
  const B64_CHUNK = 8192
  for (let offset = 0; offset < content.length; offset += B64_CHUNK) {
    let slice = content.slice(offset, offset + B64_CHUNK)
    if (offset + B64_CHUNK < content.length) {
      slice = slice.slice(0, Math.floor(slice.length / 4) * 4)
    }
    const bytes = androidInvoke(Base64, 'decode', slice, ANDROID_B64_NO_WRAP)
    const len = bytes ? Number(androidInvoke(bytes, 'length')) : 0
    if (!len) throw new Error(`Base64 解码为空(offset=${offset})`)
    androidInvoke(bos, 'write', bytes, 0, len)
  }
  androidInvoke(bos, 'flush')
  androidInvoke(bos, 'close')
  androidInvoke(fos, 'close')
  return verifyAndroidFile(absPath, 10)
}

/** Android 应用私有 files 目录（绕过 _doc 路径转换） */
function getAndroidFilesDirAbs() {
  const main = plus.android.runtimeMainActivity()
  const filesDir = androidInvoke(main, 'getFilesDir')
  const path = androidInvoke(filesDir, 'getAbsolutePath')
  return String(path || '')
}

/** 导出前路径诊断 */
function logZipExportPathDiagnostics(fileName) {
  try {
    const relPath = `_doc/${fileName}`
    const absPath = plus.io.convertLocalFileSystemURL(relPath)
    const docAbs = plus.io.convertLocalFileSystemURL('_doc/')
    const filesDir = getAndroidFilesDirAbs()
    console.warn('[诊断] relPath', relPath)
    console.warn('[诊断] absPath', absPath)
    console.warn('[诊断] absPath已存在大小', getAndroidFileSize(absPath), 'B')
    console.warn('[诊断] _doc目录', docAbs)
    console.warn('[诊断] filesDir', filesDir)
    if (docAbs) ensureAndroidDir(String(docAbs).replace(/\/+$/, ''))
    if (filesDir) ensureAndroidDir(filesDir)
    console.warn('[诊断] mkdir后 absPath大小', getAndroidFileSize(absPath), 'B')
  } catch (e) {
    console.warn('[诊断] 路径检查失败', e)
  }
}

function deleteAbsFileJava(absPath) {
  try {
    const File = plus.android.importClass('java.io.File')
    androidInvoke(plus.android.newObject('java.io.File', absPath), 'delete')
  } catch (_) {}
}

/** Java 端：b64 文本文件 → zip（Base64InputStream 流式解码，避免 decode(byte[]) 桥接） */
function decodeBase64TextFileJava(b64AbsPath, destAbsPath) {
  const b64Size = getAndroidFileSize(b64AbsPath)
  if (b64Size < 16) throw new Error(`b64 临时文件过小(${b64Size || 0}B)`)

  const FileInputStream = plus.android.importClass('java.io.FileInputStream')
  const Base64InputStream = plus.android.importClass('android.util.Base64InputStream')
  const fis = plus.android.newObject('java.io.FileInputStream', b64AbsPath)
  const b64Stream = plus.android.newObject('android.util.Base64InputStream', fis, ANDROID_B64_NO_WRAP)
  try {
    copyStreamToAbsPath(b64Stream, destAbsPath)
  } finally {
    try {
      androidInvoke(b64Stream, 'close')
    } catch (_) {}
    try {
      androidInvoke(fis, 'close')
    } catch (_) {}
  }
  deleteAbsFileJava(b64AbsPath)
  const size = verifyAndroidFile(destAbsPath, 10)
  console.warn('[Cloth导出] Base64InputStream 解码', destAbsPath, size, 'B')
  return size
}

/** Uint8Array 逐字节 write(int) 写入（桥接兜底，较慢但可靠） */
function writeUint8ViaWriteIntJava(absPath, data) {
  const u8 = data instanceof Uint8Array ? data : new Uint8Array(data)
  if (!u8.length) throw new Error('导出内容为空')
  const BufferedOutputStream = plus.android.importClass('java.io.BufferedOutputStream')
  const FileOutputStream = plus.android.importClass('java.io.FileOutputStream')
  const parentPath = absPath.replace(/\/[^/]+$/, '')
  if (parentPath) ensureAndroidDir(parentPath)
  const fos = plus.android.newObject('java.io.FileOutputStream', absPath)
  const bos = plus.android.newObject('java.io.BufferedOutputStream', fos)
  const LOG_EVERY = 512 * 1024
  for (let i = 0; i < u8.length; i++) {
    if (i > 0 && i % LOG_EVERY === 0) {
      console.warn('[Cloth导出] writeInt 进度', i, '/', u8.length)
    }
    androidInvoke(bos, 'write', u8[i] & 0xff)
  }
  androidInvoke(bos, 'flush')
  androidInvoke(bos, 'close')
  androidInvoke(fos, 'close')
  return verifyAndroidFile(absPath, 10)
}

/** Uint8Array → base64 文本文件 → Base64InputStream 解码写 zip */
function writeZipViaBase64TextBridge(absPath, data) {
  const u8 = data instanceof Uint8Array ? data : new Uint8Array(data)
  if (!u8.length) throw new Error('导出内容为空')
  const b64Path = `${absPath}.b64tmp`
  const B64_CHUNK = 384 * 1024
  let b64 = ''
  for (let i = 0; i < u8.length; i += B64_CHUNK) {
    b64 += uint8ToBase64Chunk(u8.subarray(i, Math.min(i + B64_CHUNK, u8.length)))
  }
  b64 = sanitizeBase64ForAndroid(b64)
  if (b64.length < 16) throw new Error('base64 为空')
  writeTextToAbsPath(b64Path, b64)
  const b64Size = getAndroidFileSize(b64Path)
  console.warn('[Cloth导出] b64 临时文件', b64Path, b64Size, 'B')
  if (b64Size < 16) throw new Error(`b64 临时文件过小(${b64Size}B)`)
  try {
    return decodeBase64TextFileJava(b64Path, absPath)
  } catch (e1) {
    console.warn('[Cloth导出] Base64InputStream 失败，改 writeInt', e1)
    deleteAbsFileJava(b64Path)
    return writeUint8ViaWriteIntJava(absPath, u8)
  }
}

/** Java 直接写 Uint8Array（ISO-8859-1 转 byte[]，部分基座无效，仅作兜底） */
function writeUint8DirectJava(absPath, data) {
  const u8 = data instanceof Uint8Array ? data : new Uint8Array(data)
  if (!u8.length) throw new Error('导出内容为空')
  const BufferedOutputStream = plus.android.importClass('java.io.BufferedOutputStream')
  const Charset = plus.android.importClass('java.nio.charset.Charset')
  const iso = androidInvoke(Charset, 'forName', 'ISO-8859-1')
  const parentPath = absPath.replace(/\/[^/]+$/, '')
  if (parentPath) ensureAndroidDir(parentPath)
  const fos = plus.android.newObject('java.io.FileOutputStream', absPath)
  const bos = plus.android.newObject('java.io.BufferedOutputStream', fos)
  const CHUNK = 32768
  for (let i = 0; i < u8.length; i += CHUNK) {
    const slice = u8.subarray(i, Math.min(i + CHUNK, u8.length))
    let binary = ''
    const STEP = 0x8000
    for (let j = 0; j < slice.length; j += STEP) {
      const sub = slice.subarray(j, Math.min(j + STEP, slice.length))
      binary += String.fromCharCode.apply(null, sub)
    }
    const jstr = plus.android.newObject('java.lang.String', binary)
    const jbytes = androidInvoke(jstr, 'getBytes', iso)
    const len = jbytes ? Number(androidInvoke(jbytes, 'length')) : 0
    if (!len) throw new Error(`ISO-8859-1 转 byte[] 为空(offset=${i})`)
    androidInvoke(bos, 'write', jbytes, 0, len)
  }
  androidInvoke(bos, 'flush')
  androidInvoke(bos, 'close')
  androidInvoke(fos, 'close')
  return verifyAndroidFile(absPath, 10)
}

/** 优先写入 Android files 目录（绕过 _doc 路径转换） */
async function writeZipToAppFilesDir(fileName, payload) {
  const u8 = payload instanceof Uint8Array ? payload : new Uint8Array(payload)
  if (!u8.length) throw new Error('导出内容为空')
  const filesDir = getAndroidFilesDirAbs()
  if (!filesDir) throw new Error('无法获取 files 目录')
  ensureAndroidDir(filesDir)
  const absPath = `${filesDir}/${fileName}`.replace(/\/+/g, '/')
  const size = writeZipViaBase64TextBridge(absPath, u8)
  console.warn('[Cloth导出] filesDir b64桥写 zip', absPath, size, 'B')
  return { absPath, size }
}

/** plus.io 判断路径是否已有文件 */
function plusIoPathExists(pathOrUrl) {
  return new Promise((resolve) => {
    if (typeof plus === 'undefined' || !plus.io) {
      resolve(false)
      return
    }
    plus.io.resolveLocalFileSystemURL(
      pathOrUrl,
      () => resolve(true),
      () => resolve(false)
    )
  })
}

/** Android Download 目录保存路径（plus.downloader 的 filename） */
function plusDownloaderDownloadDest(fileName) {
  const base = APP_EXPORT_DOWNLOAD_ZIP_FILE.replace(/\/[^/]+$/, '')
  const dir = base.replace(/^\//, '')
  return `file://${dir}/${fileName}`
}

/** plus.downloader 保存文件（支持 data: URL、file:// 本地、http(s)） */
function plusDownloaderSave(sourceUrl, filename) {
  return new Promise((resolve, reject) => {
    if (typeof plus === 'undefined' || !plus.downloader) {
      reject(new Error('plus.downloader 不可用'))
      return
    }
    let settled = false
    const timer = setTimeout(() => {
      if (settled) return
      settled = true
      reject(new Error('plus.downloader 超时(120s)'))
    }, 120000)

    const task = plus.downloader.createDownload(
      sourceUrl,
      { filename },
      (d, status) => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        if (status === 200 && d && d.filename) {
          const abs = plus.io.convertLocalFileSystemURL(d.filename)
          const size = getAndroidFileSize(abs)
          console.warn('[Cloth导出] plus.downloader 完成', d.filename, size, 'B', 'status', status)
          if (size < 10) {
            reject(new Error(`保存后文件过小(${size || 0}B)`))
            return
          }
          resolve({ filename: d.filename, absPath: abs, size })
          return
        }
        reject(new Error(`plus.downloader 失败 status=${status}`))
      }
    )
    task.start()
  })
}

/**
 * JSZip base64 → plus.downloader 保存（参考掘金：先判存，再 createDownload）
 * 优先 data:application/zip;base64 写入 Download，并备份 _doc
 */
async function exportZipBase64ViaPlusDownloader(fileName, b64) {
  const content = sanitizeBase64ForAndroid(b64)
  if (content.length < 16) throw new Error('ZIP base64 为空')

  const dataUrl = `data:application/zip;base64,${content}`
  const downloadDest = plusDownloaderDownloadDest(fileName)
  const docDest = `_doc/${fileName}`

  deleteAbsFileJava(APP_EXPORT_DOWNLOAD_ZIP_FILE)
  removeRelZipIfExists(docDest)

  const downloadExists = await plusIoPathExists(downloadDest)
  if (downloadExists) {
    console.warn('[Cloth导出] Download 已有文件，先覆盖导出', downloadDest)
  }

  console.warn('[Cloth导出] plus.downloader → Download', downloadDest)
  let main
  try {
    main = await plusDownloaderSave(dataUrl, downloadDest)
  } catch (e1) {
    console.warn('[Cloth导出] dataUrl 保存失败，试 _doc 再复制', e1)
    const docSaved = await plusDownloaderSave(dataUrl, docDest)
    copyAbsFileJava(docSaved.absPath, APP_EXPORT_DOWNLOAD_ZIP_FILE)
    main = {
      filename: APP_EXPORT_DOWNLOAD_ZIP_FILE,
      absPath: APP_EXPORT_DOWNLOAD_ZIP_FILE,
      size: verifyAndroidFile(APP_EXPORT_DOWNLOAD_ZIP_FILE, 10)
    }
  }

  try {
    const docExists = await plusIoPathExists(docDest)
    if (!docExists) {
      await plusDownloaderSave(dataUrl, docDest)
      console.warn('[Cloth导出] _doc 备份完成', docDest)
    }
  } catch (e2) {
    console.warn('[Cloth导出] _doc 备份失败，尝试复制 Download', e2)
    try {
      const docAbs = plus.io.convertLocalFileSystemURL(docDest)
      copyAbsFileJava(main.absPath, docAbs)
    } catch (_) {}
  }

  scanMediaFile(main.absPath)
  const docAbsPath = plus.io.convertLocalFileSystemURL(docDest)
  return {
    absPath: main.absPath,
    size: main.size,
    docAbsPath,
    publicAbsPath: main.absPath,
    usedDownloadDir: true
  }
}

/** JSZip base64 → atob → plus.io FileWriter（兜底，大文件可能较慢） */
async function writeZipBase64ToRelPath(relPath, b64) {
  const content = sanitizeBase64ForAndroid(b64)
  if (content.length < 16) throw new Error('ZIP base64 为空')

  removeRelZipIfExists(relPath)
  const tries = []

  try {
    const buf = arrayBufferFromBase64(content)
    const u8 = new Uint8Array(buf)
    if (u8.length < 10) throw new Error('atob 后 zip 为空')
    console.warn('[Cloth导出] JS atob', u8.length, 'B，plus.io FileWriter 写入')
    return await writeZipViaPlusIoWriter(relPath, u8)
  } catch (e1) {
    tries.push(`plus.io：${formatIoErr(e1)}`)
    console.warn('[Cloth导出] plus.io FileWriter 写 zip 失败', e1)
  }

  throw new Error(`ZIP 写入失败\n${tries.join('\n')}`)
}

/** Uint8Array → base64（uint8 兜底路径用） */
function uint8ToBase64Chunk(u8) {
  const STEP = 0x8000
  let binary = ''
  for (let i = 0; i < u8.length; i += STEP) {
    const sub = u8.subarray(i, Math.min(i + STEP, u8.length))
    binary += String.fromCharCode.apply(null, sub)
  }
  if (typeof btoa === 'function') return btoa(binary)
  const StringCls = plus.android.importClass('java.lang.String')
  const Charset = plus.android.importClass('java.nio.charset.Charset')
  const iso = androidInvoke(Charset, 'forName', 'ISO-8859-1')
  const jstr = plus.android.newObject('java.lang.String', binary)
  const bytes = androidInvoke(jstr, 'getBytes', iso)
  const Base64 = plus.android.importClass('android.util.Base64')
  return String(androidInvoke(Base64, 'encodeToString', bytes, 0))
}

/** Java 分块写绝对路径（兜底） */
function writeBytesToAbsPathJava(absPath, data) {
  const u8 = data instanceof Uint8Array ? data : new Uint8Array(data)
  if (!u8.length) throw new Error('导出内容为空')
  const Base64 = plus.android.importClass('android.util.Base64')
  const BufferedOutputStream = plus.android.importClass('java.io.BufferedOutputStream')
  const parentPath = absPath.replace(/\/[^/]+$/, '')
  if (parentPath) ensureAndroidDir(parentPath)
  const fos = plus.android.newObject('java.io.FileOutputStream', absPath)
  const bos = plus.android.newObject('java.io.BufferedOutputStream', fos)
  const CHUNK = 61440
  for (let i = 0; i < u8.length; i += CHUNK) {
    const slice = u8.subarray(i, Math.min(i + CHUNK, u8.length))
    const b64 = sanitizeBase64ForAndroid(uint8ToBase64Chunk(slice))
    const bytes = androidInvoke(Base64, 'decode', b64, ANDROID_B64_NO_WRAP)
    const len = bytes ? Number(androidInvoke(bytes, 'length')) : 0
    if (!len) throw new Error('Base64 解码为空')
    androidInvoke(bos, 'write', bytes, 0, len)
  }
  androidInvoke(bos, 'flush')
  androidInvoke(bos, 'close')
  androidInvoke(fos, 'close')
  return verifyAndroidFile(absPath, 10)
}

/** 创建 App 端 Blob（优先 plus.io.Blob） */
function createAppBlob(buffer) {
  if (plus.io && typeof plus.io.Blob === 'function') {
    return new plus.io.Blob([buffer], { type: 'application/octet-stream' })
  }
  if (typeof Blob !== 'undefined') {
    return new Blob([buffer], { type: 'application/octet-stream' })
  }
  return null
}

/** plus.io resolveLocalFileSystemURL + FileWriter 分块写 zip（App 原生，参考 readAsDataURL 同体系） */
function writeZipViaPlusIoWriter(relPath, u8) {
  const fileName = String(relPath || '').replace(/^_doc\//, '')
  const absPath = plus.io.convertLocalFileSystemURL(relPath)
  const CHUNK = 256 * 1024
  const total = u8.length

  return new Promise((resolve, reject) => {
    if (typeof plus === 'undefined' || !plus.io) {
      reject(new Error('plus.io 不可用'))
      return
    }
    const onFail = (err) => reject(err || new Error('FileWriter 失败'))
    plus.io.resolveLocalFileSystemURL(
      '_doc/',
      (dirEntry) => {
        dirEntry.getFile(
          fileName,
          { create: true },
          (fileEntry) => {
            fileEntry.createWriter(
              (writer) => {
                let offset = 0
                let settled = false

                const fail = (err) => {
                  if (settled) return
                  settled = true
                  reject(err || new Error('FileWriter 失败'))
                }

                writer.onerror = (e) => fail(e)

                const writeNext = () => {
                  if (settled) return
                  if (offset >= total) {
                    settled = true
                    verifyRelFileSize(relPath, 10)
                      .then((size) => {
                        console.warn('[Cloth导出] plus.io FileWriter 写 zip', absPath, size, 'B')
                        resolve({ absPath, size })
                      })
                      .catch(fail)
                    return
                  }

                  const slice = u8.subarray(offset, Math.min(offset + CHUNK, total))
                  const buf = slice.buffer.slice(slice.byteOffset, slice.byteOffset + slice.byteLength)
                  const blob = createAppBlob(buf)

                  const afterWrite = () => {
                    offset += slice.length
                    if (offset % (512 * 1024) === 0 || offset >= total) {
                      console.warn('[Cloth导出] FileWriter 进度', offset, '/', total)
                    }
                    writeNext()
                  }

                  writer.onwriteend = afterWrite

                  try {
                    if (offset === 0) {
                      const startWrite = () => {
                        writer.onwriteend = afterWrite
                        if (blob) writer.write(blob)
                        else writer.write(buf)
                      }
                      writer.onwriteend = () => {
                        writer.seek(0)
                        writer.truncate(0)
                        writer.onwriteend = startWrite
                      }
                      writer.seek(0)
                      writer.truncate(0)
                    } else {
                      writer.seek(offset)
                      if (blob) writer.write(blob)
                      else writer.write(buf)
                    }
                  } catch (e) {
                    fail(e)
                  }
                }

                writeNext()
              },
              onFail
            )
          },
          onFail
        )
      },
      onFail
    )
  })
}

/** fs 分块 base64 写 zip */
async function writeZipViaFsBase64(relPath, u8) {
  const fs = getAppFsManager()
  if (!fs) throw new Error('no fs')
  await ensureDocDir()
  const BIN_CHUNK = 384 * 1024
  for (let offset = 0; offset < u8.length; offset += BIN_CHUNK) {
    const slice = u8.subarray(offset, Math.min(offset + BIN_CHUNK, u8.length))
    const b64 = sanitizeBase64ForAndroid(uint8ToBase64Chunk(slice))
    await new Promise((resolve, reject) => {
      const api = offset === 0 ? fs.writeFile.bind(fs) : fs.appendFile.bind(fs)
      api({
        filePath: relPath,
        data: b64,
        encoding: 'base64',
        success: resolve,
        fail: reject
      })
    })
  }
  const size = await verifyRelFileSize(relPath, 10)
  const absPath = plus.io.convertLocalFileSystemURL(relPath)
  console.warn('[Cloth导出] fs.base64 写 zip', absPath, size, 'B')
  return { absPath, size }
}

/** fs 直接写 ArrayBuffer / Uint8Array */
async function writeZipViaFsBuffer(relPath, u8) {
  const fs = getAppFsManager()
  if (!fs) throw new Error('no fs')
  await ensureDocDir()
  const buf = u8.buffer.slice(u8.byteOffset, u8.byteOffset + u8.byteLength)
  await new Promise((resolve, reject) => {
    fs.writeFile({
      filePath: relPath,
      data: u8,
      success: resolve,
      fail: () => {
        fs.writeFile({
          filePath: relPath,
          data: buf,
          success: resolve,
          fail: reject
        })
      }
    })
  })
  const size = await verifyRelFileSize(relPath, 10)
  const absPath = plus.io.convertLocalFileSystemURL(relPath)
  console.warn('[Cloth导出] fs 写 zip', absPath, size, 'B')
  return { absPath, size }
}

/** 校验 _doc 相对路径文件大小（App 上 getFileInfo 可能误报 0，回退 Java File.length） */
function verifyRelFileSize(relPath, minBytes = 10) {
  const absPath =
    typeof plus !== 'undefined' && plus.io ? plus.io.convertLocalFileSystemURL(relPath) : relPath

  // #ifdef APP-PLUS
  if (isAndroid() && absPath && String(absPath).startsWith('/')) {
    const androidSize = getAndroidFileSize(absPath)
    if (androidSize >= minBytes) {
      return Promise.resolve(androidSize)
    }
  }
  // #endif

  const fs = getAppFsManager()
  if (fs) {
    return new Promise((resolve, reject) => {
      fs.getFileInfo({
        filePath: relPath,
        success: (res) => {
          let size = Number(res.size) || 0
          // #ifdef APP-PLUS
          if (isAndroid() && size < minBytes && absPath && String(absPath).startsWith('/')) {
            const androidSize = getAndroidFileSize(absPath)
            if (androidSize >= minBytes) size = androidSize
          }
          // #endif
          if (size < minBytes) {
            reject(new Error(`文件大小异常(${size}B)`))
          } else {
            resolve(size)
          }
        },
        fail: (err) => {
          // #ifdef APP-PLUS
          if (isAndroid() && absPath && String(absPath).startsWith('/')) {
            const androidSize = getAndroidFileSize(absPath)
            if (androidSize >= minBytes) {
              resolve(androidSize)
              return
            }
          }
          // #endif
          reject(err || new Error('无法校验文件'))
        }
      })
    })
  }
  return Promise.resolve(verifyAndroidFile(absPath, minBytes))
}

/** 导出前删除残留 zip（避免误用上次 0B / 半截文件） */
function removeRelZipIfExists(relPath) {
  const fs = getAppFsManager()
  if (fs && typeof fs.unlink === 'function') {
    try {
      fs.unlink({ filePath: relPath })
    } catch (_) {}
  }
  // #ifdef APP-PLUS
  if (isAndroid()) {
    try {
      const abs = plus.io.convertLocalFileSystemURL(relPath)
      deleteAbsFileJava(abs)
      deleteAbsFileJava(`${abs}.b64tmp`)
    } catch (_) {}
  }
  // #endif
}

/** _doc 写入 ZIP（App 仅 plus.io；非 App 可走 fs） */
async function writeZipBytesToRelPath(relPath, data) {
  const u8 = data instanceof Uint8Array ? data : new Uint8Array(data)
  if (!u8.length) throw new Error('导出内容为空')

  removeRelZipIfExists(relPath)
  const tries = []

  try {
    await ensureDocDir()
    return await writeZipViaPlusIoWriter(relPath, u8)
  } catch (e0) {
    tries.push(`plus.io：${formatIoErr(e0)}`)
    console.warn('[Cloth导出] plus.io FileWriter 写 zip 失败', e0)
  }

  const fs = getAppFsManager()
  if (fs) {
    try {
      return await writeZipViaFsBase64(relPath, u8)
    } catch (e1) {
      tries.push(`fs.base64：${formatIoErr(e1)}`)
    }
    try {
      return await writeZipViaFsBuffer(relPath, u8)
    } catch (e2) {
      tries.push(`fs：${formatIoErr(e2)}`)
    }
  }

  throw new Error(`ZIP 写入失败\n${tries.join('\n')}`)
}

function copyAbsFileJava(srcAbs, destAbs) {
  const FileInputStream = plus.android.importClass('java.io.FileInputStream')
  const parentPath = destAbs.replace(/\/[^/]+$/, '')
  if (parentPath) ensureAndroidDir(parentPath)
  const fis = plus.android.newObject('java.io.FileInputStream', srcAbs)
  copyStreamToAbsPath(fis, destAbs)
  return verifyAndroidFile(destAbs, 10)
}

async function writeDocMirrorBytes(fileName, payload) {
  if (typeof payload === 'string') {
    return writeZipBase64ToRelPath(`_doc/${fileName}`, payload)
  }
  return writeZipBytesToRelPath(`_doc/${fileName}`, payload)
}

/** App ZIP 导出：plus.downloader 保存（主路径）→ 失败再回退 plus.io 写 _doc */
async function exportZipAppImpl(fileName, payload) {
  logZipExportPathDiagnostics(fileName)
  const b64 =
    typeof payload === 'string'
      ? payload
      : (() => {
          const u8 = payload instanceof Uint8Array ? payload : new Uint8Array(payload)
          const B64_CHUNK = 384 * 1024
          let s = ''
          for (let i = 0; i < u8.length; i += B64_CHUNK) {
            s += uint8ToBase64Chunk(u8.subarray(i, Math.min(i + B64_CHUNK, u8.length)))
          }
          return sanitizeBase64ForAndroid(s)
        })()
  console.warn('[Cloth导出] payload', 'base64', b64.slice(0, 50), '字符', b64.length)

  try {
    const result = await exportZipBase64ViaPlusDownloader(fileName, b64)
    rememberLastExportPath(result.absPath)
    return result
  } catch (e1) {
    console.warn('[Cloth导出] plus.downloader 导出失败，回退 plus.io FileWriter', e1)
  }

  const docMirror = await writeZipBase64ToRelPath(`_doc/${fileName}`, b64)

  let publicAbs = null
  try {
    copyAbsFileJava(docMirror.absPath, APP_EXPORT_DOWNLOAD_ZIP_FILE)
    publicAbs = APP_EXPORT_DOWNLOAD_ZIP_FILE
    scanMediaFile(publicAbs)
    console.warn('[Cloth导出] zip 已复制到下载目录', publicAbs, docMirror.size, 'B')
  } catch (e2) {
    console.warn('[Cloth导出] 写入下载目录失败，仅保留 _doc', e2)
  }

  const absPath = publicAbs || docMirror.absPath
  rememberLastExportPath(absPath)
  return {
    absPath,
    size: docMirror.size,
    docAbsPath: docMirror.absPath,
    publicAbsPath: publicAbs,
    usedDownloadDir: !!publicAbs
  }
}

function arrayBufferFromBase64(b64) {
  const raw = atob(String(b64 || ''))
  const out = new Uint8Array(raw.length)
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i)
  return out.buffer
}

/** plus.io.FileReader + readAsDataURL 读二进制（App 读 ZIP 更稳） */
function readBinaryViaPlusIoDataUrl(pathOrAbs) {
  return new Promise((resolve, reject) => {
    if (typeof plus === 'undefined' || !plus.io) {
      reject(new Error('plus.io 不可用'))
      return
    }
    let target = String(pathOrAbs || '')
    if (target.startsWith('_')) {
      target = plus.io.convertLocalFileSystemURL(target)
    } else if (target.startsWith('/') && !target.startsWith('file://')) {
      target = `file://${target}`
    }
    plus.io.resolveLocalFileSystemURL(
      target,
      (entry) => {
        entry.file(
          (file) => {
            const reader = new plus.io.FileReader()
            reader.onloadend = (e) => {
              try {
                const raw = e.target && e.target.result ? String(e.target.result) : ''
                const idx = raw.indexOf('base64,')
                const b64 = idx >= 0 ? raw.slice(idx + 7) : raw
                if (!b64 || b64.length < 16) {
                  reject(new Error('plus.io 读取为空'))
                  return
                }
                resolve(arrayBufferFromBase64(b64))
              } catch (err) {
                reject(err)
              }
            }
            reader.onerror = (err) => reject(err || new Error('FileReader 失败'))
            reader.readAsDataURL(file)
          },
          reject
        )
      },
      reject
    )
  })
}

function readBinaryFileAndroid(absPath) {
  const size = getAndroidFileSize(absPath)
  if (!size || size < 10) {
    throw new Error(`文件过小(${size || 0}B)：${absPath}`)
  }
  const FileInputStream = plus.android.importClass('java.io.FileInputStream')
  const Base64 = plus.android.importClass('android.util.Base64')
  const fis = plus.android.newObject('java.io.FileInputStream', absPath)
  const ByteArrayOutputStream = plus.android.importClass('java.io.ByteArrayOutputStream')
  const baos = plus.android.newObject('java.io.ByteArrayOutputStream')
  const bufLen = 65536
  const buffer = plus.android.newObject('byte[]', bufLen)
  let len = Number(androidInvoke(fis, 'read', buffer, 0, bufLen))
  while (len !== -1) {
    if (len > 0) androidInvoke(baos, 'write', buffer, 0, len)
    len = Number(androidInvoke(fis, 'read', buffer, 0, bufLen))
  }
  androidInvoke(fis, 'close')
  const rawBytes = androidInvoke(baos, 'toByteArray')
  const total = Number(androidInvoke(rawBytes, 'length'))
  if (!total || total < 10) {
    throw new Error(`读取为空(${total || 0}B)：${absPath}`)
  }
  const Arrays = plus.android.importClass('java.util.Arrays')
  const out = new Uint8Array(total)
  const CHUNK = 262144
  for (let i = 0; i < total; i += CHUNK) {
    const end = Math.min(i + CHUNK, total)
    const part = androidInvoke(Arrays, 'copyOfRange', rawBytes, i, end)
    const b64 = String(androidInvoke(Base64, 'encodeToString', part, 0))
    const bin = atob(b64)
    for (let j = 0; j < bin.length; j++) out[i + j] = bin.charCodeAt(j)
  }
  return out.buffer
}

function readBinaryByAbsPath(absPath, source) {
  const size = getAndroidFileSize(absPath)
  console.warn('[Cloth导入]', source, absPath, '大小', size, 'B')
  if (!size || size < 10) {
    return Promise.reject(new Error(`文件不存在或过小(${size || 0}B)：${absPath}`))
  }

  const finish = (buffer) => {
    if (!buffer || buffer.byteLength < 10) {
      throw new Error(`读取为空(${buffer ? buffer.byteLength : 0}B)`)
    }
    return buffer
  }

  const fs = getAppFsManager()
  if (fs && absPath.includes('/doc/')) {
    const name = absPath.split('/doc/').pop()
    if (name) {
      return new Promise((resolve, reject) => {
        fs.readFile({
          filePath: `_doc/${name}`,
          success: (res) => {
            try {
              const data = res.data
              if (data instanceof ArrayBuffer) {
                resolve(finish(data))
                return
              }
              if (typeof data === 'string') {
                resolve(finish(arrayBufferFromBase64(data)))
                return
              }
              reject(new Error('无法解析二进制'))
            } catch (e) {
              reject(e)
            }
          },
          fail: () => {
            readBinaryViaPlusIoDataUrl(`_doc/${name}`)
              .then((buf) => resolve(finish(buf)))
              .catch(() => {
                try {
                  resolve(finish(readBinaryFileAndroid(absPath)))
                } catch (e) {
                  reject(e)
                }
              })
          }
        })
      })
    }
  }

  return readBinaryViaPlusIoDataUrl(absPath)
    .then((buf) => finish(buf))
    .catch(() => Promise.resolve(finish(readBinaryFileAndroid(absPath))))
}

async function readBinaryFromPickUriSync(uri, uriStr) {
  const direct = tryResolveUriToAbsPath(uri)
  if (direct && !isVirtualDownloadsUri(uriStr)) {
    try {
      return await readBinaryByAbsPath(direct, 'ZIP选择器→绝对路径')
    } catch (e) {
      console.warn('[Cloth导入] ZIP 绝对路径直读失败', e)
    }
  }

  const absTmp = plus.io.convertLocalFileSystemURL(IMPORT_TMP_ZIP)
  const copied = copyUriToAbsPath(uri, absTmp)
  console.warn('[Cloth导入] ZIP 选择器 → 临时文件', absTmp, '大小', copied, 'B')
  if (!copied || copied < 10) {
    throw new Error(`所选 ZIP 读取失败(${copied || 0}B)`)
  }
  return await readBinaryByAbsPath(absTmp, 'ZIP选择器→临时文件')
}

async function readBinaryFromPickUriAsync(uri) {
  const uriStr = getUriString(uri)
  console.warn('[Cloth导入] 开始读取 ZIP', uriStr)

  const primaryRel = (() => {
    const m = uriStr.match(/\/document\/([^?#]+)/i)
    if (!m) return null
    let docId = ''
    try {
      docId = decodeURIComponent(m[1])
    } catch {
      docId = m[1].replace(/%3A/gi, ':').replace(/%2F/gi, '/')
    }
    return docId.startsWith('primary:') ? docId.slice('primary:'.length) : null
  })()

  if (primaryRel && !isVirtualDownloadsUri(uriStr)) {
    const roots = [
      getAndroidExternalStorageRoot(),
      '/storage/emulated/0',
      '/sdcard',
      '/storage/self/primary'
    ]
    const tried = new Set()
    let lastErr = null
    for (const root of roots) {
      if (!root || tried.has(root)) continue
      tried.add(root)
      const abs = `${root}/${primaryRel}`.replace(/\/+/g, '/')
      try {
        return await readBinaryByAbsPath(abs, `ZIP primary(${root})`)
      } catch (e) {
        lastErr = e
      }
    }
    if (lastErr) console.warn('[Cloth导入] ZIP primary 直读失败', lastErr)
  }

  if (isVirtualDownloadsUri(uriStr)) {
    const absTmp = plus.io.convertLocalFileSystemURL(IMPORT_TMP_ZIP)
    const copied = copyUriToAbsPath(uri, absTmp)
    if (copied >= 10) {
      return readBinaryByAbsPath(absTmp, 'ZIP msf→临时文件')
    }
  }

  return readBinaryFromPickUriSync(uri, uriStr)
}

function readPickedPathToBinary(pathStr) {
  const p = normalizePickedPath(pathStr)
  if (!p) return Promise.reject(new Error('无文件路径'))

  return ensureDocDir().then(() => {
    if (/^content:/i.test(p)) {
      try {
        const Uri = plus.android.importClass('android.net.Uri')
        const uri = Uri.parse(p)
        return readBinaryFromPickUriAsync(uri)
      } catch (e) {
        console.warn('[Cloth导入] ZIP Uri.parse 失败', e)
        return Promise.reject(e)
      }
    }
    if (p.startsWith('/')) {
      return readBinaryByAbsPath(p, 'ZIP plus.io.chooseFile')
    }
    if (p.startsWith('_') && typeof plus !== 'undefined' && plus.io) {
      const abs = plus.io.convertLocalFileSystemURL(p)
      return readBinaryByAbsPath(abs, 'ZIP plus.io.chooseFile')
    }
    return Promise.reject(new Error('无法读取所选 ZIP'))
  })
}

function pickZipViaPlusIoChooseFile() {
  return new Promise((resolve, reject) => {
    if (!isAndroid()) {
      reject(new Error('not android'))
      return
    }
    if (typeof plus === 'undefined' || !plus.io || typeof plus.io.chooseFile !== 'function') {
      reject(new Error('plus.io.chooseFile 不可用'))
      return
    }

    const onSuccess = (e) => {
      const path = extractPathFromPlusIoChooseResult(e)
      if (!path) {
        reject(new Error('未获取到文件路径'))
        return
      }
      readPickedPathToBinary(path)
        .then((buffer) => {
          console.warn('[Cloth导入] ZIP chooseFile 成功', buffer.byteLength, 'B')
          resolve(buffer)
        })
        .catch(reject)
    }

    const onFail = (err) => {
      const msg = err && (err.message || err.msg) ? String(err.message || err.msg) : ''
      if (msg.includes('cancel') || msg.includes('取消')) {
        reject(new Error('cancel'))
        return
      }
      reject(err || new Error('选择文件失败'))
    }

    const opts = {
      title: '选择 wardrobe_export.zip',
      filetypes: ['zip'],
      multiple: false
    }

    try {
      plus.io.chooseFile(
        opts,
        (res) => {
          if (res && typeof res.code === 'number' && res.code !== 0) {
            onFail(res)
            return
          }
          onSuccess(res)
        },
        (err) => onFail(err)
      )
    } catch (e1) {
      try {
        plus.io.chooseFile({ multiple: false }, onSuccess, onFail)
      } catch (e2) {
        reject(e2)
      }
    }
  })
}

function pickZipTextAndroid() {
  return new Promise((resolve, reject) => {
    if (!isAndroid()) {
      reject(new Error('not android'))
      return
    }

    const main = plus.android.runtimeMainActivity()
    const Intent = plus.android.importClass('android.content.Intent')
    const intent = new Intent(Intent.ACTION_GET_CONTENT)
    androidInvoke(intent, 'setType', 'application/zip')
    androidInvoke(intent, 'addCategory', Intent.CATEGORY_OPENABLE)
    try {
      androidInvoke(intent, 'addFlags', Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch {
      androidInvoke(intent, 'addFlags', 1)
    }

    let chooser = intent
    try {
      if (typeof Intent.createChooser === 'function') {
        chooser = Intent.createChooser(intent, '选择 wardrobe_export.zip')
      }
    } catch {
      /* ignore */
    }

    let settled = false
    const finish = (fn) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      fn()
    }

    const timer = setTimeout(() => {
      finish(() => reject(new Error('打开文件选择器超时')))
    }, 90000)

    const prevHandler = main.onActivityResult
    main.onActivityResult = function (requestCode, resultCode, data) {
      if (requestCode !== PICK_REQUEST_CODE + 1) {
        if (typeof prevHandler === 'function') prevHandler(requestCode, resultCode, data)
        return
      }
      main.onActivityResult = prevHandler
      if (resultCode !== -1 || !data) {
        finish(() => reject(new Error('cancel')))
        return
      }
      const uri = resolvePickUri(data)
      if (!uri) {
        finish(() => reject(new Error('未获取到文件')))
        return
      }
      setTimeout(() => {
        ensureDocDir()
          .then(() => readBinaryFromPickUriAsync(uri))
          .then((buffer) => finish(() => resolve(buffer)))
          .catch((e) => finish(() => reject(e)))
      }, 80)
    }

    setTimeout(() => {
      try {
        androidInvoke(main, 'startActivityForResult', chooser, PICK_REQUEST_CODE + 1)
      } catch (e) {
        main.onActivityResult = prevHandler
        finish(() => reject(e))
      }
    }, 120)
  })
}

function pickZipFileAppImpl() {
  if (isAndroid()) {
    return pickZipViaPlusIoChooseFile().catch((err) => {
      if (err && err.message === 'cancel') throw err
      console.warn('[Cloth导入] ZIP plus.io 失败，试 Intent', err)
      return pickZipTextAndroid()
    })
  }
  return Promise.reject(new Error('not android'))
}

function readAppLocalZipBackupImpl(fileName = 'wardrobe_export.zip') {
  return (async () => {
    if (!isAndroid()) {
      throw new Error('仅支持 Android App')
    }
    const tries = []
    const paths = [`_doc/${fileName}`, APP_EXPORT_DOWNLOAD_ZIP_FILE]
    for (const p of paths) {
      try {
        const buffer = await readBinaryByAbsPath(
          p.startsWith('_') ? plus.io.convertLocalFileSystemURL(p) : p,
          `ZIP 备份 ${p}`
        )
        if (buffer && buffer.byteLength >= 10) return buffer
      } catch (e) {
        tries.push(`${p}：${e && e.message ? e.message : e}`)
      }
    }
    const last = uni.getStorageSync(LAST_EXPORT_PATH_KEY)
    if (last && String(last).endsWith('.zip')) {
      try {
        const buffer = await readBinaryByAbsPath(String(last), 'ZIP 上次导出')
        if (buffer && buffer.byteLength >= 10) return buffer
      } catch (e) {
        tries.push(`上次导出：${e && e.message ? e.message : e}`)
      }
    }
    throw new Error(`无法读取 ZIP 备份。\n\n${tries.join('\n')}\n\n请先导出一次 ZIP。`)
  })()
}

function ensureAndroidDir(dirPath) {
  const File = plus.android.importClass('java.io.File')
  const dir = plus.android.newObject('java.io.File', dirPath)
  if (!androidInvoke(dir, 'exists')) {
    androidInvoke(dir, 'mkdirs')
  }
}

/** 用 Java 校验绝对路径文件大小（不依赖 getFileSystemManager） */
function verifyAndroidFile(absPath, minBytes = 10) {
  const File = plus.android.importClass('java.io.File')
  const f = plus.android.newObject('java.io.File', absPath)
  if (!androidInvoke(f, 'exists')) {
    throw new Error('文件不存在')
  }
  const size = Number(androidInvoke(f, 'length'))
  if (!size || size < minBytes) {
    throw new Error(`文件大小异常(${size || 0}B)`)
  }
  return size
}

// ========== 导出（已验收稳定，勿改）见 .cursor/rules/import-export.mdc ==========

/** 应用内 _doc 备份（不读回校验，避免大文件重复读取） */
async function writeDocMirror(fileName, content) {
  const relPath = `_doc/${fileName}`
  let absPath
  try {
    absPath = await writeBigFileUtf8(relPath, content)
  } catch (e) {
    console.warn('[Cloth导出] fs 写 _doc 失败，改 Java', e)
    absPath = writeTextFileAndroid(relPath, content)
  }
  const size = verifyAndroidFile(absPath, 10)
  console.warn('[Cloth导出] _doc 备份', absPath, size, 'B')
  return { absPath, size }
}

/**
 * App 导出：_doc 备份 + 尽量写入「下载/wardrobe_export.json」（不弹选位置）
 */
async function exportJsonApp(fileName, content) {
  const docMirror = await writeDocMirror(fileName, content)

  let publicAbs = null
  try {
    const dir = APP_EXPORT_DOWNLOAD_FILE.replace(/\/[^/]+$/, '')
    ensureAndroidDir(dir)
    writeTextToAbsPath(APP_EXPORT_DOWNLOAD_FILE, content)
    publicAbs = APP_EXPORT_DOWNLOAD_FILE
    verifyAndroidFile(publicAbs, 10)
    scanMediaFile(publicAbs)
    console.warn('[Cloth导出] 下载目录', publicAbs, docMirror.size, 'B')
  } catch (e) {
    console.warn('[Cloth导出] 写入下载目录失败，仅保留 _doc', e)
  }

  const absPath = publicAbs || docMirror.absPath
  rememberLastExportPath(absPath)

  return {
    absPath,
    size: docMirror.size,
    docAbsPath: docMirror.absPath,
    publicAbsPath: publicAbs,
    usedDownloadDir: !!publicAbs
  }
}

function getAndroidFileSize(absPath) {
  const File = plus.android.importClass('java.io.File')
  const f = plus.android.newObject('java.io.File', absPath)
  if (!androidInvoke(f, 'exists')) return 0
  return Number(androidInvoke(f, 'length'))
}

/** 按行读取（适合 BufferedWriter 写出的整行 JSON） */
function readTextFileAndroidLines(absPath) {
  const FileInputStream = plus.android.importClass('java.io.FileInputStream')
  const InputStreamReader = plus.android.importClass('java.io.InputStreamReader')
  const BufferedReader = plus.android.importClass('java.io.BufferedReader')
  const fis = plus.android.newObject('java.io.FileInputStream', absPath)
  const isr = plus.android.newObject('java.io.InputStreamReader', fis, 'UTF-8')
  const br = plus.android.newObject('java.io.BufferedReader', isr)
  const StringBuilder = plus.android.importClass('java.lang.StringBuilder')
  const sb = plus.android.newObject('java.lang.StringBuilder')
  let line = androidInvoke(br, 'readLine')
  let lines = 0
  while (line != null) {
    lines++
    androidInvoke(sb, 'append', line)
    line = androidInvoke(br, 'readLine')
  }
  androidInvoke(br, 'close')
  const result = String(androidInvoke(sb, 'toString'))
  console.warn('[Cloth导入] readLine', absPath, '行数', lines, '字符', result.length)
  return result
}

/** 从绝对路径读取 UTF-8 文本 */
function readTextFileAndroid(absPath) {
  const size = getAndroidFileSize(absPath)
  console.warn('[Cloth导入] 准备读取', absPath, '文件大小', size, 'B')
  if (!size || size < 10) {
    throw new Error(`文件仅 ${size || 0}B（可能未导出成功）`)
  }

  let text = ''
  try {
    text = readTextFileAndroidLines(absPath)
  } catch (e) {
    console.warn('[Cloth导入] readLine 失败', e)
  }

  if (!text || text.length < 10) {
    try {
      const FileInputStream = plus.android.importClass('java.io.FileInputStream')
      const fis = plus.android.newObject('java.io.FileInputStream', absPath)
      text = readStreamAsUtf8(fis)
      console.warn('[Cloth导入] 字节流读取字符', text ? text.length : 0)
    } catch (e2) {
      console.warn('[Cloth导入] 字节流失败', e2)
    }
  }

  if (!text || text.length < 10) {
    throw new Error(`读出 ${text ? text.length : 0} 字符，文件 ${size}B（路径：${absPath}）`)
  }
  return text
}

function pruneLastExportPathIfMissing() {
  try {
    const last = uni.getStorageSync(LAST_EXPORT_PATH_KEY)
    if (!last) return
    const File = plus.android.importClass('java.io.File')
    const f = plus.android.newObject('java.io.File', String(last))
    if (!androidInvoke(f, 'exists')) {
      uni.removeStorageSync(LAST_EXPORT_PATH_KEY)
      console.warn('[Cloth导入] 已清除无效「上次导出」记录', last)
    }
  } catch {
    /* ignore */
  }
}

/** 读取 _doc 备份文本 */
async function readDocExportText(fileName, srcAbsPath) {
  const fs = typeof uni.getFileSystemManager === 'function' ? uni.getFileSystemManager() : null
  if (fs) {
    const relPath = docRelPath(fileName)
    const text = await new Promise((resolve, reject) => {
      fs.readFile({
        filePath: relPath,
        encoding: 'utf8',
        success: (res) => resolve(typeof res.data === 'string' ? res.data : ''),
        fail: (err) => reject(err || new Error('读取备份失败'))
      })
    })
    if (text && text.length >= 10) return text
  }

  let path = srcAbsPath
  if (!path && typeof plus !== 'undefined' && plus.io) {
    path = plus.io.convertLocalFileSystemURL(docRelPath(fileName))
  }
  if (!path) {
    throw new Error('无法定位备份文件路径')
  }
  return readBigFileUtf8(path)
}

function scanMediaFile(absPath) {
  try {
    const MediaScannerConnection = plus.android.importClass(
      'android.media.MediaScannerConnection'
    )
    const main = plus.android.runtimeMainActivity()
    androidInvoke(MediaScannerConnection, 'scanFile', main, [absPath], null, null)
  } catch (e) {
    console.warn('scanMediaFile', e)
  }
}

function resolvePickUri(data) {
  if (!data) return null
  let uri = androidInvoke(data, 'getData')
  if (uri) return uri
  try {
    const clip = androidInvoke(data, 'getClipData')
    if (clip && Number(androidInvoke(clip, 'getItemCount')) > 0) {
      const item = androidInvoke(clip, 'getItemAt', 0)
      if (item) return androidInvoke(item, 'getUri')
    }
  } catch (e) {
    console.warn('resolvePickUri', e)
  }
  return null
}

function validateImportJsonText(text) {
  const trim = String(text || '').trim()
  if (!trim.startsWith('{') && !trim.startsWith('[')) {
    throw new Error('不是有效的 JSON 文件')
  }
  return trim
}

/** plus.io：把 content 条目复制到 _doc 再读（Downloads msf: URI） */
function readJsonViaPlusIoCopy(uriStr) {
  return new Promise((resolve, reject) => {
    if (typeof plus === 'undefined' || !plus.io) {
      reject(new Error('plus.io 不可用'))
      return
    }
    const destName = '_import_pick.json'
    plus.io.resolveLocalFileSystemURL(
      uriStr,
      (srcEntry) => {
        plus.io.requestFileSystem(
          plus.io.PRIVATE_DOC,
          (fs) => {
            srcEntry.copyTo(
              fs.root,
              destName,
              () => {
                try {
                  const abs = plus.io.convertLocalFileSystemURL(`_doc/${destName}`)
                  readBigFileUtf8(abs)
                    .then((text) => {
                      console.warn('[Cloth导入] plus.io copyTo 成功', abs, text.length)
                      resolve(text)
                    })
                    .catch(reject)
                  return
                } catch (e) {
                  reject(e)
                }
              },
              reject
            )
          },
          reject
        )
      },
      reject
    )
  })
}

/** plus.io FileReader 直读（部分 URI 不支持） */
function readJsonViaPlusIoReader(uriStr) {
  return new Promise((resolve, reject) => {
    if (typeof plus === 'undefined' || !plus.io) {
      reject(new Error('plus.io 不可用'))
      return
    }
    plus.io.resolveLocalFileSystemURL(
      uriStr,
      (entry) => {
        entry.file(
          (file) => {
            const reader = new plus.io.FileReader()
            reader.onloadend = (evt) => {
              const data = evt.target && evt.target.result
              if (typeof data === 'string' && data.trim().length >= 10) {
                console.warn('[Cloth导入] plus.io FileReader 成功', data.length)
                resolve(data)
              } else {
                reject(new Error('plus.io 内容为空'))
              }
            }
            reader.onerror = (err) => reject(err || new Error('FileReader 失败'))
            reader.readAsText(file, 'utf-8')
          },
          reject
        )
      },
      reject
    )
  })
}

/** 仅 Downloads 虚拟 ID（msf:）无法还原路径，必须走流复制 */
function isVirtualDownloadsUri(uriStr) {
  return /downloads\.documents/i.test(uriStr) && /msf%3A|msf:/i.test(uriStr)
}

async function readJsonFromPickUriSync(uri, uriStr) {
  const direct = tryResolveUriToAbsPath(uri)
  if (direct && !isVirtualDownloadsUri(uriStr)) {
    try {
      return await readJsonByAbsPath(direct, '选择器→绝对路径')
    } catch (e) {
      console.warn('[Cloth导入] 绝对路径直读失败', e)
    }
  }

  const absTmp = plus.io.convertLocalFileSystemURL(IMPORT_TMP)
  const size = copyUriToAbsPath(uri, absTmp)
  console.warn('[Cloth导入] 文件选择器 → 临时文件', absTmp, '大小', size, 'B')
  if (!size || size < 10) {
    throw new Error(
      `所选文件读取失败(${size || 0}B)。请用「选择 JSON 文件」重新选择 wardrobe_export.json`
    )
  }
  return await readJsonByAbsPath(absTmp, '选择器→临时文件')
}

/** 异步读取选择器 URI */
function readJsonFromPickUriAsync(uri) {
  const uriStr = getUriString(uri)
  console.warn('[Cloth导入] 开始读取', uriStr)

  const primaryRel = (() => {
    const m = uriStr.match(/\/document\/([^?#]+)/i)
    if (!m) return null
    let docId = ''
    try {
      docId = decodeURIComponent(m[1])
    } catch {
      docId = m[1].replace(/%3A/gi, ':').replace(/%2F/gi, '/')
    }
    return docId.startsWith('primary:') ? docId.slice('primary:'.length) : null
  })()

  if (primaryRel && !isVirtualDownloadsUri(uriStr)) {
    return readJsonByPrimaryRel(primaryRel, '选择器→primary直读')
  }

  if (isVirtualDownloadsUri(uriStr)) {
    return readJsonFromContentUri(uri, uriStr)
  }

  return readJsonFromPickUriSync(uri, uriStr)
}

function normalizePickedPath(raw) {
  let p = String(raw || '').trim()
  if (!p) return ''
  p = p.replace(/^content;\/\//i, 'content://')
  if (p.startsWith('file://') && typeof plus !== 'undefined' && plus.io) {
    try {
      p = plus.io.convertLocalFileSystemURL(p)
    } catch {
      /* 保持原路径 */
    }
  }
  return p
}

function extractPathFromPlusIoChooseResult(e) {
  if (e == null) return ''
  if (typeof e === 'string') return e
  if (Array.isArray(e.files) && e.files.length) {
    const f0 = e.files[0]
    if (typeof f0 === 'string') return f0
    if (f0 && (f0.path || f0.fullPath)) return String(f0.path || f0.fullPath)
  }
  if (e.path) return String(e.path)
  if (e.tempFilePaths && e.tempFilePaths[0]) return String(e.tempFilePaths[0])
  return ''
}

/** 读取 plus.io.chooseFile / 选择器返回的路径或 URI */
function readPickedPathToJsonText(pathStr) {
  const p = normalizePickedPath(pathStr)
  if (!p) return Promise.reject(new Error('无文件路径'))

  return ensureDocDir().then(() => {
    if (/^content:/i.test(p)) {
      try {
        const Uri = plus.android.importClass('android.net.Uri')
        const uri = Uri.parse(p)
        return readJsonFromPickUriAsync(uri)
      } catch (e) {
        console.warn('[Cloth导入] Uri.parse 失败，改 plus.io 读', e)
        return readJsonViaPlusIoCopy(p)
      }
    }
    if (p.startsWith('/')) {
      return Promise.resolve(readJsonByAbsPath(p, 'plus.io.chooseFile'))
    }
    if (p.startsWith('_') && typeof plus !== 'undefined' && plus.io) {
      const abs = plus.io.convertLocalFileSystemURL(p)
      return Promise.resolve(readJsonByAbsPath(abs, 'plus.io.chooseFile'))
    }
    return readJsonViaPlusIoCopy(p)
  })
}

/**
 * 5+ Runtime：plus.io.chooseFile（优先于 Intent，利于部分正式包）
 * 兼容两参数 / 三参数回调写法
 */
export function pickJsonViaPlusIoChooseFile() {
  return new Promise((resolve, reject) => {
    if (!isAndroid()) {
      reject(new Error('not android'))
      return
    }
    if (typeof plus === 'undefined' || !plus.io || typeof plus.io.chooseFile !== 'function') {
      reject(new Error('plus.io.chooseFile 不可用'))
      return
    }

    const onSuccess = (e) => {
      console.warn('[Cloth导入] plus.io.chooseFile 回调', e)
      const path = extractPathFromPlusIoChooseResult(e)
      if (!path) {
        reject(new Error('未获取到文件路径'))
        return
      }
      readPickedPathToJsonText(path)
        .then((text) => {
          console.warn('[Cloth导入] plus.io.chooseFile 读取成功，长度', text.length)
          resolve(text)
        })
        .catch((e) => {
          reject(e)
        })
    }

    const onFail = (err) => {
      const msg = err && (err.message || err.msg) ? String(err.message || err.msg) : ''
      if (msg.includes('cancel') || msg.includes('取消')) {
        reject(new Error('cancel'))
        return
      }
      reject(err || new Error('选择文件失败'))
    }

    const opts = {
      title: '选择 wardrobe_export.json',
      filetypes: ['json'],
      multiple: false
    }

    try {
      plus.io.chooseFile(
        opts,
        (res) => {
          if (res && typeof res.code === 'number' && res.code !== 0) {
            onFail(res)
            return
          }
          onSuccess(res)
        },
        (err) => {
          onFail(err)
        }
      )
    } catch (e1) {
      console.warn('[Cloth导入] chooseFile 两参数失败，试三参数', e1)
      try {
        plus.io.chooseFile({ multiple: false }, onSuccess, onFail)
      } catch (e2) {
        reject(e2)
      }
    }
  })
}
// #endif

/** 校验写入后的文件大小 */
export function verifyDocFile(relPath, minBytes = 10) {
  const fs = uni.getFileSystemManager && uni.getFileSystemManager()
  if (!fs) return Promise.resolve(0)
  return new Promise((resolve, reject) => {
    fs.getFileInfo({
      filePath: relPath,
      success: (res) => {
        if (!res.size || res.size < minBytes) {
          reject(new Error(`文件大小异常(${res.size || 0}B)`))
        } else {
          resolve(res.size)
        }
      },
      fail: (err) => reject(err || new Error('无法校验文件'))
    })
  })
}

export function ensureDocDir() {
  const fs = uni.getFileSystemManager && uni.getFileSystemManager()
  if (!fs) return Promise.resolve()
  return new Promise((resolve) => {
    fs.access({
      path: '_doc/',
      success: () => resolve(),
      fail: () => {
        fs.mkdir({
          dirPath: '_doc/',
          recursive: true,
          success: () => resolve(),
          fail: () => resolve()
        })
      }
    })
  })
}

export async function writeJsonToDoc(fileName, jsonText) {
  const content = String(jsonText || '')
  if (!content.length) {
    throw new Error('导出内容为空')
  }

  await ensureDocDir()
  const relPath = `_doc/${fileName}`

  // #ifdef APP-PLUS
  if (isAndroid()) {
    return await exportJsonApp(fileName, content)
  }
  // #endif

  try {
    const abs = await writeBigFileUtf8(relPath, content)
    return abs || relPath
  } catch (e) {
    console.warn('[Cloth导出] writeBigFileUtf8 失败', e)
  }

  const fs = uni.getFileSystemManager && uni.getFileSystemManager()
  if (!fs) {
    throw new Error('当前环境无法写入文件')
  }
  await new Promise((resolve, reject) => {
    fs.writeFile({
      filePath: relPath,
      data: content,
      encoding: 'utf8',
      success: resolve,
      fail: reject
    })
  })
  await verifyDocFile(relPath, 10)

  // #ifdef APP-PLUS
  if (typeof plus !== 'undefined' && plus.io) {
    return plus.io.convertLocalFileSystemURL(relPath)
  }
  // #endif
  return relPath
}

/** _doc 相对路径 */
export function docRelPath(fileName) {
  return `_doc/${fileName}`
}

/** 记录上次导出路径（同机导入用） */
export function rememberLastExportPath(absPath) {
  if (!absPath) return
  try {
    uni.setStorageSync(LAST_EXPORT_PATH_KEY, absPath)
    console.warn('[Cloth导出] 已记录路径', absPath)
  } catch (e) {
    console.warn('[Cloth导出] 记录路径失败', e)
  }
}

/** 读取应用内 _doc 备份（及上次导出记录） */
export function readAppLocalBackup(fileName = 'wardrobe_export.json') {
  // #ifdef APP-PLUS
  return (async () => {
    if (!isAndroid()) {
      throw new Error('仅支持 Android App')
    }

    const tries = []
    try {
      const text = await readBigFileUtf8(`_doc/${fileName}`)
      console.warn('[Cloth导入] _doc 备份读取成功', text.length)
      return text
    } catch (e) {
      tries.push(`应用内 _doc：${e && e.message ? e.message : e}`)
    }

    try {
      const publicSize = getAndroidFileSize(APP_EXPORT_DOWNLOAD_FILE)
      if (publicSize >= 10) {
        const text = await readBigFileUtf8(APP_EXPORT_DOWNLOAD_FILE)
        console.warn('[Cloth导入] 下载目录备份', text.length)
        return text
      }
    } catch (e1) {
      tries.push(`下载目录：${e1 && e1.message ? e1.message : e1}`)
    }

    try {
      const last = uni.getStorageSync(LAST_EXPORT_PATH_KEY)
      if (last) {
        const text = await readBigFileUtf8(String(last))
        console.warn('[Cloth导入] 上次导出路径', text.length)
        return text
      }
    } catch (e2) {
      tries.push(`上次导出：${e2 && e2.message ? e2.message : e2}`)
    }

    throw new Error(
      `无法读取应用内备份。\n\n${tries.join('\n')}\n\n请先导出一次，或使用「选择 JSON 文件」从文件管理器导入。`
    )
  })()
  // #endif
  // #ifndef APP-PLUS
  return Promise.reject(new Error('not app'))
  // #endif
}

/** @deprecated 使用 readAppLocalBackup */
export function readBackupJsonFromExportDir(fileName) {
  return readAppLocalBackup(fileName)
}

/** App 导出完成：记录路径供同机导入 */
export function finishExportForApp(exportAbsPath) {
  rememberLastExportPath(exportAbsPath)
  return exportAbsPath
}

export function pickJsonTextAndroid() {
  return new Promise((resolve, reject) => {
    // #ifdef APP-PLUS
    if (!isAndroid()) {
      reject(new Error('not android'))
      return
    }

    const main = plus.android.runtimeMainActivity()
    const Intent = plus.android.importClass('android.content.Intent')
    const intent = new Intent(Intent.ACTION_GET_CONTENT)
    androidInvoke(intent, 'setType', '*/*')
    androidInvoke(intent, 'addCategory', Intent.CATEGORY_OPENABLE)
    try {
      const flags =
        Intent.FLAG_GRANT_READ_URI_PERMISSION |
        (Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION || 0)
      androidInvoke(intent, 'addFlags', flags)
    } catch {
      androidInvoke(intent, 'addFlags', 1)
    }

    let chooser = intent
    try {
      if (typeof Intent.createChooser === 'function') {
        chooser = Intent.createChooser(intent, '选择 wardrobe_export.json')
      }
    } catch {
      /* 无 Chooser 则用原 Intent */
    }

    let settled = false
    const finish = (fn) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      fn()
    }

    const timer = setTimeout(() => {
      finish(() => reject(new Error('打开文件选择器超时，请重试或使用「粘贴 JSON」')))
    }, 90000)

    const prevHandler = main.onActivityResult
    main.onActivityResult = function (requestCode, resultCode, data) {
      if (requestCode !== PICK_REQUEST_CODE) {
        if (typeof prevHandler === 'function') {
          prevHandler(requestCode, resultCode, data)
        }
        return
      }

      main.onActivityResult = prevHandler

      if (resultCode !== -1 || !data) {
        finish(() => reject(new Error('cancel')))
        return
      }

      const uri = resolvePickUri(data)
      if (!uri) {
        finish(() => reject(new Error('未获取到文件')))
        return
      }

      try {
        const takeFlags =
          Intent.FLAG_GRANT_READ_URI_PERMISSION |
          (Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION || 0)
        const resolver = androidInvoke(main, 'getContentResolver')
        androidInvoke(resolver, 'takePersistableUriPermission', uri, takeFlags)
      } catch {
        /* 部分 URI 不支持持久授权 */
      }

      setTimeout(() => {
        ensureDocDir()
          .then(() => readJsonFromPickUriAsync(uri))
          .then((text) => finish(() => resolve(text)))
          .catch((e) => {
            console.error('readJsonFromPickUriAsync', e)
            finish(() => reject(e))
          })
      }, 80)
    }

    const launch = () => {
      try {
        console.warn('[Cloth导入] 打开系统文件选择器')
        androidInvoke(main, 'startActivityForResult', chooser, PICK_REQUEST_CODE)
      } catch (e) {
        main.onActivityResult = prevHandler
        console.error('[Cloth导入] startActivityForResult', e)
        finish(() => reject(e))
      }
    }

    setTimeout(launch, 120)
    // #endif
    // #ifndef APP-PLUS
    reject(new Error('not app'))
    // #endif
  })
}

/**
 * App 选 JSON：plus.io 文件选择器优先（基座常用），失败再用系统 Intent
 */
export function pickJsonFileApp() {
  // #ifdef APP-PLUS
  if (isAndroid()) {
    return pickJsonViaPlusIoChooseFile().catch((err) => {
      if (err && err.message === 'cancel') throw err
      console.warn('[Cloth导入] plus.io 选文件失败，试系统 Intent', err)
      return pickJsonTextAndroid().catch((err2) => {
        if (err2 && err2.message === 'cancel') throw err2
        console.warn('[Cloth导入] Intent 失败，试 uni.chooseFile', err2)
        return pickJsonColleague()
      })
    })
  }
  // #endif
  return pickJsonColleague()
}

function pickJsonColleague() {
  return new Promise((resolve, reject) => {
    if (typeof uni.chooseFile !== 'function') {
      reject(new Error('当前环境不支持选择文件，请用「粘贴 JSON」'))
      return
    }

    const onSuccess = (res) => {
      const tf = res.tempFiles && res.tempFiles[0]
      const path =
        (res.tempFilePaths && res.tempFilePaths[0]) ||
        (tf && (tf.path || tf.filePath || tf.tempFilePath))
      if (!path) {
        reject(new Error('未获取到文件路径'))
        return
      }
      readBigFileUtf8(path)
        .then((text) => {
          console.warn('[Cloth导入] chooseFile + readFile', path, '长度', text.length)
          resolve(text)
        })
        .catch(reject)
    }

    const onFail = (err) => {
      const msg = err && (err.errMsg || err.message) ? String(err.errMsg || err.message) : ''
      if (msg.includes('cancel') || msg.includes('取消')) {
        reject(new Error('cancel'))
        return
      }
      reject(err || new Error('选择文件失败'))
    }

    uni.chooseFile({
      count: 1,
      extension: ['.json'],
      success: onSuccess,
      fail: onFail
    })
  })
}

/** App ZIP 导出（模块级导出，兼容 H5 编译） */
export async function exportZipApp(fileName, payload) {
  // #ifdef APP-PLUS
  return exportZipAppImpl(fileName, payload)
  // #endif
  throw new Error('ZIP 导出仅支持 App')
}

/** App 选 ZIP 文件 */
export function pickZipFileApp() {
  // #ifdef APP-PLUS
  return pickZipFileAppImpl()
  // #endif
  return Promise.reject(new Error('not app'))
}

/** 读取应用内 ZIP 备份 */
export function readAppLocalZipBackup(fileName = 'wardrobe_export.zip') {
  // #ifdef APP-PLUS
  return readAppLocalZipBackupImpl(fileName)
  // #endif
  return Promise.reject(new Error('not app'))
}
