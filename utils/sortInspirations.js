import { PRESET_COLORS } from './constants.js'

export const INSPIRATION_SORT_FIELD_LABELS = {
  created: '时间',
  color: '颜色'
}

export const INSPIRATION_SORT_FIELDS = ['created', 'color']

function primaryColorName(insp) {
  const p = insp?.colorTags?.primary
  if (Array.isArray(p) && p.length) return p[0]
  return '其他'
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

export function nextInspirationSortField(current) {
  const i = INSPIRATION_SORT_FIELDS.indexOf(current)
  const next = INSPIRATION_SORT_FIELDS[(i + 1) % INSPIRATION_SORT_FIELDS.length]
  return {
    field: next,
    ascending: next === 'created' ? false : true
  }
}

/**
 * @param {Array} list
 * @param {string} field created | color
 * @param {boolean} ascending
 */
export function sortInspirations(list, field = 'created', ascending = false) {
  const arr = Array.isArray(list) ? [...list] : []
  if (!arr.length) return arr

  if (field === 'created' || !field) {
    return arr.sort(ascending ? byCreatedAsc : byCreatedDesc)
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
