import { PRESET_COLORS } from './constants.js'
import { getItemColors } from './color.js'

export const SORT_FIELD_LABELS = {
  created: '时间',
  length: '衣长',
  color: '颜色'
}

export const WARDROBE_SORT_FIELDS = ['created', 'length', 'color']

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

function byCreatedAsc(a, b) {
  return (a.createdAt || 0) - (b.createdAt || 0)
}

function compareColorKeys(a, b) {
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
  return 0
}

export function nextWardrobeSortField(current) {
  const i = WARDROBE_SORT_FIELDS.indexOf(current)
  const next = WARDROBE_SORT_FIELDS[(i + 1) % WARDROBE_SORT_FIELDS.length]
  return {
    field: next,
    ascending: next === 'created' ? false : true
  }
}

/**
 * @param {Array} list
 * @param {string} field created | length | color
 * @param {boolean} ascending
 */
export function sortClothes(list, field = 'created', ascending = false) {
  const arr = Array.isArray(list) ? [...list] : []
  if (!arr.length) return arr

  if (field === 'created' || !field) {
    return arr.sort(ascending ? byCreatedAsc : byCreatedDesc)
  }

  if (field === 'length') {
    arr.sort((a, b) => {
      const la = parseGarmentLength(a)
      const lb = parseGarmentLength(b)
      if (la == null && lb == null) return ascending ? byCreatedAsc(a, b) : byCreatedDesc(a, b)
      if (la == null) return 1
      if (lb == null) return -1
      const diff = la - lb
      if (diff !== 0) return ascending ? diff : -diff
      return ascending ? byCreatedAsc(a, b) : byCreatedDesc(a, b)
    })
    return arr
  }

  if (field === 'color') {
    arr.sort((a, b) => {
      let d = compareColorKeys(a, b)
      if (d === 0) d = ascending ? byCreatedAsc(a, b) : byCreatedDesc(a, b)
      return ascending ? d : -d
    })
    return arr
  }

  return arr.sort(byCreatedDesc)
}
