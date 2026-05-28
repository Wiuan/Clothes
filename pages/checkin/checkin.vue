<template>
  <view class="page">
    <view class="date-bar">
      <text class="date-label">今日</text>
      <text class="date-val">{{ todayStr }}</text>
    </view>

    <view class="mode-tabs">
      <view
        class="mode-tab"
        :class="{ active: mode === 'single' }"
        @tap="mode = 'single'"
      >
        单件
      </view>
      <view
        class="mode-tab"
        :class="{ active: mode === 'match' }"
        @tap="mode = 'match'"
      >
        搭配
      </view>
    </view>

    <view v-if="todayLogs.length" class="today-block">
      <text class="block-title">今日已记录（{{ todayLogs.length }}）</text>
      <view v-for="log in todayLogs" :key="log.id" class="today-item">
        <text class="today-meta">
          {{ log.type === 'match' ? '搭配' : '单件' }} · {{ log.clothIds.length }} 件
          <text v-if="log.matchName"> · {{ log.matchName }}</text>
        </text>
        <text class="today-del" @tap="onRemoveLog(log.id)">撤销</text>
      </view>
    </view>

    <template v-if="mode === 'single'">
      <view class="cloth-filter-row">
        <picker class="filter-picker" :range="clothSeasonOpts" :value="clothSeasonIdx" @change="onClothSeasonPick">
          <view class="chip" :class="{ active: !!clothFilterSeason }">{{ clothSeasonLabel }} ▾</view>
        </picker>
        <picker class="filter-picker" :range="clothTypeOpts" :value="clothTypeIdx" @change="onClothTypePick">
          <view class="chip" :class="{ active: !!clothFilterType }">{{ clothTypeLabel }} ▾</view>
        </picker>
        <text
          v-if="clothFilterSeason || clothFilterType"
          class="filter-reset"
          @tap="resetClothFilter"
        >
          重置
        </text>
        <text class="filter-stats">
          已选{{ selectedIds.length }}·{{ filteredClothes.length }}/{{ allClothes.length }}
        </text>
      </view>

      <button class="btn primary save-bar" :disabled="!canSubmit" @tap="onSubmit">
        记录今日穿着
      </button>

      <view class="pick-grid">
        <view
          v-for="c in filteredClothes"
          :key="c.id"
          class="pick-item"
          :class="{ selected: selectedSet.has(c.id) }"
          @tap="toggleSelect(c.id)"
        >
          <image
            v-if="getClothImageSrc(c)"
            class="pick-img"
            :src="getClothImageSrc(c)"
            mode="aspectFill"
          />
          <view v-else class="pick-img placeholder">无图</view>
          <text class="pick-name">{{ c.name }}</text>
          <view v-if="selectedSet.has(c.id)" class="check">✓</view>
        </view>
      </view>
    </template>

    <template v-else>
      <text class="hint">选一套搭配，其中每件衣服各计 1 次</text>
      <button class="btn primary save-bar" :disabled="!canSubmit" @tap="onSubmit">
        记录今日穿着
      </button>
      <view v-if="matches.length" class="match-list">
        <view
          v-for="m in matches"
          :key="m.id"
          class="match-row"
          :class="{ selected: selectedMatchId === m.id }"
          @tap="selectedMatchId = m.id"
        >
          <view class="match-thumbs">
            <image
              v-for="(src, i) in matchThumbSrcs(m)"
              :key="i"
              class="match-thumb"
              :src="src"
              mode="aspectFill"
            />
          </view>
          <view class="match-info">
            <text class="match-name">{{ m.name }}</text>
            <text class="match-sub">{{ m.clothIds.length }} 件</text>
          </view>
          <view v-if="selectedMatchId === m.id" class="match-check">✓</view>
        </view>
      </view>
      <view v-else class="empty">
        <text>还没有搭配，请先在「搭配」页创建</text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { SEASONS, TYPES } from '../../utils/constants.js'
import { getClothes, getClothById } from '../../utils/storage.js'
import { getMatches } from '../../utils/matchStorage.js'
import { filterClothes } from '../../utils/filter.js'
import {
  todayDateStr,
  addWearLog,
  getWearLogsForDate,
  removeWearLog
} from '../../utils/checkinStorage.js'
import { getClothImageSrc } from '../../utils/image.js'
import { hydrateClothesImages } from '../../utils/imageStore.js'

const CLOTH_ALL = '全部'
const clothSeasonOpts = [CLOTH_ALL, ...SEASONS]
const clothTypeOpts = [CLOTH_ALL, ...TYPES]

const todayStr = todayDateStr()
const mode = ref('single')
const selectedIds = ref([])
const selectedMatchId = ref('')
const matches = ref([])
const todayLogs = ref([])

const clothFilterSeason = ref('')
const clothFilterType = ref('')

const selectedSet = computed(() => new Set(selectedIds.value))
const allClothes = computed(() => getClothes().filter((c) => c.status !== 'discarded'))

const clothSeasonIdx = computed(() =>
  clothFilterSeason.value ? clothSeasonOpts.indexOf(clothFilterSeason.value) : 0
)
const clothTypeIdx = computed(() =>
  clothFilterType.value ? clothTypeOpts.indexOf(clothFilterType.value) : 0
)
const clothSeasonLabel = computed(() => clothFilterSeason.value || CLOTH_ALL)
const clothTypeLabel = computed(() => clothFilterType.value || CLOTH_ALL)

const filteredClothes = computed(() =>
  filterClothes(allClothes.value, {
    season: clothFilterSeason.value || undefined,
    type: clothFilterType.value || undefined
  })
)

const canSubmit = computed(() => {
  if (mode.value === 'single') return selectedIds.value.length > 0
  return !!selectedMatchId.value
})

function onClothSeasonPick(e) {
  const v = clothSeasonOpts[e.detail.value]
  clothFilterSeason.value = v === CLOTH_ALL ? '' : v
}
function onClothTypePick(e) {
  const v = clothTypeOpts[e.detail.value]
  clothFilterType.value = v === CLOTH_ALL ? '' : v
}
function resetClothFilter() {
  clothFilterSeason.value = ''
  clothFilterType.value = ''
}

function toggleSelect(id) {
  const set = new Set(selectedIds.value)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  selectedIds.value = [...set]
}

function matchThumbSrcs(m) {
  return (m.clothIds || []).slice(0, 3).map((id) => {
    const c = getClothById(id)
    return c ? getClothImageSrc(c) : ''
  })
}

function refreshTodayLogs() {
  const logs = getWearLogsForDate(todayStr)
  todayLogs.value = logs.map((log) => {
    let matchName = ''
    if (log.type === 'match' && log.matchId) {
      const m = matches.value.find((x) => x.id === log.matchId)
      matchName = m?.name || ''
    }
    return { ...log, matchName }
  })
}

function onRemoveLog(id) {
  uni.showModal({
    title: '撤销记录',
    content: '删除这条今日穿着记录？对应次数会减回。',
    success: (res) => {
      if (!res.confirm) return
      removeWearLog(id)
      refreshTodayLogs()
      uni.showToast({ title: '已撤销', icon: 'none' })
    }
  })
}

function onSubmit() {
  if (mode.value === 'single') {
    if (!selectedIds.value.length) {
      uni.showToast({ title: '请至少选一件衣服', icon: 'none' })
      return
    }
    addWearLog({
      date: todayStr,
      type: 'single',
      clothIds: [...selectedIds.value]
    })
    selectedIds.value = []
  } else {
    const m = matches.value.find((x) => x.id === selectedMatchId.value)
    if (!m || !m.clothIds.length) {
      uni.showToast({ title: '请选择搭配', icon: 'none' })
      return
    }
    addWearLog({
      date: todayStr,
      type: 'match',
      clothIds: [...m.clothIds],
      matchId: m.id
    })
    selectedMatchId.value = ''
  }
  refreshTodayLogs()
  uni.showToast({ title: '已记录', icon: 'success' })
}

onShow(async () => {
  await hydrateClothesImages(getClothes())
  matches.value = getMatches().filter((m) => m.clothIds.length > 0)
  refreshTodayLogs()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f7f7f8;
  padding: 20rpx;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.date-bar {
  background: #fff;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  margin-bottom: 12rpx;
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.date-label {
  font-size: 24rpx;
  color: #888;
}

.date-val {
  font-size: 30rpx;
  font-weight: 600;
  color: #222;
}

.mode-tabs {
  display: flex;
  background: #fff;
  border-radius: 999rpx;
  padding: 4rpx;
  margin-bottom: 12rpx;
}

.mode-tab {
  flex: 1;
  text-align: center;
  padding: 10rpx 0;
  font-size: 24rpx;
  color: #666;
  border-radius: 999rpx;

  &.active {
    background: #ff2442;
    color: #fff;
    font-weight: 600;
  }
}

.hint {
  display: block;
  font-size: 22rpx;
  color: #999;
  margin-bottom: 10rpx;
}

.today-block {
  background: #fff;
  border-radius: 12rpx;
  padding: 14rpx 18rpx;
  margin-bottom: 12rpx;
}

.block-title {
  font-size: 22rpx;
  font-weight: 600;
  color: #333;
  display: block;
  margin-bottom: 8rpx;
}

.today-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 0;
  border-top: 1rpx solid #f0f0f0;
}

.today-meta {
  font-size: 22rpx;
  color: #555;
}

.today-del {
  font-size: 22rpx;
  color: #ff2442;
}

.cloth-filter-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-bottom: 10rpx;
  flex-wrap: nowrap;
}

.filter-picker {
  flex: 0 0 auto;
}

.chip {
  font-size: 22rpx;
  padding: 6rpx 12rpx;
  background: #fff;
  border-radius: 999rpx;
  border: 1rpx solid #eee;
  color: #333;
  white-space: nowrap;

  &.active {
    color: #ff2442;
    font-weight: 600;
    background: #fff5f6;
    border-color: #ffcdd2;
  }
}

.filter-reset {
  font-size: 20rpx;
  color: #ff2442;
  flex-shrink: 0;
}

.filter-stats {
  margin-left: auto;
  font-size: 20rpx;
  color: #999;
  white-space: nowrap;
  flex-shrink: 0;
}

.pick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
  margin-bottom: 24rpx;
}

.pick-item {
  position: relative;
  background: #fff;
  border-radius: 12rpx;
  overflow: hidden;
  border: 3rpx solid transparent;

  &.selected {
    border-color: #ff2442;
  }
}

.pick-img {
  width: 100%;
  height: 160rpx;
  display: block;
  background: #f0f0f0;
}

.pick-img.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  color: #ccc;
}

.pick-name {
  display: block;
  font-size: 22rpx;
  padding: 8rpx;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.check {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 36rpx;
  height: 36rpx;
  background: #ff2442;
  color: #fff;
  border-radius: 50%;
  font-size: 22rpx;
  line-height: 36rpx;
  text-align: center;
}

.match-list {
  margin-bottom: 24rpx;
}

.match-row {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 12rpx;
  padding: 12rpx;
  margin-bottom: 10rpx;
  border: 3rpx solid transparent;

  &.selected {
    border-color: #ff2442;
  }
}

.match-thumbs {
  display: flex;
  gap: 4rpx;
  margin-right: 16rpx;
}

.match-thumb {
  width: 64rpx;
  height: 64rpx;
  border-radius: 8rpx;
  background: #f0f0f0;
}

.match-info {
  flex: 1;
}

.match-name {
  font-size: 24rpx;
  font-weight: 600;
  color: #222;
  display: block;
}

.match-sub {
  font-size: 22rpx;
  color: #999;
}

.match-check {
  width: 36rpx;
  height: 36rpx;
  background: #ff2442;
  color: #fff;
  border-radius: 50%;
  text-align: center;
  line-height: 36rpx;
  font-size: 22rpx;
}

.empty {
  padding: 60rpx 24rpx;
  text-align: center;
  font-size: 26rpx;
  color: #999;
  margin-bottom: 24rpx;
}

.btn {
  border-radius: 999rpx;
  font-size: 26rpx;
  height: 72rpx;
  line-height: 72rpx;

  &.primary {
    background: #ff2442;
    color: #fff;
  }

  &[disabled] {
    opacity: 0.45;
  }
}

.save-bar {
  margin-bottom: 12rpx;
}
</style>
