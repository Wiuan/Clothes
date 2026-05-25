/**
 * V2 数据结构说明（localStorage）
 *
 * --- 衣物 wardrobe_clothes_v1 ---
 * @typedef {Object} ClothItem
 * @property {string} id
 * @property {string} name
 * @property {string[]} colors       颜色列表（可多选）
 * @property {Object<string,string>} [colorHexMap] 自定义颜色名 → #RRGGBB
 * @property {string} color          兼容：首个颜色
 * @property {string} [colorPreset]  兼容：首个预设
 * @property {string} [colorHex]     兼容：首个自定义 hex
 * @property {string} season         夏 | 春秋 | 冬
 * @property {string} type           上衣 | 下装 | 长款 | 运动
 * @property {string} [status]       active | discarded
 * @property {number|null} [discardedAt]
 * @property {string} [purchaseDate]   买入时间
 * @property {string} [purchasePrice] 买入价钱
 * @property {string} [material]       材质（上衣/下装）
 * @property {string} [note]           备注
 * @property {number|null} [tempMin] 最低适宜温度 ℃，null=未设置
 * @property {number|null} [tempMax] 最高适宜温度 ℃，null=未设置
 * @property {string} imageRef          图片库引用 img_{id}
 * @property {Object} sizes
 * @property {number} createdAt
 *
 * --- 搭配 wardrobe_matches_v1 ---
 * @typedef {Object} MatchItem
 * @property {string} id
 * @property {string} name
 * @property {string[]} clothIds      关联衣物 id，顺序即展示顺序
 * @property {string} [note]
 * @property {number} createdAt
 *
 * --- 导出包 version 3 ---
 * @typedef {Object} ExportBundleV3
 * @property {3} version
 * @property {number} exportedAt
 * @property {ClothItem[]} clothes
 * @property {MatchItem[]} [matches]
 * @property {WearLogItem[]} [wearLogs]
 *
 * --- 穿衣记录 wardrobe_wear_logs_v1 ---
 * @typedef {Object} WearLogItem
 * @property {string} id
 * @property {string} date
 * @property {'single'|'match'} type
 * @property {string[]} clothIds
 * @property {string} [matchId]
 * @property {number} createdAt
 */

import { migrateType, CLOTH_STATUS, PRESET_COLORS } from './constants.js'
import { normalizeItemColors } from './color.js'

export const STORAGE_CLOTHES = 'wardrobe_clothes_v1'
export const STORAGE_MATCHES = 'wardrobe_matches_v1'
export const STORAGE_INSPIRATIONS = 'wardrobe_inspirations_v1'
export const STORAGE_WEAR_LOGS = 'wardrobe_wear_logs_v1'

const REMOVED_PRESET_COLORS = ['粉', '橙']

/** V1 → V2 衣物字段补全 */
export function normalizeClothV2(raw) {
  if (!raw || typeof raw !== 'object' || !raw.id) return null

  const cloth = { ...raw }
  cloth.name = (cloth.name && String(cloth.name).trim()) || '未命名'
  cloth.season = cloth.season || '夏'
  cloth.type = migrateType(cloth.type || '上衣')
  cloth.sizes = cloth.sizes && typeof cloth.sizes === 'object' ? cloth.sizes : {}
  cloth.createdAt = typeof cloth.createdAt === 'number' ? cloth.createdAt : Date.now()

  cloth.status =
    cloth.status === CLOTH_STATUS.DISCARDED ? CLOTH_STATUS.DISCARDED : CLOTH_STATUS.ACTIVE
  if (cloth.status === CLOTH_STATUS.DISCARDED) {
    cloth.discardedAt =
      typeof cloth.discardedAt === 'number' ? cloth.discardedAt : cloth.createdAt
  } else {
    cloth.discardedAt = null
  }

  cloth.purchaseDate = cloth.purchaseDate ? String(cloth.purchaseDate).trim() : ''
  cloth.purchasePrice =
    cloth.purchasePrice != null && cloth.purchasePrice !== ''
      ? String(cloth.purchasePrice).trim()
      : ''
  cloth.material = cloth.material ? String(cloth.material).trim() : ''
  cloth.note = cloth.note ? String(cloth.note).trim() : ''

  // 颜色：兼容单选 → colors[]
  if (REMOVED_PRESET_COLORS.includes(cloth.colorPreset)) {
    const legacyName = cloth.colorPreset
    cloth.colorPreset = '其他'
    if (!cloth.color || PRESET_COLORS.includes(cloth.color)) {
      cloth.color = legacyName
    }
  }
  Object.assign(cloth, normalizeItemColors(cloth))

  // 温度：默认 null（未设置 = 筛选时视为全温度适用）
  cloth.tempMin = parseTemp(cloth.tempMin)
  cloth.tempMax = parseTemp(cloth.tempMax)
  if (
    cloth.tempMin != null &&
    cloth.tempMax != null &&
    cloth.tempMin > cloth.tempMax
  ) {
    const t = cloth.tempMin
    cloth.tempMin = cloth.tempMax
    cloth.tempMax = t
  }

  delete cloth.imageBase64
  delete cloth.imagePath
  delete cloth.thumbRef

  if (!cloth.imageRef && cloth.id) {
    cloth.imageRef = `img_${cloth.id}`
  }

  return cloth
}

export function normalizeMatchV2(raw) {
  if (!raw || typeof raw !== 'object') return null
  const match = { ...raw }
  if (!match.id) return null
  match.name = (match.name && String(match.name).trim()) || '未命名搭配'
  match.clothIds = Array.isArray(match.clothIds) ? match.clothIds.filter(Boolean) : []
  match.note = match.note ? String(match.note) : ''
  match.createdAt = typeof match.createdAt === 'number' ? match.createdAt : Date.now()
  return match
}

function parseTemp(v) {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? Math.round(n) : null
}

/** 详情页展示：15-25℃ / 15℃以上 / 仅最高 8℃ */
export function formatTempRange(item) {
  const min = item?.tempMin
  const max = item?.tempMax
  if (min == null && max == null) return '未设置'
  if (min != null && max != null) return `${min}-${max}℃`
  if (min != null) return `${min}℃以上`
  return `${max}℃以下`
}
