/** 灵感库筛选 / 排序本地记忆，仅「重置」时清空 */

const STORAGE_KEY = 'inspiration_ui_v1'

const DEFAULTS = {
  filterStyle: '全部',
  filterSeason: '全部',
  filterColor: '全部',
  wantToBuyOnly: false,
  sortField: 'created',
  sortAsc: false
}

export function loadInspirationUiPrefs() {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY)
    if (!raw || typeof raw !== 'object') return { ...DEFAULTS }
    const sortField = raw.sortField === 'color' ? 'color' : 'created'
    return {
      filterStyle: raw.filterStyle || DEFAULTS.filterStyle,
      filterSeason: raw.filterSeason || DEFAULTS.filterSeason,
      filterColor: raw.filterColor || DEFAULTS.filterColor,
      wantToBuyOnly: !!raw.wantToBuyOnly,
      sortField,
      sortAsc: !!raw.sortAsc
    }
  } catch {
    return { ...DEFAULTS }
  }
}

export function saveInspirationUiPrefs(state) {
  try {
    uni.setStorageSync(STORAGE_KEY, {
      filterStyle: state.filterStyle,
      filterSeason: state.filterSeason,
      filterColor: state.filterColor,
      wantToBuyOnly: !!state.wantToBuyOnly,
      sortField: state.sortField === 'color' ? 'color' : 'created',
      sortAsc: !!state.sortAsc
    })
  } catch {
    /* ignore */
  }
}

export function clearInspirationUiPrefs() {
  try {
    uni.removeStorageSync(STORAGE_KEY)
  } catch {
    /* ignore */
  }
}
