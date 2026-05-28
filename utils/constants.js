export const STORAGE_KEY = 'wardrobe_clothes_v1'

export const PRESET_COLORS = [
  '红',
  '黄',
  '蓝',
  '黑',
  '白',
  '绿',
  '紫',
  '棕',
  '灰',
  '其他'
]

/** @deprecated 使用 PRESET_COLORS */
export const COLORS = PRESET_COLORS

export const SEASONS = ['夏', '春秋', '冬']

export const TYPE_TOP = '上衣'
export const TYPE_BOTTOM = '下装'
export const TYPE_LONG = '长款'
export const TYPE_SPORT = '运动'

export const TYPES = [TYPE_TOP, TYPE_BOTTOM, TYPE_LONG, TYPE_SPORT]

export const CLOTH_STATUS = {
  ACTIVE: 'active',
  DISCARDED: 'discarded'
}

export const COLOR_HEX = {
  红: '#e53935',
  黄: '#f9a825',
  蓝: '#1e88e5',
  黑: '#212121',
  白: '#e0e0e0',
  绿: '#43a047',
  紫: '#8e24aa',
  棕: '#6d4c41',
  灰: '#757575',
  其他: '#9e9e9e'
}

export const TOP_SIZE_FIELDS = [
  { key: '衣长', label: '衣长 (cm)' },
  { key: '胸围', label: '胸围 (cm)' },
  { key: '肩宽', label: '肩宽 (cm)' },
  { key: '袖长', label: '袖长 (cm)' }
]

export const TOP_EXTRA_SIZE_FIELDS = [
  { key: '后片长', label: '后片长 (cm)' },
  { key: '领宽', label: '领宽 (cm)' }
]

export const BOTTOM_SIZE_FIELDS = [
  { key: '裤长裙长', label: '裤长/裙长 (cm)' },
  { key: '腰围', label: '腰围 (cm)' },
  { key: '臀围', label: '臀围 (cm)' }
]

export const BOTTOM_EXTRA_SIZE_FIELDS = [
  { key: '大腿围', label: '大腿围 (cm)' },
  { key: '前档', label: '前档 (cm)' },
  { key: '后档', label: '后档 (cm)' },
  { key: '脚口', label: '脚口 (cm)' }
]

/** 旧数据「外套」→「长款」 */
export function migrateType(type) {
  if (type === '外套') return TYPE_LONG
  return type
}

export function isBottomType(type) {
  return migrateType(type) === TYPE_BOTTOM
}

export function getSizeFieldsForType(type) {
  const t = migrateType(type)
  if (t === TYPE_BOTTOM) return [...BOTTOM_SIZE_FIELDS, ...BOTTOM_EXTRA_SIZE_FIELDS]
  if (t === TYPE_TOP) return [...TOP_SIZE_FIELDS, ...TOP_EXTRA_SIZE_FIELDS]
  if (t === TYPE_LONG || t === TYPE_SPORT) return [...TOP_SIZE_FIELDS]
  return TOP_SIZE_FIELDS
}

/** 上衣、下装可填材质 */
export function typeHasMaterial(type) {
  const t = migrateType(type)
  return t === TYPE_TOP || t === TYPE_BOTTOM
}

export function isDiscarded(item) {
  return item?.status === CLOTH_STATUS.DISCARDED
}

export const INSPIRATION_STYLES = ['通勤', '休闲', '约会', '运动']

export const LINK_RELATION = {
  HAVE_SIMILAR: 'have_similar',
  WANT_TO_BUY: 'want_to_buy'
}

export const LINK_RELATION_LABEL = {
  have_similar: '我有类似',
  want_to_buy: '想买'
}

/**
 * App 导出到手机「下载」目录（文件管理可直接看到，便于发给同事）
 */
export const APP_EXPORT_DOWNLOAD_FILE = '/storage/emulated/0/Download/wardrobe_export.json'

/** App ZIP 导出到下载目录 */
export const APP_EXPORT_DOWNLOAD_ZIP_FILE = '/storage/emulated/0/Download/wardrobe_export.zip'

/** 上次 App 导出成功的路径（同机「导入应用内备份」会尝试读取） */
export const LAST_EXPORT_PATH_KEY = 'wardrobe_last_export_abs'
