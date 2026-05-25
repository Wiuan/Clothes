import { STORAGE_WEAR_LOGS } from './models.js'

/**
 * @typedef {Object} WearLogItem
 * @property {string} id
 * @property {string} date          YYYY-MM-DD 本地日期
 * @property {'single'|'match'} type
 * @property {string[]} clothIds
 * @property {string} [matchId]
 * @property {number} createdAt
 */

export function todayDateStr(d = new Date()) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function normalizeWearLog(raw) {
  if (!raw || typeof raw !== 'object' || !raw.id) return null
  const log = { ...raw }
  log.date = log.date ? String(log.date).slice(0, 10) : todayDateStr()
  log.type = log.type === 'match' ? 'match' : 'single'
  log.clothIds = Array.isArray(log.clothIds) ? log.clothIds.filter(Boolean) : []
  if (!log.clothIds.length) return null
  log.createdAt = typeof log.createdAt === 'number' ? log.createdAt : Date.now()
  return log
}

export function getWearLogs() {
  try {
    const raw = uni.getStorageSync(STORAGE_WEAR_LOGS)
    if (!Array.isArray(raw)) return []
    return raw.map(normalizeWearLog).filter(Boolean)
  } catch {
    return []
  }
}

export function saveWearLogs(list) {
  uni.setStorageSync(STORAGE_WEAR_LOGS, list)
}

/** @param {Omit<WearLogItem, 'id'|'createdAt'> & { id?: string, createdAt?: number }} entry */
export function addWearLog(entry) {
  const clothIds = [...new Set((entry.clothIds || []).filter(Boolean))]
  if (!clothIds.length) return null

  const log = normalizeWearLog({
    id: entry.id || `wear_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
    date: entry.date || todayDateStr(),
    type: entry.type === 'match' ? 'match' : 'single',
    clothIds,
    matchId: entry.matchId || undefined,
    createdAt: entry.createdAt ?? Date.now()
  })
  if (!log) return null

  const list = getWearLogs()
  list.unshift(log)
  saveWearLogs(list)
  return log
}

export function removeWearLog(id) {
  saveWearLogs(getWearLogs().filter((l) => l.id !== id))
}

export function replaceAllWearLogs(list) {
  const normalized = (Array.isArray(list) ? list : [])
    .map(normalizeWearLog)
    .filter(Boolean)
  saveWearLogs(normalized)
  return normalized
}

export function getWearLogsForDate(dateStr) {
  return getWearLogs().filter((l) => l.date === dateStr)
}

/** 某日已打卡的衣物 id 集合（单件/搭配均计入） */
export function getWornClothIdSetForDate(dateStr = todayDateStr()) {
  const set = new Set()
  for (const log of getWearLogsForDate(dateStr)) {
    for (const id of log.clothIds) set.add(id)
  }
  return set
}

export function isClothWornOnDate(clothId, dateStr = todayDateStr()) {
  if (!clothId) return false
  return getWornClothIdSetForDate(dateStr).has(clothId)
}

export function wearLogsForExport() {
  return getWearLogs().map((l) => ({
    id: l.id,
    date: l.date,
    type: l.type,
    clothIds: [...l.clothIds],
    matchId: l.matchId,
    createdAt: l.createdAt
  }))
}
