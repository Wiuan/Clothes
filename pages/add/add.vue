<template>
  <view class="page">
    <view class="photo-box" @tap="choosePhoto">
      <image v-if="imageBase64" class="preview" :src="imageBase64" mode="aspectFill" />
      <view v-else class="placeholder">
        <text class="icon">📷</text>
        <text class="tip">{{ isEdit ? '点击更换照片' : '拍照 / 从相册选择' }}</text>
      </view>
    </view>

    <view class="form">
      <view class="field">
        <text class="label">名称</text>
        <input v-model="form.name" class="input" placeholder="例如：白色衬衫" maxlength="30" />
      </view>

      <view class="field">
        <text class="label">颜色</text>
        <text class="hint inline">可多选</text>
        <view class="chips">
          <view
            v-for="c in PRESET_COLORS"
            :key="c"
            class="chip"
            :class="{ active: isColorSelected(c) }"
            @tap="onToggleColor(c)"
          >
            <view class="dot" :style="{ background: COLOR_HEX[c] }" />
            <text>{{ c }}</text>
          </view>
        </view>
        <view v-if="form.otherSelected" class="custom-color">
          <input
            v-model="form.colorCustom"
            class="input"
            placeholder="自定义颜色名，如：藏青"
            maxlength="10"
          />
          <input
            v-model="form.colorHex"
            class="input hex"
            placeholder="#色值 选填 如 #2c5282"
            maxlength="7"
          />
        </view>
      </view>

      <view class="field">
        <text class="label">季节</text>
        <view class="chips row">
          <view
            v-for="s in SEASONS"
            :key="s"
            class="chip text-only"
            :class="{ active: form.season === s }"
            @tap="form.season = s"
          >
            {{ s }}
          </view>
        </view>
      </view>

      <view class="field">
        <text class="label">类型</text>
        <view class="chips row">
          <view
            v-for="t in TYPES"
            :key="t"
            class="chip text-only"
            :class="{ active: form.type === t }"
            @tap="onTypeChange(t)"
          >
            {{ t }}
          </view>
        </view>
      </view>

      <view class="field">
        <text class="label">适宜温度 (℃)</text>
        <view class="temp-row">
          <input
            v-model="form.tempMin"
            class="input temp-input"
            type="number"
            placeholder="最低"
          />
          <text class="temp-sep">—</text>
          <input
            v-model="form.tempMax"
            class="input temp-input"
            type="number"
            placeholder="最高"
          />
        </view>
        <text class="hint">选填；留空表示未设置</text>
      </view>

      <view v-if="sizeFields.length" class="field sizes">
        <text class="label">尺寸</text>
        <view v-for="f in sizeFields" :key="f.key" class="size-row">
          <text class="size-label">{{ f.label }}</text>
          <input
            v-model="form.sizes[f.key]"
            class="input size-input"
            type="digit"
            placeholder="选填"
          />
        </view>
      </view>

      <view class="field">
        <text class="label">买入时间</text>
        <picker mode="date" :value="form.purchaseDate" @change="onDatePick">
          <view class="input picker-val">{{ form.purchaseDate || '选填，点击选择' }}</view>
        </picker>
        <text v-if="form.purchaseDate" class="clear-link" @tap="form.purchaseDate = ''">清除</text>
      </view>

      <view class="field">
        <text class="label">买入价钱</text>
        <input
          v-model="form.purchasePrice"
          class="input"
          type="digit"
          placeholder="选填，如 199"
          maxlength="12"
        />
      </view>

      <view v-if="showMaterial" class="field">
        <text class="label">材质</text>
        <input v-model="form.material" class="input" placeholder="选填，如：棉、涤纶" maxlength="20" />
      </view>

      <view class="field">
        <text class="label">备注</text>
        <textarea
          v-model="form.note"
          class="textarea"
          placeholder="选填"
          maxlength="200"
          :auto-height="true"
        />
      </view>

      <view v-if="isEdit" class="field">
        <text class="label">状态</text>
        <view class="chips row">
          <view
            class="chip text-only"
            :class="{ active: form.status === CLOTH_STATUS.ACTIVE }"
            @tap="form.status = CLOTH_STATUS.ACTIVE"
          >
            在穿
          </view>
          <view
            class="chip text-only"
            :class="{ active: form.status === CLOTH_STATUS.DISCARDED }"
            @tap="form.status = CLOTH_STATUS.DISCARDED"
          >
            已扔掉
          </view>
        </view>
        <text v-if="form.status === CLOTH_STATUS.DISCARDED" class="hint">
          标记为已扔掉后，主衣柜不再显示，可在「已扔掉」中查看
        </text>
      </view>
    </view>

    <button class="save-btn" type="primary" @tap="onSave">{{ isEdit ? '保存修改' : '保存' }}</button>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  PRESET_COLORS,
  SEASONS,
  TYPES,
  COLOR_HEX,
  CLOTH_STATUS,
  getSizeFieldsForType,
  typeHasMaterial
} from '../../utils/constants.js'
import { getClothById, addCloth, updateCloth } from '../../utils/storage.js'
import { pickImageAsBase64, getClothImageSrc } from '../../utils/image.js'
import { saveClothImage, hydrateClothesImages } from '../../utils/imageStore.js'
import { buildColorsPayload, colorsToForm } from '../../utils/color.js'
import { clothExtraToForm, formExtraToCloth, emptyExtraForm } from '../../utils/clothFields.js'

const isEdit = ref(false)
const editId = ref('')
const imageBase64 = ref('')
const imageChanged = ref(false)
const originalType = ref('')

const form = ref({
  name: '',
  selectedColors: ['白'],
  otherSelected: false,
  colorCustom: '',
  colorHex: '',
  season: '夏',
  type: '上衣',
  tempMin: '',
  tempMax: '',
  sizes: {},
  status: CLOTH_STATUS.ACTIVE,
  ...emptyExtraForm()
})

const sizeFields = computed(() => getSizeFieldsForType(form.value.type))
const showMaterial = computed(() => typeHasMaterial(form.value.type))

onLoad(async (query) => {
  if (query.id) {
    const cloth = getClothById(query.id)
    if (!cloth) {
      uni.showToast({ title: '衣服不存在', icon: 'none' })
      setTimeout(() => uni.navigateBack(), 800)
      return
    }
    isEdit.value = true
    editId.value = cloth.id
    originalType.value = cloth.type
    await hydrateClothesImages([cloth])
    imageBase64.value = getClothImageSrc(cloth)
    loadClothToForm(cloth)
    uni.setNavigationBarTitle({ title: '编辑衣服' })
  } else {
    uni.setNavigationBarTitle({ title: '录入衣服' })
  }
})

function loadClothToForm(cloth) {
  const colorForm = colorsToForm(cloth)
  form.value = {
    name: cloth.name,
    ...colorForm,
    season: cloth.season,
    type: cloth.type,
    tempMin: cloth.tempMin != null ? String(cloth.tempMin) : '',
    tempMax: cloth.tempMax != null ? String(cloth.tempMax) : '',
    sizes: { ...(cloth.sizes || {}) },
    status: cloth.status === CLOTH_STATUS.DISCARDED ? CLOTH_STATUS.DISCARDED : CLOTH_STATUS.ACTIVE,
    ...clothExtraToForm(cloth)
  }
}

function onDatePick(e) {
  form.value.purchaseDate = e.detail.value
}

function isColorSelected(c) {
  if (c === '其他') return form.value.otherSelected
  return form.value.selectedColors.includes(c)
}

function onToggleColor(c) {
  if (c === '其他') {
    form.value.otherSelected = !form.value.otherSelected
    if (!form.value.otherSelected) {
      form.value.colorCustom = ''
      form.value.colorHex = ''
    }
    return
  }
  const set = new Set(form.value.selectedColors)
  if (set.has(c)) set.delete(c)
  else set.add(c)
  form.value.selectedColors = [...set]
}

function onTypeChange(type) {
  const prev = form.value.type
  form.value.type = type
  if (!isEdit.value || prev !== type) {
    if (!isEdit.value || originalType.value !== type) {
      form.value.sizes = {}
    }
  }
}

function pickImageFrom(sourceType) {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType,
    success: async (res) => {
      const temp = res.tempFilePaths[0]
      uni.showLoading({ title: '压缩中...', mask: true })
      try {
        imageBase64.value = await pickImageAsBase64(temp)
        imageChanged.value = true
      } catch (e) {
        const msg = e && e.message ? String(e.message) : ''
        uni.showToast({
          title: msg && msg.length < 24 ? msg : '图片处理失败，请换一张或重试',
          icon: 'none'
        })
      } finally {
        uni.hideLoading()
      }
    },
    fail: (err) => {
      const msg = err && err.errMsg ? String(err.errMsg) : ''
      if (/camera|Camera|模块/.test(msg)) {
        uni.showToast({
          title: '拍照需含 Camera 模块的自定义基座，请用相册或重新打包',
          icon: 'none',
          duration: 3200
        })
      } else if (!msg.includes('cancel')) {
        uni.showToast({ title: '选择图片失败', icon: 'none' })
      }
    }
  })
}

function choosePhoto() {
  uni.showActionSheet({
    itemList: ['从相册选择', '拍照'],
    success: (res) => {
      pickImageFrom(res.tapIndex === 1 ? ['camera'] : ['album'])
    }
  })
}

function parseTempInput(v) {
  if (v === '' || v === null || v === undefined) return null
  const n = Number(v)
  return Number.isFinite(n) ? Math.round(n) : null
}

function buildColorFields() {
  return buildColorsPayload(
    form.value.selectedColors,
    form.value.otherSelected,
    form.value.colorCustom,
    form.value.colorHex
  )
}

async function onSave() {
  if (!form.value.name.trim()) {
    uni.showToast({ title: '请填写名称', icon: 'none' })
    return
  }
  if (!imageBase64.value) {
    uni.showToast({ title: '请添加照片', icon: 'none' })
    return
  }

  let tempMin = parseTempInput(form.value.tempMin)
  let tempMax = parseTempInput(form.value.tempMax)
  if (tempMin != null && tempMax != null && tempMin > tempMax) {
    const t = tempMin
    tempMin = tempMax
    tempMax = t
  }

  const colorFields = buildColorFields()
  if (!colorFields) {
    uni.showToast({ title: '请至少选择一种颜色', icon: 'none' })
    return
  }
  const extra = formExtraToCloth(form.value, form.value.type)

  uni.showLoading({ title: '保存中...', mask: true })
  try {
    const clothId = isEdit.value
      ? editId.value
      : `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const { imageRef } = await saveClothImage(clothId, imageBase64.value)

    const payload = {
      name: form.value.name.trim(),
      ...colorFields,
      season: form.value.season,
      type: form.value.type,
      tempMin,
      tempMax,
      imageRef,
      sizes: { ...form.value.sizes },
      ...extra
    }

    if (isEdit.value) {
      const existing = getClothById(editId.value)
      const discarded = form.value.status === CLOTH_STATUS.DISCARDED
      updateCloth({
        ...existing,
        ...payload,
        id: editId.value,
        createdAt: existing.createdAt,
        status: discarded ? CLOTH_STATUS.DISCARDED : CLOTH_STATUS.ACTIVE,
        discardedAt: discarded ? existing.discardedAt || Date.now() : null
      })
      uni.showToast({ title: '已更新', icon: 'success' })
    } else {
      addCloth({
        ...payload,
        id: clothId,
        createdAt: Date.now()
      })
      uni.showToast({ title: '已保存', icon: 'success' })
    }
  } catch (e) {
    console.error(e)
    uni.showToast({ title: '保存失败', icon: 'none' })
    return
  } finally {
    uni.hideLoading()
  }

  setTimeout(() => uni.navigateBack(), 400)
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f7f7f8;
  padding: 24rpx 24rpx 48rpx;
  box-sizing: border-box;
}

.photo-box {
  width: 100%;
  height: 420rpx;
  background: #fff;
  border-radius: 20rpx;
  overflow: hidden;
  margin-bottom: 24rpx;
}

.preview {
  width: 100%;
  height: 100%;
}

.placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
}

.icon {
  font-size: 64rpx;
  margin-bottom: 16rpx;
}

.tip {
  font-size: 28rpx;
}

.form {
  background: #fff;
  border-radius: 20rpx;
  padding: 8rpx 24rpx 24rpx;
}

.field {
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f0f0f0;

  &:last-child {
    border-bottom: none;
  }
}

.label {
  font-size: 28rpx;
  color: #333;
  font-weight: 600;
  display: block;
  margin-bottom: 20rpx;
}

.input {
  font-size: 28rpx;
  padding: 16rpx 20rpx;
  background: #f7f7f8;
  border-radius: 12rpx;
}

.custom-color {
  margin-top: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.hex {
  font-size: 24rpx;
}

.temp-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.temp-input {
  flex: 1;
  text-align: center;
}

.temp-sep {
  color: #999;
  font-size: 28rpx;
}

.hint {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #aaa;

  &.inline {
    display: inline;
    margin-top: 0;
    margin-left: 12rpx;
    font-weight: normal;
  }
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.chip {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 24rpx;
  background: #f7f7f8;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: #555;
  border: 2rpx solid transparent;

  &.active {
    background: #fff0f3;
    border-color: #ff2442;
    color: #ff2442;
  }
}

.dot {
  width: 24rpx;
  height: 24rpx;
  border-radius: 50%;
  border: 1rpx solid rgba(0, 0, 0, 0.1);
}

.sizes .size-row {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
}

.size-label {
  width: 220rpx;
  font-size: 26rpx;
  color: #666;
  flex-shrink: 0;
}

.size-input {
  flex: 1;
}

.picker-val {
  color: #333;
}

.picker-val:empty,
.input.picker-val {
  min-height: 72rpx;
  line-height: 72rpx;
}

.clear-link {
  display: inline-block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #ff2442;
}

.textarea {
  width: 100%;
  min-height: 120rpx;
  font-size: 28rpx;
  padding: 16rpx 20rpx;
  background: #f7f7f8;
  border-radius: 12rpx;
  box-sizing: border-box;
}

.save-btn {
  margin-top: 40rpx;
  background: #ff2442;
  border-radius: 999rpx;
  font-size: 32rpx;
}
</style>
