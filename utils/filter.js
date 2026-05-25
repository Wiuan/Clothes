import { itemMatchesColor } from './color.js'

/**
 * 筛选衣物
 * @param {Object} opts
 * @param {string} [opts.season]
 * @param {string} [opts.type]
 * @param {string} [opts.color] 按 colors 任一匹配
 * @param {string|number} [opts.currentTemp] 当前温度 ℃，空则不按温度筛
 * @param {boolean} [opts.onlyDiscarded] 仅已扔掉
 * @param {boolean} [opts.includeDiscarded] 包含已扔掉（默认 false，主列表隐藏）
 */
export function filterClothes(
  list,
  { season, type, color, currentTemp, onlyDiscarded = false, includeDiscarded = false }
) {
  const temp = parseCurrentTemp(currentTemp)

  return list.filter((item) => {
    const discarded = item.status === 'discarded'
    if (onlyDiscarded) {
      if (!discarded) return false
    } else if (!includeDiscarded && discarded) {
      return false
    }

    if (season && item.season !== season) return false
    if (type && item.type !== type) return false
    if (color && !itemMatchesColor(item, color)) return false
    if (temp !== null && !matchTemperature(item, temp)) return false
    return true
  })
}

function parseCurrentTemp(v) {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

/**
 * 未设置 tempMin/tempMax 的衣物：任意温度都显示（兼容 V1）
 * 已设置：当前温度落在 [min, max] 内（缺一端则开放）
 */
export function matchTemperature(item, current) {
  const min = item.tempMin
  const max = item.tempMax
  if (min == null && max == null) return true

  const lo = min != null ? min : -Infinity
  const hi = max != null ? max : Infinity
  return current >= lo && current <= hi
}
