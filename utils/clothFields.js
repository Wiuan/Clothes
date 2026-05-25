import { getSizeFieldsForType, typeHasMaterial, migrateType } from './constants.js'

/** 详情页分区展示 */
export function buildClothDetailSections(item) {
  if (!item) return []

  const sections = []
  const sizeFields = getSizeFieldsForType(item.type)
  const sizes = item.sizes || {}
  if (sizeFields.length) {
    sections.push({
      title: '尺寸',
      rows: sizeFields.map((f) => ({
        key: f.key,
        label: f.label.replace(' (cm)', ''),
        value: sizes[f.key] || ''
      }))
    })
  }

  const infoRows = []
  if (item.purchaseDate) infoRows.push({ label: '买入时间', value: item.purchaseDate })
  if (item.purchasePrice) infoRows.push({ label: '买入价钱', value: formatPrice(item.purchasePrice) })
  if (typeHasMaterial(item.type) && item.material) {
    infoRows.push({ label: '材质', value: item.material })
  }
  if (item.note) infoRows.push({ label: '备注', value: item.note, full: true })

  if (infoRows.length) {
    sections.push({ title: '购买信息', rows: infoRows })
  }

  return sections
}

function formatPrice(v) {
  const s = String(v).trim()
  if (!s) return ''
  return /元|¥/.test(s) ? s : `${s} 元`
}

export function emptyExtraForm() {
  return {
    purchaseDate: '',
    purchasePrice: '',
    material: '',
    note: ''
  }
}

export function clothExtraToForm(cloth) {
  return {
    purchaseDate: cloth.purchaseDate || '',
    purchasePrice: cloth.purchasePrice != null ? String(cloth.purchasePrice) : '',
    material: cloth.material || '',
    note: cloth.note || ''
  }
}

export function formExtraToCloth(extra, type) {
  const t = migrateType(type)
  const out = {
    purchaseDate: (extra.purchaseDate || '').trim(),
    purchasePrice: (extra.purchasePrice || '').trim(),
    note: (extra.note || '').trim()
  }
  if (typeHasMaterial(t)) {
    out.material = (extra.material || '').trim()
  } else {
    out.material = ''
  }
  return out
}

/** 各类型均可录入买入信息 */
export function typeShowsPurchase() {
  return true
}
