import { COLOR_HEX, PRESET_COLORS } from './constants.js'

/** 是否预设颜色名（不含「其他」） */
export function isPresetColor(name) {
  return name && name !== '其他' && PRESET_COLORS.includes(name)
}

/** 衣物颜色列表（展示名） */
export function getItemColors(item) {
  if (!item) return []
  if (Array.isArray(item.colors) && item.colors.length) {
    return item.colors
  }
  const legacy = item.color || item.colorPreset
  if (!legacy || legacy === '其他') {
    if (item.color && item.color !== '其他') return [item.color]
    return legacy ? [legacy] : []
  }
  return [legacy]
}

export function getColorHexByName(name, colorHexMap) {
  if (!name) return COLOR_HEX['其他']
  const map = colorHexMap || {}
  if (map[name] && /^#[0-9A-Fa-f]{6}$/.test(map[name])) return map[name]
  if (COLOR_HEX[name]) return COLOR_HEX[name]
  if (name === '其他') return COLOR_HEX['其他']
  return COLOR_HEX['其他']
}

/** 筛选：任一颜色匹配即可 */
export function itemMatchesColor(item, filterColor) {
  if (!filterColor) return true
  const list = getItemColors(item)
  if (list.includes(filterColor)) return true
  if (filterColor === '其他') {
    return list.some((c) => !isPresetColor(c))
  }
  return false
}

/** 列表卡片：最多展示 3 个色点 */
export function getItemColorHexList(item, max = 3) {
  const colors = getItemColors(item)
  const map = item?.colorHexMap || {}
  return colors.slice(0, max).map((c) => getColorHexByName(c, map))
}

/** 首个颜色圆点（兼容） */
export function getItemColorHex(item) {
  const list = getItemColorHexList(item, 1)
  return list[0] || COLOR_HEX['其他']
}

/** 标签：红·白 / 藏青 */
export function getItemColorLabel(item) {
  const colors = getItemColors(item)
  if (!colors.length) return '其他'
  return colors.join('·')
}

/** 表单 → 存储字段 */
export function buildColorsPayload(selectedPresets, otherSelected, colorCustom, colorHex) {
  const colors = [...selectedPresets.filter((c) => c && c !== '其他')]
  const colorHexMap = {}

  if (otherSelected) {
    const custom = (colorCustom || '').trim() || '其他'
    if (!colors.includes(custom)) colors.push(custom)
    let hex = (colorHex || '').trim()
    if (hex && !hex.startsWith('#')) hex = `#${hex}`
    if (hex && /^#[0-9A-Fa-f]{6}$/.test(hex)) {
      colorHexMap[custom] = hex
    }
  }

  if (!colors.length) return null

  const first = colors[0]
  return {
    colors,
    colorHexMap,
    color: first,
    colorPreset: isPresetColor(first) ? first : '其他',
    colorHex: colorHexMap[first] || undefined
  }
}

/** 存储 → 表单 */
export function colorsToForm(item) {
  const all = getItemColors(item)
  const selectedPresets = all.filter((c) => isPresetColor(c))
  const customs = all.filter((c) => !isPresetColor(c))
  const otherSelected = customs.length > 0
  const custom = customs[0] || ''
  const map = item?.colorHexMap || {}
  return {
    selectedColors: selectedPresets,
    otherSelected,
    colorCustom: custom && custom !== '其他' ? custom : '',
    colorHex: custom ? map[custom] || item.colorHex || '' : ''
  }
}

/** 在 normalizeClothV2 中调用 */
export function normalizeItemColors(cloth) {
  if (!Array.isArray(cloth.colors) || !cloth.colors.length) {
    let single = cloth.color || cloth.colorPreset || '其他'
    if (cloth.colorPreset === '其他' && cloth.color && cloth.color !== '其他') {
      single = cloth.color
    }
    cloth.colors = [single]
  }

  cloth.colors = [...new Set(cloth.colors.map((s) => String(s).trim()).filter(Boolean))]
  if (!cloth.colors.length) cloth.colors = ['其他']

  cloth.colorHexMap =
    cloth.colorHexMap && typeof cloth.colorHexMap === 'object' ? { ...cloth.colorHexMap } : {}

  if (cloth.colorHex && /^#[0-9A-Fa-f]{6}$/.test(cloth.colorHex)) {
    const customName = cloth.colors.find((c) => !isPresetColor(c))
    if (customName && !cloth.colorHexMap[customName]) {
      cloth.colorHexMap[customName] = cloth.colorHex
    }
  }

  const first = cloth.colors[0]
  cloth.color = first
  cloth.colorPreset = isPresetColor(first) ? first : '其他'
  const customFirst = cloth.colors.find((c) => !isPresetColor(c))
  cloth.colorHex = customFirst ? cloth.colorHexMap[customFirst] : undefined

  return cloth
}
