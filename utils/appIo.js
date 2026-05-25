/**
 * App 端文件导入/导出（Android 原生读写）
 */
import { APP_EXPORT_DOWNLOAD_FILE, LAST_EXPORT_PATH_KEY } from './constants.js'

export { LAST_EXPORT_PATH_KEY }

const PICK_REQUEST_CODE = 90262
const IMPORT_TMP = '_doc/_import_tmp.json'
const WRITE_CHUNK = 50000

function getAppFsManager() {
  return typeof uni.getFileSystemManager === 'function' ? uni.getFileSystemManager() : null
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
