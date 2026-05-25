export const BATCH_EDIT_IDS_KEY = 'wardrobe_batch_edit_ids'
export const BATCH_EDIT_DONE_KEY = 'wardrobe_batch_edit_done'

export function setBatchEditIds(ids) {
  uni.setStorageSync(BATCH_EDIT_IDS_KEY, ids)
}

export function getBatchEditIds() {
  try {
    const raw = uni.getStorageSync(BATCH_EDIT_IDS_KEY)
    return Array.isArray(raw) ? raw : []
  } catch {
    return []
  }
}

export function clearBatchEditIds() {
  uni.removeStorageSync(BATCH_EDIT_IDS_KEY)
}

export function markBatchEditDone() {
  uni.setStorageSync(BATCH_EDIT_DONE_KEY, true)
}

export function consumeBatchEditDone() {
  try {
    const done = uni.getStorageSync(BATCH_EDIT_DONE_KEY)
    if (done) uni.removeStorageSync(BATCH_EDIT_DONE_KEY)
    return !!done
  } catch {
    return false
  }
}
