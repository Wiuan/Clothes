/** 列表 → 详情左右滑：与当前筛选/排序后的展示顺序一致 */

let clothBrowseIds = []
let inspirationBrowseIds = []

export function setClothBrowseIds(ids) {
  clothBrowseIds = Array.isArray(ids) ? [...ids] : []
}

export function getClothBrowseIds(anchorId) {
  if (!anchorId || !clothBrowseIds.length || !clothBrowseIds.includes(anchorId)) {
    return null
  }
  return clothBrowseIds
}

export function setInspirationBrowseIds(ids) {
  inspirationBrowseIds = Array.isArray(ids) ? [...ids] : []
}

export function getInspirationBrowseIds(anchorId) {
  if (!anchorId || !inspirationBrowseIds.length || !inspirationBrowseIds.includes(anchorId)) {
    return null
  }
  return inspirationBrowseIds
}
