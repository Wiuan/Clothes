/**
 * 从 衣服.xlsx 生成 wardrobe_export.json（version 3，images 为空）
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const XLSX_PATHS = [
  path.join(process.env.USERPROFILE || '', 'Desktop', '衣服.xlsx'),
  path.join(__dirname, '..', '衣服.xlsx')
]

const CATEGORY_MAP = {
  上衣打底: '上衣',
  上衣外套: '长款',
  下装: '下装',
  裙子: '长款',
  运动: '运动'
}

const SIZE_KEY_MAP = {
  衣长: '衣长',
  胸围: '胸围',
  肩宽: '肩宽',
  袖长: '袖长',
  领宽: '领宽',
  下摆: '下摆',
  后片长: '后片长',
  前衣长: '衣长',
  后衣长: '后片长',
  裤长: '裤长裙长',
  裙长: '裤长裙长',
  腰围: '腰围',
  臀围: '臀围',
  大腿围: '大腿围',
  前裆: '前档',
  前档: '前档',
  后档: '后档',
  后裆: '后档',
  脚口: '脚口',
  裤脚: '脚口',
  裤脚口: '脚口',
  袖口: '袖长'
}

function colToNum(col) {
  let n = 0
  for (const ch of col) n = n * 26 + (ch.charCodeAt(0) - 64)
  return n
}

function parseCellRef(ref) {
  const m = ref.match(/^([A-Z]+)(\d+)$/)
  return m ? { col: colToNum(m[1]), row: parseInt(m[2], 10) } : null
}

function readSharedStrings(xml) {
  const out = []
  const siRe = /<si>([\s\S]*?)<\/si>/g
  let m
  while ((m = siRe.exec(xml))) {
    const block = m[1]
    const parts = [...block.matchAll(/<t[^>]*>([^<]*)<\/t>/g)].map((x) => x[1])
    out.push(parts.join(''))
  }
  return out
}

function readSheetCells(xml, shared) {
  const cells = new Map()
  const rowRe = /<row r="(\d+)"[^>]*>([\s\S]*?)<\/row>/g
  let rm
  while ((rm = rowRe.exec(xml))) {
    const row = parseInt(rm[1], 10)
    const rowXml = rm[2]
    const cRe = /<c r="([A-Z]+\d+)"[^>]*(?: t="s")?[^>]*><v>([^<]*)<\/v>/g
    let cm
    while ((cm = cRe.exec(rowXml))) {
      const ref = parseCellRef(cm[1])
      if (!ref) continue
      const isStr = /t="s"/.test(cm[0])
      const raw = cm[2]
      const value = isStr ? shared[parseInt(raw, 10)] ?? '' : raw
      if (value) cells.set(`${ref.col},${ref.row}`, value.trim())
    }
  }
  return cells
}

function buildHeaderMap(row1Cells) {
  const seasons = []
  const colors = []
  for (let col = 2; col <= 60; col++) {
    const s = row1Cells.get(`${col},1`)
    if (s && ['夏', '春秋', '冬'].includes(s)) seasons[col] = s
    if (s && ['红', '黄', '蓝', '黑', '白', '绿', '紫', '棕', '灰'].includes(s)) {
      colors[col] = s
    }
  }
  return { seasons, colors }
}

function getColorForCol(col, colors, seasons) {
  if (colors[col]) return colors[col]
  for (let c = col; c >= 2; c--) {
    if (colors[c]) return colors[c]
  }
  return '其他'
}

function getSeasonForCol(col, seasons) {
  if (seasons[col]) return seasons[col]
  for (let c = col; c >= 2; c--) {
    if (seasons[c]) return seasons[c]
  }
  return '夏'
}

function parseMeasurements(text, type) {
  const sizes = {}
  const noteParts = []
  let namePrefix = ''

  let body = String(text || '').trim()
  if (!body || body === '扔了') return { skip: true }

  const paren = body.match(/^（([^）]+)）/)
  if (paren) {
    namePrefix = paren[1]
    body = body.slice(paren[0].length).trim()
  }

  const segments = body.split(/[。；;]/).filter(Boolean)
  for (const seg of segments) {
    const s = seg.trim()
    if (!s) continue
    const pairs = [...s.matchAll(/([\u4e00-\u9fa5/（）()]+?)(\d+(?:\.\d+)?(?:\/\d+(?:\.\d+)?)?)/g)]
    if (!pairs.length) {
      if (/[\u4e00-\u9fa5]/.test(s) && !/^\d/.test(s)) noteParts.push(s)
      continue
    }
    for (const [, label, val] of pairs) {
      const key = label.replace(/\s/g, '')
      const mapped = SIZE_KEY_MAP[key]
      if (mapped) {
        sizes[mapped] = val
      } else if (key && !['洗', '棉'].some((x) => key.includes(x))) {
        noteParts.push(`${key}${val}`)
      }
    }
    const tailNote = s.replace(/[\u4e00-\u9fa5/（）()]+?\d+(?:\.\d+)?(?:\/\d+(?:\.\d+)?)?/g, '').trim()
    if (tailNote && /[\u4e00-\u9fa5A-Za-z]/.test(tailNote)) noteParts.push(tailNote)
  }

  return { sizes, noteParts, namePrefix, skip: false }
}

function inferName(season, color, categoryLabel, index, namePrefix, sizes) {
  const len = sizes['衣长'] || sizes['裤长裙长'] || ''
  const parts = [season, color, categoryLabel]
  if (len) parts.push(len.includes('/') ? `长${len}` : `${len}cm`)
  if (namePrefix) parts.push(`(${namePrefix})`)
  if (index > 1) parts.push(`#${index}`)
  return parts.join('·')
}

function clothItem({ id, season, color, type, sizes, note, categoryLabel, index, namePrefix }) {
  return {
    id,
    name: inferName(season, color, categoryLabel, index, namePrefix, sizes),
    colors: [color],
    color,
    colorPreset: color,
    season,
    type,
    status: 'active',
    discardedAt: null,
    purchaseDate: '',
    purchasePrice: '',
    material: '',
    note: note || '',
    tempMin: null,
    tempMax: null,
    sizes,
    createdAt: Date.now() + seq,
    imageRef: `img_${id}`
  }
}

const xlsxPath = XLSX_PATHS.find((p) => fs.existsSync(p))
if (!xlsxPath) {
  console.error('未找到 衣服.xlsx')
  process.exit(1)
}

import { execSync } from 'child_process'

function loadXlsxXml(sourcePath) {
  const unzippedCandidates = [
    path.join(process.env.TEMP || '', 'xlsx_inspect', 'unzipped'),
    path.join(process.env.TEMP || '', 'wardrobe_xlsx_unzip')
  ]
  const existing = unzippedCandidates.find(
    (d) =>
      fs.existsSync(path.join(d, 'xl/sharedStrings.xml')) &&
      fs.existsSync(path.join(d, 'xl/worksheets/sheet1.xml'))
  )
  if (existing) {
    return {
      shared: fs.readFileSync(path.join(existing, 'xl/sharedStrings.xml'), 'utf8'),
      sheet: fs.readFileSync(path.join(existing, 'xl/worksheets/sheet1.xml'), 'utf8')
    }
  }
  const tmp = path.join(process.env.TEMP || '/tmp', 'wardrobe_xlsx_unzip')
  fs.mkdirSync(tmp, { recursive: true })
  const zipCopy = path.join(tmp, 'book.zip')
  fs.copyFileSync(sourcePath, zipCopy)
  execSync(
    `powershell -NoProfile -Command "Expand-Archive -LiteralPath '${zipCopy.replace(/'/g, "''")}' -DestinationPath '${tmp.replace(/'/g, "''")}' -Force"`,
    { stdio: 'pipe' }
  )
  return {
    shared: fs.readFileSync(path.join(tmp, 'xl/sharedStrings.xml'), 'utf8'),
    sheet: fs.readFileSync(path.join(tmp, 'xl/worksheets/sheet1.xml'), 'utf8')
  }
}

const { shared: sharedXml, sheet: sheetXmlRaw } = loadXlsxXml(xlsxPath)
const shared = readSharedStrings(sharedXml)
const sheetXml = sheetXmlRaw

const cells = readSheetCells(sheetXml, shared)
const row1 = new Map()
for (const [k, v] of cells) {
  if (k.endsWith(',1')) row1.set(k, v)
}
const { seasons, colors } = buildHeaderMap(row1)

const CATEGORY_ROWS = [
  { label: '上衣打底', imageRow: 3, textRow: 4 },
  { label: '上衣外套', imageRow: 6, textRow: 7 },
  { label: '下装', imageRow: 9, textRow: 10 },
  { label: '裙子', imageRow: 12, textRow: 13 },
  { label: '运动', imageRow: 14, textRow: 15 }
]

const clothes = []
const colorCount = new Map()
let seq = 0

for (const cat of CATEGORY_ROWS) {
  const type = CATEGORY_MAP[cat.label] || '上衣'
  for (let col = 2; col <= 49; col++) {
    const text = cells.get(`${col},${cat.textRow}`)
    if (!text || text === '介绍' || text === '扔了') continue

    const parsed = parseMeasurements(text, type)
    if (parsed.skip) continue

    const season = getSeasonForCol(col, seasons)
    const color = getColorForCol(col, colors, seasons)
    const key = `${season}|${color}|${cat.label}`
    const idx = (colorCount.get(key) || 0) + 1
    colorCount.set(key, idx)

    seq += 1
    const id = `cloth_${String(seq).padStart(3, '0')}`
    const note = [...(parsed.noteParts || [])].filter(Boolean).join('；')
    const fullNote = text !== inferName(season, color, cat.label, idx, parsed.namePrefix, parsed.sizes)
      ? text
      : note

    clothes.push(
      clothItem({
        id,
        season,
        color,
        type,
        sizes: parsed.sizes,
        note: fullNote,
        categoryLabel: cat.label,
        index: idx,
        namePrefix: parsed.namePrefix
      })
    )
  }
}

const bundle = {
  version: 3,
  exportedAt: Date.now(),
  clothes,
  matches: [],
  inspirations: [],
  images: {}
}

const outPath = path.join(__dirname, '..', 'static', 'data', 'wardrobe_import.json')
fs.mkdirSync(path.dirname(outPath), { recursive: true })
fs.writeFileSync(outPath, JSON.stringify(bundle, null, 2), 'utf8')
console.log('Wrote', outPath)
console.log('Items:', clothes.length)
console.log('Source:', xlsxPath)
