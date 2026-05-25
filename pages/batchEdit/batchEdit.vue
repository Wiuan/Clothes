<template>
  <view class="page">
    <view class="summary">
      <text class="count">已选 {{ ids.length }} 件</text>
      <text class="hint">只修改你勾选的字段，未勾选的不变</text>
    </view>

    <view class="form">
      <view class="toggle-row">
        <text class="toggle-label">修改季节</text>
        <switch :checked="apply.season" @change="apply.season = $event.detail.value" />
      </view>
      <view v-if="apply.season" class="chips row">
        <view
          v-for="s in SEASONS"
          :key="s"
          class="chip"
          :class="{ active: form.season === s }"
          @tap="form.season = s"
        >
          {{ s }}
        </view>
      </view>

      <view class="toggle-row">
        <text class="toggle-label">修改类型</text>
        <switch :checked="apply.type" @change="apply.type = $event.detail.value" />
      </view>
      <view v-if="apply.type" class="chips row">
        <view
          v-for="t in TYPES"
          :key="t"
          class="chip"
          :class="{ active: form.type === t }"
          @tap="form.type = t"
        >
          {{ t }}
        </view>
      </view>

      <view class="toggle-row">
        <text class="toggle-label">修改颜色</text>
        <switch :checked="apply.color" @change="apply.color = $event.detail.value" />
      </view>
      <view v-if="apply.color" class="chips">
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
      <text v-if="apply.color" class="sub-hint">可多选，将覆盖原颜色</text>
      <view v-if="apply.color && form.otherSelected" class="custom">
        <input v-model="form.colorCustom" class="input" placeholder="自定义颜色名" maxlength="10" />
      </view>

      <view class="toggle-row">
        <text class="toggle-label">修改适宜温度</text>
        <switch :checked="apply.temp" @change="apply.temp = $event.detail.value" />
      </view>
      <view v-if="apply.temp" class="temp-row">
        <input v-model="form.tempMin" class="input temp" type="number" placeholder="最低℃" />
        <text class="sep">—</text>
        <input v-model="form.tempMax" class="input temp" type="number" placeholder="最高℃" />
      </view>
      <text v-if="apply.temp" class="sub-hint">留空表示清除该温度值</text>
    </view>

    <button class="save-btn" type="primary" @tap="onSave">应用修改</button>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { PRESET_COLORS, SEASONS, TYPES, COLOR_HEX } from '../../utils/constants.js'
import { batchUpdateClothes } from '../../utils/storage.js'
import { getBatchEditIds, clearBatchEditIds, markBatchEditDone } from '../../utils/batch.js'
import { buildColorsPayload } from '../../utils/color.js'

const ids = ref([])

const apply = ref({
  season: false,
  type: false,
  color: false,
  temp: false
})

const form = ref({
  season: '夏',
  type: '上衣',
  selectedColors: ['白'],
  otherSelected: false,
  colorCustom: '',
  tempMin: '',
  tempMax: ''
})

function isColorSelected(c) {
  if (c === '其他') return form.value.otherSelected
  return form.value.selectedColors.includes(c)
}

function onToggleColor(c) {
  if (c === '其他') {
    form.value.otherSelected = !form.value.otherSelected
    return
  }
  const set = new Set(form.value.selectedColors)
  if (set.has(c)) set.delete(c)
  else set.add(c)
  form.value.selectedColors = [...set]
}

onLoad(() => {
  ids.value = getBatchEditIds()
  if (!ids.value.length) {
    uni.showToast({ title: '请先选择衣服', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 800)
  }
})

function parseTemp(v) {
  if (v === '' || v == null) return null
  const n = Number(v)
  return Number.isFinite(n) ? Math.round(n) : null
}

function onSave() {
  if (!ids.value.length) return
  const any =
    apply.value.season || apply.value.type || apply.value.color || apply.value.temp
  if (!any) {
    uni.showToast({ title: '请至少勾选一项要修改的内容', icon: 'none' })
    return
  }
  if (apply.value.color) {
    const colorFields = buildColorsPayload(
      form.value.selectedColors,
      form.value.otherSelected,
      form.value.colorCustom,
      ''
    )
    if (!colorFields) {
      uni.showToast({ title: '请至少选择一种颜色', icon: 'none' })
      return
    }
  }

  batchUpdateClothes(ids.value, (c) => {
    const next = { ...c }
    if (apply.value.season) next.season = form.value.season
    if (apply.value.type) next.type = form.value.type
    if (apply.value.color) {
      const colorFields = buildColorsPayload(
        form.value.selectedColors,
        form.value.otherSelected,
        form.value.colorCustom,
        ''
      )
      if (colorFields) Object.assign(next, colorFields)
    }
    if (apply.value.temp) {
      let tMin = parseTemp(form.value.tempMin)
      let tMax = parseTemp(form.value.tempMax)
      if (tMin != null && tMax != null && tMin > tMax) {
        const t = tMin
        tMin = tMax
        tMax = t
      }
      next.tempMin = tMin
      next.tempMax = tMax
    }
    return next
  })

  clearBatchEditIds()
  markBatchEditDone()
  uni.showToast({ title: '已批量更新', icon: 'success' })
  setTimeout(() => uni.navigateBack(), 400)
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f7f7f8;
  padding: 24rpx;
  box-sizing: border-box;
}

.summary {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
}

.count {
  font-size: 32rpx;
  font-weight: 600;
  color: #222;
  display: block;
}

.hint {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #999;
}

.form {
  background: #fff;
  border-radius: 16rpx;
  padding: 8rpx 24rpx 24rpx;
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0 12rpx;
  border-top: 1rpx solid #f0f0f0;

  &:first-child {
    border-top: none;
  }
}

.toggle-label {
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  padding-bottom: 16rpx;
}

.chip {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 10rpx 22rpx;
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
  width: 22rpx;
  height: 22rpx;
  border-radius: 50%;
  border: 1rpx solid rgba(0, 0, 0, 0.1);
}

.custom {
  padding-bottom: 16rpx;
}

.input {
  font-size: 28rpx;
  padding: 16rpx 20rpx;
  background: #f7f7f8;
  border-radius: 12rpx;
}

.temp-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding-bottom: 8rpx;
}

.temp {
  flex: 1;
  text-align: center;
}

.sep {
  color: #999;
}

.sub-hint {
  display: block;
  font-size: 22rpx;
  color: #aaa;
  padding-bottom: 16rpx;
}

.save-btn {
  margin-top: 40rpx;
  background: #ff2442;
  border-radius: 999rpx;
  font-size: 32rpx;
}
</style>
