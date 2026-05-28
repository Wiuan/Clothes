<template>
  <view class="page">
    <view class="top-bar">
      <text class="count">已选 {{ ids.length }} 件</text>
      <text class="hint">只修改勾选字段，未勾选不变</text>
    </view>

    <view class="form-card">
      <view class="field-block">
        <view class="field-head">
          <text class="field-label">季节</text>
          <switch
            class="field-switch"
            :checked="apply.season"
            color="#ff2442"
            @change="apply.season = $event.detail.value"
          />
        </view>
        <view v-if="apply.season" class="chips">
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
      </view>

      <view class="field-block">
        <view class="field-head">
          <text class="field-label">类型</text>
          <switch
            class="field-switch"
            :checked="apply.type"
            color="#ff2442"
            @change="apply.type = $event.detail.value"
          />
        </view>
        <view v-if="apply.type" class="chips">
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
      </view>

      <view class="field-block">
        <view class="field-head">
          <text class="field-label">颜色</text>
          <switch
            class="field-switch"
            :checked="apply.color"
            color="#ff2442"
            @change="apply.color = $event.detail.value"
          />
        </view>
        <template v-if="apply.color">
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
          <text class="sub-hint">可多选，将覆盖原颜色</text>
          <view v-if="form.otherSelected" class="custom">
            <input v-model="form.colorCustom" class="input" placeholder="自定义颜色名" maxlength="10" />
          </view>
        </template>
      </view>

      <view class="field-block field-block-last">
        <view class="field-head">
          <text class="field-label">适宜温度</text>
          <switch
            class="field-switch"
            :checked="apply.temp"
            color="#ff2442"
            @change="apply.temp = $event.detail.value"
          />
        </view>
        <template v-if="apply.temp">
          <view class="temp-row">
            <input v-model="form.tempMin" class="input temp" type="number" placeholder="最低℃" />
            <text class="sep">—</text>
            <input v-model="form.tempMax" class="input temp" type="number" placeholder="最高℃" />
          </view>
          <text class="sub-hint">留空表示清除该温度值</text>
        </template>
      </view>
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
  padding: 16rpx 16rpx 48rpx;
  box-sizing: border-box;
}

.top-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8rpx 16rpx;
  background: #fff;
  border-radius: 12rpx;
  padding: 14rpx 16rpx;
  margin-bottom: 12rpx;
}

.count {
  font-size: 26rpx;
  font-weight: 600;
  color: #222;
}

.hint {
  font-size: 22rpx;
  color: #999;
  flex: 1;
  min-width: 0;
}

.form-card {
  background: #fff;
  border-radius: 12rpx;
  padding: 4rpx 16rpx 12rpx;
}

.field-block {
  padding: 12rpx 0;
  border-bottom: 1rpx solid #f0f0f0;
}

.field-block-last {
  border-bottom: none;
}

.field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48rpx;
}

.field-label {
  font-size: 24rpx;
  font-weight: 600;
  color: #333;
}

.field-switch {
  transform: scale(0.82);
  transform-origin: center right;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  padding: 8rpx 0 4rpx;
}

.chip {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 6rpx 16rpx;
  background: #fff;
  border: 1rpx solid #eee;
  border-radius: 999rpx;
  font-size: 22rpx;
  color: #555;

  &.active {
    background: #fff5f6;
    border-color: #ffcdd2;
    color: #ff2442;
  }
}

.dot {
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
  border: 1rpx solid rgba(0, 0, 0, 0.08);
}

.custom {
  padding: 4rpx 0 8rpx;
}

.input {
  font-size: 24rpx;
  padding: 10rpx 14rpx;
  background: #fff;
  border: 1rpx solid #eee;
  border-radius: 8rpx;
  box-sizing: border-box;
}

.temp-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 8rpx 0 4rpx;
}

.temp {
  flex: 1;
  text-align: center;
}

.sep {
  color: #999;
  font-size: 22rpx;
}

.sub-hint {
  display: block;
  font-size: 20rpx;
  color: #aaa;
  line-height: 1.4;
  padding-bottom: 4rpx;
}

.save-btn {
  margin-top: 24rpx;
  height: 72rpx;
  line-height: 72rpx;
  background: #ff2442;
  border-radius: 999rpx;
  font-size: 28rpx;
}
</style>
