import { getItemColors } from './color.js'
import { TYPE_TOP, TYPE_BOTTOM } from './constants.js'

/** 灵感未填季节 → 视为可匹配，便于你发现后去改 */
export function seasonsMatch(clothSeason, inspSeason) {
  if (!inspSeason) return true
  return clothSeason === inspSeason
}

export function colorsOverlap(colorsA, colorsB) {
  if (!colorsA?.length || !colorsB?.length) return false
  const set = new Set(colorsB)
  return colorsA.some((c) => set.has(c))
}

function tagList(colorTags, key) {
  return Array.isArray(colorTags?.[key]) ? colorTags[key].filter(Boolean) : []
}

export function isInspirationLinkedToCloth(insp, clothId) {
  return !!clothId && (insp.links || []).some((l) => l.clothId === clothId)
}

/** 灵感库中已关联本件（不限主色/季节） */
export function getLinkedInspirations(cloth, inspirations) {
  if (!cloth?.id) return []
  return (inspirations || [])
    .filter((insp) => isInspirationLinkedToCloth(insp, cloth.id))
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
}

/**
 * 同季节（或未填季节）且灵感主色与衣物颜色有交集
 * @param {Set<string>} [excludeIds] 已列入的灵感 id（避免与已关联重复）
 */
function unlinkedInspirationSeasonRank(clothSeason, inspSeason) {
  if (!inspSeason) return 0
  if (inspSeason === clothSeason) return 0
  return 1
}

export function getColorMatchedInspirations(cloth, inspirations, excludeIds = new Set()) {
  if (!cloth) return []
  const clothColors = getItemColors(cloth)
  if (!clothColors.length) return []

  return (inspirations || [])
    .filter((insp) => {
      if (excludeIds.has(insp.id)) return false
      const primary = tagList(insp.colorTags, 'primary')
      if (!primary.length) return false
      return colorsOverlap(clothColors, primary)
    })
    .sort((a, b) => {
      const sa = unlinkedInspirationSeasonRank(cloth.season, a.season)
      const sb = unlinkedInspirationSeasonRank(cloth.season, b.season)
      if (sa !== sb) return sa - sb
      return (b.createdAt || 0) - (a.createdAt || 0)
    })
}

/** 已关联优先，其余为主色相近的灵感 */
export function getMatchedInspirations(cloth, inspirations) {
  const linked = getLinkedInspirations(cloth, inspirations)
  const linkedIds = new Set(linked.map((i) => i.id))
  const colorMatched = getColorMatchedInspirations(cloth, inspirations, linkedIds)
  return [...linked, ...colorMatched]
}

/**
 * 搭配色：优先辅色+点缀；若无则用主色中不与当前衣物重叠的颜色
 */
export function getCoordPaletteFromInspirations(inspirations, clothColors) {
  const coord = new Set()
  for (const insp of inspirations) {
    for (const c of tagList(insp.colorTags, 'secondary')) coord.add(c)
    for (const c of tagList(insp.colorTags, 'accent')) coord.add(c)
  }
  if (coord.size) return [...coord]

  const clothSet = new Set(clothColors || [])
  const fallback = new Set()
  for (const insp of inspirations) {
    for (const c of tagList(insp.colorTags, 'primary')) {
      if (!clothSet.has(c)) fallback.add(c)
    }
  }
  return [...fallback]
}

/**
 * 可搭配单品类型优先级（数字越小越靠前）
 * 上衣 → 先下装（成套），再上衣（叠穿）
 * 下装 → 先上衣，再下装
 * 长款/运动等 → 先上后下，其余靠后
 */
export function companionTypeRank(currentType, candidateType) {
  const cur = currentType || ''
  const cand = candidateType || ''
  if (cur === TYPE_TOP) {
    if (cand === TYPE_BOTTOM) return 0
    if (cand === TYPE_TOP) return 1
    return 2
  }
  if (cur === TYPE_BOTTOM) {
    if (cand === TYPE_TOP) return 0
    if (cand === TYPE_BOTTOM) return 1
    return 2
  }
  if (cand === TYPE_TOP) return 0
  if (cand === TYPE_BOTTOM) return 1
  return 2
}

export function sortCompanionClothes(cloth, companions) {
  return [...(companions || [])].sort((a, b) => {
    const ra = companionTypeRank(cloth?.type, a.type)
    const rb = companionTypeRank(cloth?.type, b.type)
    if (ra !== rb) return ra - rb
    return (b.createdAt || 0) - (a.createdAt || 0)
  })
}

/** 同季节、颜色命中搭配色 palette 的其它衣柜单品（已按类型优先排序） */
export function getCompanionClothes(cloth, allClothes, palette) {
  if (!cloth || !palette?.length) return []
  const list = (allClothes || []).filter((c) => {
    if (c.id === cloth.id) return false
    if (c.status === 'discarded') return false
    if (c.season !== cloth.season) return false
    return colorsOverlap(getItemColors(c), palette)
  })
  return sortCompanionClothes(cloth, list)
}

/**
 * @param {import('./models.js').ClothItem} cloth
 * @param {object[]} inspirations
 * @param {import('./models.js').ClothItem[]} allClothes
 */
export function buildColorRecommendations(cloth, inspirations, allClothes) {
  const clothColors = getItemColors(cloth)
  const linkedInspirations = getLinkedInspirations(cloth, inspirations)
  const matchedInspirations = getMatchedInspirations(cloth, inspirations)
  const palette = getCoordPaletteFromInspirations(matchedInspirations, clothColors)
  const companions = getCompanionClothes(cloth, allClothes, palette)

  return {
    ready: !!clothColors.length || linkedInspirations.length > 0,
    matchedInspirations,
    companions,
    palette,
    matchedTotal: matchedInspirations.length,
    companionTotal: companions.length
  }
}
