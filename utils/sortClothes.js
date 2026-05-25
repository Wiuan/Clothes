import { PRESET_COLORS } from './constants.js'
import { getItemColors } from './color.js'

/** 衣柜排序选项（picker 用，文案尽量短） */
export const SORT_OPTIONS = [
  { value: '', label: '默认' },
  { value: 'length_asc', label: '衣长↑' },
  { value: 'length_desc', label: '衣长↓' },
  { value: 'color', label: '颜色' }
]

const LENGTH_KEYS = ['衣长', '裤长裙长']

function parseGarmentLength(item) {
  const sizes = item && item.sizes
  if (!sizes || typeof sizes !== 'object') return null
  for (const key of LENGTH_KEYS) {
    const raw = sizes[key]
    if (raw === '' || raw == null) continue
    const n = Number(raw)
    if (Number.isFinite(n)) return n
  }
  return null
}

function primaryColorName(item) {
  const colors = getItemColors(item)
  return colors[0] || item.color || '其他'
}

function colorSortKey(item) {
  const name = primaryColorName(item)
  const presetIdx = PRESET_COLORS.indexOf(name)
  if (presetIdx >= 0) {
    return { tier: 0, presetIdx, name }
  }
  return { tier: 1, presetIdx: 0, name: String(name) }
}

function byCreatedDesc(a, b) {
  return (b.createdAt || 0) - (a.createdAt || 0)
}

/**
 * @param {Array} list 已筛选列表
 * @param {string} mode '' | length_asc | length_desc | color
 */
export function sortClothes(list, mode) {
  if (!mode || !Array.isArray(list) || !list.length) {
    return Array.isArray(list) ? [...list] : []
  }

  const arr = [...list]

  if (mode === 'length_asc' || mode === 'length_desc') {
    const asc = mode === 'length_asc'
    arr.sort((a, b) => {
      const la = parseGarmentLength(a)
      const lb = parseGarmentLength(b)
      if (la == null && lb == null) return byCreatedDesc(a, b)
      if (la == null) return 1
      if (lb == null) return -1
      const diff = la - lb
      if (diff !== 0) return asc ? diff : -diff
      return byCreatedDesc(a, b)
    })
    return arr
  }

  if (mode === 'color') {
    arr.sort((a, b) => {
      const ka = colorSortKey(a)
      const kb = colorSortKey(b)
      if (ka.tier !== kb.tier) return ka.tier - kb.tier
      if (ka.tier === 0) {
        const d = ka.presetIdx - kb.presetIdx
        if (d !== 0) return d
      } else {
        const d = ka.name.localeCompare(kb.name, 'zh')
        if (d !== 0) return d
      }
      return byCreatedDesc(a, b)
    })
    return arr
  }

  return arr
}
