import { todayDateStr } from './checkinStorage.js'

/** @typedef {{ key: string, label: string, days: number|null }} WearPeriodOption */

export const WEAR_PERIOD_OPTS = [
  { key: '30d', label: '近30天', days: 30 },
  { key: '90d', label: '近90天', days: 90 },
  { key: '365d', label: '近一年', days: 365 },
  { key: 'all', label: '全部', days: null }
]

/**
 * @param {string} periodKey
 * @returns {{ startMs: number|null, endMs: number, label: string }}
 */
export function resolveWearPeriod(periodKey) {
  const opt = WEAR_PERIOD_OPTS.find((o) => o.key === periodKey) || WEAR_PERIOD_OPTS[2]
  const end = new Date()
  end.setHours(23, 59, 59, 999)
  const endMs = end.getTime()

  if (opt.days == null) {
    return { startMs: null, endMs, label: opt.label }
  }

  const start = new Date()
  start.setDate(start.getDate() - (opt.days - 1))
  start.setHours(0, 0, 0, 0)
  return { startMs: start.getTime(), endMs, label: opt.label }
}

function logInRange(log, startMs, endMs) {
  const d = log.date || todayDateStr(new Date(log.createdAt || 0))
  const parts = d.split('-').map(Number)
  if (parts.length < 3) return false
  const t = new Date(parts[0], parts[1] - 1, parts[2]).getTime()
  if (startMs != null && t < startMs) return false
  if (t > endMs) return false
  return true
}

/**
 * @param {string} clothId
 * @param {import('./checkinStorage.js').WearLogItem[]} logs
 */
export function countWearsForCloth(clothId, logs, { startMs = null, endMs = Date.now() } = {}) {
  let n = 0
  for (const log of logs) {
    if (!logInRange(log, startMs, endMs)) continue
    if (log.clothIds.includes(clothId)) n += 1
  }
  return n
}

export function getLastWearDate(clothId, logs) {
  let last = ''
  for (const log of logs) {
    if (!log.clothIds.includes(clothId)) continue
    const d = log.date || ''
    if (d > last) last = d
  }
  return last || null
}

/**
 * @param {import('./models.js').ClothItem[]} clothes active list
 * @param {import('./checkinStorage.js').WearLogItem[]} logs
 */
export function buildWearRankList(
  clothes,
  logs,
  { startMs = null, endMs = Date.now(), season = '', ascending = true } = {}
) {
  let list = clothes.filter((c) => c.status !== 'discarded')
  if (season) list = list.filter((c) => c.season === season)

  return list
    .map((cloth) => {
      const count = countWearsForCloth(cloth.id, logs, { startMs, endMs })
      const lastDate = getLastWearDate(cloth.id, logs)
      return {
        cloth,
        count,
        lastDate
      }
    })
    .sort((a, b) => {
      if (a.count !== b.count) return ascending ? a.count - b.count : b.count - a.count
      if (!a.lastDate && b.lastDate) return ascending ? -1 : 1
      if (a.lastDate && !b.lastDate) return ascending ? 1 : -1
      const cmp = (a.lastDate || '').localeCompare(b.lastDate || '')
      return ascending ? cmp : -cmp
    })
}

export function formatWearDate(dateStr) {
  if (!dateStr) return '从未穿着'
  const today = todayDateStr()
  if (dateStr === today) return '今天'
  const parts = dateStr.split('-')
  if (parts.length >= 3) return `${parts[0]}-${parts[1]}-${parts[2]}`
  return dateStr
}
